#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

dirname_cmd="dirname"
grepenv="grep"
pwd_cmd="pwd"

# Determine the JVM to use, depending on which is available.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the Java executable
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1 ; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
        echo "Please set the JAVA_HOME variable in your environment to match the location of your Java installation." >&2
        exit 1
    fi
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=$(ulimit -H -n 2>/dev/null)
    if [ $? -eq 0 ] ; then
        case "$MAX_FD_LIMIT" in
            *INF*)
                MAX_FD_LIMIT=2147483647 ;;
            esac
        ulimit -n $MAX_FD_LIMIT 2>/dev/null || true
    fi
fi

# Setup an environment variable for the application startup script
APP_BASE_NAME=`basename "$0"`
APP_HOME=`dirname "$0"`

# Resolve symlinks and relative paths
APP_HOME=`cd "$APP_HOME" && pwd -P`

# Collect all JVM arguments
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
