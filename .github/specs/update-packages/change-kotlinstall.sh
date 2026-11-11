#!/usr/bin/env bash
set -euo pipefail

cd "$1"
file=src/commonMain/kotlin/dev/yuyuyuyuyu/kotlinstall/Kotlinstall.kt
sed -i.bak 's/"Install Kotlin\/Native programs from Git repositories"/"Updated by a new commit"/' "$file"
rm "$file.bak"
git commit --quiet --all --message "Change the description"
