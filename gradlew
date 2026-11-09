#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_BIN=${JAVA_HOME:+"$JAVA_HOME/bin/java"}
[ -n "$JAVA_BIN" ] || JAVA_BIN=java
exec "$JAVA_BIN" -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
