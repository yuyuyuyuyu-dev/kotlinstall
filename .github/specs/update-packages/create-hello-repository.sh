#!/usr/bin/env bash
set -euo pipefail

repository="$1"

cp -R "$(dirname "$0")/hello" "$repository"
cp "$GITHUB_WORKSPACE/gradlew" "$repository/"
cp -R "$GITHUB_WORKSPACE/gradle" "$repository/"
cd "$repository"
git init --quiet --initial-branch main
git add --all
git commit --quiet --message "Add hello"
