#!/usr/bin/env bash
set -euo pipefail

repository="$1"

mkdir "$repository"
git -C "$GITHUB_WORKSPACE" archive HEAD | tar -x -C "$repository"
cd "$repository"
git init --quiet --initial-branch main
git add --all
git commit --quiet --message "Add kotlinstall"
