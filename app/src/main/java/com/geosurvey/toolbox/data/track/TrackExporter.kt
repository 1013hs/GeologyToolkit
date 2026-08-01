package com.geosurvey.toolbox.data.track

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TrackExporter {

    fun exportGpx(context: Context, points: List<TrackPointEntity>, trackId: String): File? {
        if (points.isEmpty()) return null

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n<gpx version=\"1.1\" creator=\"GeoSurveyToolbox\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        sb.append("\n  <trk>")
        sb.append("\n    <name>Track ").append(trackId.take(8)).append("</name>")
        sb.append("\n    <trkseg>")

        for (p in points) {
            sb.append("\n      <trkpt lat=\"").append(p.latitude).append("\" lon=\"").append(p.longitude).append("\">")
            sb.append("\n        <ele>").append(p.altitude).append("</ele>")
            sb.append("\n        <time>").append(sdf.format(Date(p.timestamp))).append("</time>")
            sb.append("\n      </trkpt>")
        }

        sb.append("\n    </trkseg>")
        sb.append("\n  </trk>")
        sb.append("\n</gpx>")

        val file = File(context.cacheDir, "track_${trackId.take(8)}.gpx")
        file.writeText(sb.toString())
        return file
    }

    fun exportKml(context: Context, points: List<TrackPointEntity>, trackId: String): File? {
        if (points.isEmpty()) return null

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
        sb.append("\n  <Document>")
        sb.append("\n    <name>Track ").append(trackId.take(8)).append("</name>")
        sb.append("\n    <Placemark>")
        sb.append("\n      <name>Track Line</name>")
        sb.append("\n      <LineString>")
        sb.append("\n        <coordinates>")

        for (p in points) {
            // KML 顺序：经度,纬度,海拔
            sb.append("\n          ").append(p.longitude).append(",").append(p.latitude).append(",").append(p.altitude)
        }

        sb.append("\n        </coordinates>")
        sb.append("\n      </LineString>")
        sb.append("\n    </Placemark>")
        sb.append("\n  </Document>")
        sb.append("\n</kml>")

        val file = File(context.cacheDir, "track_${trackId.take(8)}.kml")
        file.writeText(sb.toString())
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出轨迹"))
    }
}
