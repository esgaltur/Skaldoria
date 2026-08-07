#!/usr/bin/env bash
# ==============================================================================
# Skaldoria Studio — Local Linux Release & Packaging Script
# Builds native Linux packages (.deb, .rpm, .tar.gz, universal .jar)
# ==============================================================================
set -euo pipefail

PUBLISH_GH="${2:-false}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

# The version is the build's, not the script's.
#
# This defaulted to a literal "1.0.0", so running it with no argument stamped 1.0.0 filenames
# onto whatever the build actually produced. build.gradle.kts is the single source of truth and
# `printVersion` is how it is read. An explicit argument that disagrees with the build is
# refused rather than silently honoured.
BUILD_VERSION="$(./gradlew -q printVersion --console=plain | tail -n 1 | tr -d '[:space:]')"
if [ -z "${BUILD_VERSION}" ]; then
    echo "Error: could not read the project version from Gradle (./gradlew -q printVersion)." >&2
    exit 1
fi

VERSION="${1:-${BUILD_VERSION}}"
if [ "${VERSION}" != "${BUILD_VERSION}" ]; then
    echo "Error: requested version '${VERSION}' but the build produces '${BUILD_VERSION}'." >&2
    echo "       Change appVersion in build.gradle.kts instead of overriding it here." >&2
    exit 1
fi

echo "=========================================================="
echo " 👑 Skaldoria Studio — Linux Packaging Pipeline"
echo " Version: ${VERSION}"
echo " Project: ${PROJECT_ROOT}"
echo "=========================================================="

# 1. Clean & Prepare dist directory
mkdir -p dist
rm -f dist/*

# 2. Run Automated Verification Tests
#
# PLT-09: packaging must not require a display. The render guards drive real Compose frames
# through ImageComposeScene, which needs a surface Skia can target — absent under WSL, in a
# container, and on any headless build box. Without this the *packaging* run fails on a
# graphics problem that says nothing about the packages being built, which is what made an
# earlier attempt abandon the suite entirely with a blanket @Ignore (see PLT-08).
#
# RenderEnvironment already detects headlessness on its own; the flag is passed explicitly so
# the reason appears in the log rather than being inferred from a skip count.
echo -e "\n[1/5] 🧪 Running test suite..."
RENDER_FLAG=""
if [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
    echo "  -> No DISPLAY/WAYLAND_DISPLAY: render guards will be skipped, not failed."
    echo "     Run this on a machine with a display to exercise them (see docs/RENDERING_STATUS.md)."
    RENDER_FLAG="-PskipRenderTests"
fi
./gradlew desktopTest :skaldoria-markdown:test --no-daemon ${RENDER_FLAG}
echo "  -> All tests passed successfully!"

# The zero-warning NFR (CONTRIBUTING.md section 6). Nothing enforces it automatically — the CI
# workflow is manual-dispatch only (PLT-01) — so the release run is where it actually holds; the
# Windows pipeline does the same through scripts/verify.ps1.
echo "  -> Compiling with warnings as errors..."
./gradlew compileKotlinDesktop compileTestKotlinDesktop -PwarningsAsErrors --no-daemon
echo "  -> Zero warnings."

# 3. Build Linux Distributable & Universal JAR
echo -e "\n[2/5] 🔨 Building native Linux standalone distributable & universal JAR..."
./gradlew createDistributable packageUberJarForCurrentOS --no-daemon

# Attempt to build .deb and .rpm if tools are installed
if command -v dpkg-deb >/dev/null 2>&1 || command -v fakeroot >/dev/null 2>&1; then
    echo "  -> Building Debian (.deb) package..."
    ./gradlew packageDeb --no-daemon || true
fi

if command -v rpmbuild >/dev/null 2>&1; then
    echo "  -> Building RedHat (.rpm) package..."
    ./gradlew packageRpm --no-daemon || true
fi

# 4. Bundle Portable Tarball and Collect Artifacts
echo -e "\n[3/5] 📦 Bundling Linux packages..."

APP_DIR="build/compose/binaries/main/app/Skaldoria"
if [ -d "${APP_DIR}" ]; then
    TAR_NAME="Skaldoria-v${VERSION}-linux-x64-portable.tar.gz"
    echo "  -> Creating portable archive: ${TAR_NAME}..."
    tar -czf "dist/${TAR_NAME}" -C "${APP_DIR}" .
fi

if [ -d "build/compose/binaries/main/deb" ]; then
    for deb in build/compose/binaries/main/deb/*.deb; do
        [ -f "$deb" ] && cp "$deb" "dist/Skaldoria-v${VERSION}-linux-amd64.deb" && echo "  -> Collected DEB: Skaldoria-v${VERSION}-linux-amd64.deb"
    done
fi

if [ -d "build/compose/binaries/main/rpm" ]; then
    for rpm in build/compose/binaries/main/rpm/*.rpm; do
        [ -f "$rpm" ] && cp "$rpm" "dist/Skaldoria-v${VERSION}-linux-x86_64.rpm" && echo "  -> Collected RPM: Skaldoria-v${VERSION}-linux-x86_64.rpm"
    done
fi

if [ -d "build/compose/jars" ]; then
    for jar in build/compose/jars/*.jar; do
        [ -f "$jar" ] && cp "$jar" "dist/Skaldoria-v${VERSION}-universal.jar" && echo "  -> Collected Universal JAR: Skaldoria-v${VERSION}-universal.jar"
    done
fi

# 5. Generate Checksums
echo -e "\n[4/5] 🔒 Generating SHA-256 Checksums..."
cd dist
sha256sum * > checksums-sha256.txt 2>/dev/null || true
cd "${PROJECT_ROOT}"
echo "  -> Checksums saved to dist/checksums-sha256.txt"

# 6. Publish to GitHub
echo -e "\n[5/5] 🚀 GitHub Release..."
if [ "${PUBLISH_GH}" = "--publish" ] || [ "${PUBLISH_GH}" = "true" ]; then
    if command -v gh >/dev/null 2>&1; then
        echo "  Publishing release to GitHub with 'gh' CLI..."
        gh release create "v${VERSION}" dist/* --title "Skaldoria Studio v${VERSION}" --notes-file "CHANGELOG.md" || true
    else
        echo "  Warning: 'gh' CLI not found. Artifacts ready in dist/."
    fi
else
    echo "  Build complete! Artifacts created in dist/:"
    ls -lh dist/
fi

echo -e "\n🎉 Linux release build finished successfully!\n"
