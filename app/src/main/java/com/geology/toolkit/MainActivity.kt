package com.geosurvey.toolbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geosurvey.toolbox.data.track.TrackExporter
import com.geosurvey.toolbox.data.track.TrackRepository
import com.geosurvey.toolbox.service.LocationTrackingService
import com.geosurvey.toolbox.ui.GnssViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: GnssViewModel by viewModels()
    private lateinit var repository: TrackRepository

    private lateinit var statusText: TextView
    private lateinit var historyText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnExportGpx: Button
    private lateinit var btnExportKml: Button
    private lateinit var btnDeleteLatest: Button

    private var isTracking = false
    private var latestTrackId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.onPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TrackRepository(applicationContext)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 60, 36, 40)
        }

        statusText = TextView(this).apply {
            textSize = 14.5f
            setTextColor(0xFF00695C.toInt())
            setPadding(0, 0, 0, 20)
        }

        btnStart = Button(this).apply {
            text = "开始记录轨迹"
            setOnClickListener { startTracking() }
        }

        btnStop = Button(this).apply {
            text = "停止记录"
            isEnabled = false
            setOnClickListener { stopTracking() }
        }

        btnRefresh = Button(this).apply {
            text = "刷新历史轨迹"
            setOnClickListener { loadHistory() }
        }

        btnExportGpx = Button(this).apply {
            text = "导出最新轨迹 GPX"
            setOnClickListener { exportLatest("gpx") }
        }

        btnExportKml = Button(this).apply {
            text = "导出最新轨迹 KML"
            setOnClickListener { exportLatest("kml") }
        }

        btnDeleteLatest = Button(this).apply {
            text = "删除最新轨迹"
            setOnClickListener { deleteLatest() }
        }

        historyText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF37474F.toInt())
            setPadding(0, 16, 0, 0)
        }

        layout.addView(statusText)
        layout.addView(btnStart)
        layout.addView(btnStop)
        layout.addView(btnRefresh)
        layout.addView(btnExportGpx)
        layout.addView(btnExportKml)
        layout.addView(btnDeleteLatest)
        layout.addView(historyText)
        scroll.addView(layout)
        setContentView(scroll)

        requestPermissionsIfNeeded()
        observeFix()
        loadHistory()
    }

    private fun observeFix() {
        lifecycleScope.launch {
            viewModel.fix.collect { fix ->
                val trackStatus = if (isTracking) "【正在记录】" else "【未记录】"
                if (fix == null) {
                    statusText.text = trackStatus + "\n正在搜索卫星…\n请到空旷处"
                } else {
                    val lat = String.format("%.8f", fix.latitude)
                    val lon = String.format("%.8f", fix.longitude)
                    val alt = String.format("%.1f", fix.altitudeEllipsoid)
                    val acc = String.format("%.1f", fix.accuracyHorizontal)
                    val hdop = String.format("%.2f", fix.hdop)
                    val pdop = String.format("%.2f", fix.pdop)
                    val speed = String.format("%.2f", fix.speed)

                    statusText.text =
                        trackStatus + "\n" +
                        "阶段4 · 历史管理 + 导出\n\n" +
                        "纬度: " + lat + "\n" +
                        "经度: " + lon + "\n" +
                        "海拔: " + alt + " m\n" +
                        "精度: " + acc + " m\n" +
                        "HDOP: " + hdop + "  PDOP: " + pdop + "\n" +
                        "卫星: " + fix.usedSatelliteCount + "/" + fix.satelliteCount + "\n" +
                        "质量: " + fix.quality.label + "\n" +
                        "速度: " + speed + " m/s"
                }
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            repository.getAllTrackIds().collectLatest { ids ->
                if (ids.isEmpty()) {
                    historyText.text = "\n暂无历史轨迹"
                    latestTrackId = null
                    return@collectLatest
                }

                latestTrackId = ids.firstOrNull()
                val sb = StringBuilder("\n===== 历史轨迹 =====\n")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (id in ids) {
                    val count = repository.getPointCount(id)
                    val latest = repository.getLatestPoint()
                    var timeStr = "-"
                    if (latest != null && latest.trackId == id) {
                        timeStr = sdf.format(Date(latest.timestamp))
                    }
                    sb.append("\n轨迹ID: ").append(id.take(8)).append("...\n")
                    sb.append("点数: ").append(count).append("\n")
                    sb.append("最近时间: ").append(timeStr).append("\n")
                    sb.append("------------------\n")
                }
                historyText.text = sb.toString()
            }
        }
    }

    private fun exportLatest(type: String) {
        val id = latestTrackId
        if (id == null) {
            Toast.makeText(this, "没有可导出的轨迹", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val points = withContext(Dispatchers.IO) {
                repository.getPoints(id).first()
            }
            if (points.isEmpty()) {
                Toast.makeText(this@MainActivity, "轨迹点为空", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val file = if (type == "gpx") {
                TrackExporter.exportGpx(this@MainActivity, points, id)
            } else {
                TrackExporter.exportKml(this@MainActivity, points, id)
            }

            if (file != null) {
                val mime = if (type == "gpx") "application/gpx+xml" else "application/vnd.google-earth.kml+xml"
                TrackExporter.shareFile(this@MainActivity, file, mime)
            } else {
                Toast.makeText(this@MainActivity, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteLatest() {
        val id = latestTrackId
        if (id == null) {
            Toast.makeText(this, "没有可删除的轨迹", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            repository.deleteTrack(id)
            Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
            loadHistory()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.onPermissionGranted()
        }
    }

    private fun startTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isTracking = true
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        Toast.makeText(this, "开始记录", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        isTracking = false
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        Toast.makeText(this, "已停止记录", Toast.LENGTH_SHORT).show()
        loadHistory()
    }
}
