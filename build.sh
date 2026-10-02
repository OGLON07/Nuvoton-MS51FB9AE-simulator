#!/usr/bin/env bash
# Builds everything: Java classes -> out/, native POSIX launcher -> bin/nuvoton_launcher
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -d out $(find src -name '*.java')
javac -cp out -d out tests/*.java
make -C native
echo "Build OK.  Run the GUI:  ./run.sh     Run IPC tests:  tests/run_ipc_tests.sh"
