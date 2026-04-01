#!/bin/sh

##############################################################################
##
##  Gradle start up script for POSIX generated compatibility.
##
##############################################################################

APP_HOME=$(cd "${0%/*}" 2>/dev/null && pwd -P)
APP_BASE_NAME=${0##*/}

if [ -z "$JAVA_HOME" ]; then
    JAVACMD=java
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
    exit 1
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"