#!/usr/bin/env bash
set -euo pipefail

cd "$1"
sed -i.bak 's/Hello, 1/Hello, 2/' src/nativeMain/kotlin/Main.kt
rm src/nativeMain/kotlin/Main.kt.bak
git commit --quiet --all --message "Change the message"
