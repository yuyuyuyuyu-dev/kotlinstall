#!/usr/bin/env bash
set -euo pipefail

cd "$1"
sed -i '' 's/Hello, 1/Hello, 2/' src/macosArm64Main/kotlin/Main.kt
git commit --quiet --all --message "Change the message"
