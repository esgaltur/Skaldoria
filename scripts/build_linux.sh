#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"

echo "========================================="
echo " 👑 Skaldoria Linux Packager v${VERSION}"
echo "========================================="

# 1. Create dist directory
mkdir -p dist

# 2. Build native Linux distribution
echo -e "\n[1/2] Building native Linux distributable..."
./gradlew createDistributable packageDeb

# 3. Compress into .tar.gz archive
APP_DIR="build/compose/binaries/main/app/Skaldoria"
TAR_NAME="Skaldoria-v${VERSION}-linux-x64.tar.gz"
TAR_PATH="dist/${TAR_NAME}"

echo -e "\n[2/2] Creating Linux package archive: ${TAR_PATH}..."
tar -czf "${TAR_PATH}" -C "${APP_DIR}" .

# Copy .deb if produced
if [ -d "build/compose/binaries/main/deb" ]; then
    cp build/compose/binaries/main/deb/*.deb dist/ || true
fi

echo -e "\n-> Build complete! Artifacts created in dist/:"
ls -lh dist/
