#!/usr/bin/env bash
# Formats backend (Maven formatter plugin) and frontend (Prettier via npm).

set -euo pipefail

# Repository root, independent of the working directory
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Formatting backend"
cd "$ROOT_DIR/backend"
# Absolute path needed (relative ones are resolved per module); "pwd -W" gives a
# Windows path under Git Bash, other shells fall back to the plain path
BACKEND_DIR="$(pwd -W 2>/dev/null || pwd)"
mvn -B formatter:format -Dconfigfile="$BACKEND_DIR/formatter/formatter.xml"

echo "==> Formatting frontend"
# Requires "npm install" in frontend/ once
cd "$ROOT_DIR/frontend"
npm run format
