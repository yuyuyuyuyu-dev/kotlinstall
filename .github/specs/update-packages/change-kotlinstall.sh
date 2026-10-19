#!/usr/bin/env bash
set -euo pipefail

cd "$1"
sed -i '' 's/"Install Kotlin\/Native programs from Git repositories"/"Updated by a new commit"/' src/commonMain/kotlin/dev/yuyuyuyuyu/kotlinstall/Kotlinstall.kt
git commit --quiet --all --message "Change the description"
