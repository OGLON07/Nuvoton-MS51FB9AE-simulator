#!/usr/bin/env bash
# Starts the 3-process simulator through the native POSIX launcher.
# Needs a display for the Swing UI (WSLg on Windows 11 sets DISPLAY automatically).
cd "$(dirname "$0")"
[ -x bin/nuvoton_launcher ] || ./build.sh
exec bin/nuvoton_launcher --classpath out "$@"
