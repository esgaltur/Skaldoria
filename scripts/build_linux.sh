#!/usr/bin/env bash
# Builds all Skaldoria desktop applications on Linux. Intended for native Linux or WSL 2.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

VERSION=""
OUTPUT_DIRECTORY="dist/linux"
PUBLISH_GITHUB=false
SKIP_TESTS=false
SKIP_RENDER_TESTS=false
SKIP_INSTALLERS=false

usage() {
    cat <<'EOF'
Usage: ./scripts/build_linux.sh [options]

Options:
  --version VERSION       Must match appVersion in build.gradle.kts.
  --output-dir PATH       Artifact directory (default: dist/linux).
  --publish               Create the GitHub release with gh.
  --skip-tests            Skip tests and the warnings-as-errors gate.
  --skip-render-tests     Skip Compose render guards.
  --skip-installers       Build portable archives and JARs only.
  -h, --help              Show this help.

For compatibility, a bare first argument is treated as VERSION.
EOF
}

while (($#)); do
    case "$1" in
        --version)
            [[ $# -ge 2 ]] || { echo "Error: --version needs a value." >&2; exit 2; }
            VERSION="$2"
            shift 2
            ;;
        --output-dir)
            [[ $# -ge 2 ]] || { echo "Error: --output-dir needs a value." >&2; exit 2; }
            OUTPUT_DIRECTORY="$2"
            shift 2
            ;;
        --publish|true)
            PUBLISH_GITHUB=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-render-tests)
            SKIP_RENDER_TESTS=true
            shift
            ;;
        --skip-installers)
            SKIP_INSTALLERS=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --*)
            echo "Error: unknown option '$1'." >&2
            usage >&2
            exit 2
            ;;
        *)
            if [[ -z "${VERSION}" ]]; then
                VERSION="$1"
                shift
            else
                echo "Error: unexpected argument '$1'." >&2
                exit 2
            fi
            ;;
    esac
done

cd "${PROJECT_ROOT}"

BUILD_VERSION="$(./gradlew -q printVersion --console=plain | awk '/^[0-9]+\.[0-9]+\.[0-9]+/ { value=$0 } END { gsub(/[[:space:]]/, "", value); print value }')"
[[ -n "${BUILD_VERSION}" ]] || { echo "Error: Gradle did not report the project version." >&2; exit 1; }
VERSION="${VERSION:-${BUILD_VERSION}}"
if [[ "${VERSION}" != "${BUILD_VERSION}" ]]; then
    echo "Error: requested version '${VERSION}', but Gradle builds '${BUILD_VERSION}'." >&2
    echo "       Change appVersion in build.gradle.kts." >&2
    exit 1
fi

if [[ "${OUTPUT_DIRECTORY}" = /* ]]; then
    OUTPUT_DIR="${OUTPUT_DIRECTORY}"
else
    OUTPUT_DIR="${PROJECT_ROOT}/${OUTPUT_DIRECTORY}"
fi
mkdir -p "${OUTPUT_DIR}"
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd)"
case "${OUTPUT_DIR}/" in
    "${PROJECT_ROOT}/"*) ;;
    *) echo "Error: output directory must be inside the repository: ${OUTPUT_DIR}" >&2; exit 1 ;;
esac

# Only files created by an earlier release run live here; nested directories are preserved.
find "${OUTPUT_DIR}" -mindepth 1 -maxdepth 1 -type f -delete

case "$(uname -m)" in
    x86_64) PORTABLE_ARCH="x64"; DEB_ARCH="amd64"; RPM_ARCH="x86_64" ;;
    aarch64|arm64) PORTABLE_ARCH="arm64"; DEB_ARCH="arm64"; RPM_ARCH="aarch64" ;;
    *) echo "Error: unsupported Linux architecture: $(uname -m)" >&2; exit 1 ;;
esac

APPLICATIONS=(
    "skaldoria-presentation|Skaldoria|Skaldoria"
    "skaldoria-writer|SkaldoriaWriter|SkaldoriaWriter"
    "skaldoria-canvas|SkaldoriaCanvas|SkaldoriaCanvas"
    "skaldoria-cv|SkaldoriaCV|SkaldoriaCV"
)

echo "=========================================================="
echo " Skaldoria Suite - Linux Local Release"
echo " Version: ${VERSION}"
echo " Output:  ${OUTPUT_DIR}"
echo "=========================================================="

echo
echo "Preparing clean Linux build outputs..."
./gradlew \
    :skaldoria-markdown:clean \
    :skaldoria-shared-ui:clean \
    :skaldoria-cv-core:clean \
    :skaldoria-presentation:clean \
    :skaldoria-writer:clean \
    :skaldoria-canvas:clean \
    :skaldoria-cv:clean \
    --no-daemon --console=plain

if [[ "${SKIP_TESTS}" == false ]]; then
    echo
    echo "[1/4] Verifying every module..."
    RENDER_ARGS=()
    if [[ "${SKIP_RENDER_TESTS}" == true || ( -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ) ]]; then
        RENDER_ARGS=(-PskipRenderTests)
        echo "  -> Render guards are skipped because this Linux session is headless or explicitly opted out."
    fi
    ./gradlew \
        :skaldoria-presentation:desktopTest \
        :skaldoria-markdown:test \
        :skaldoria-shared-ui:desktopTest \
        :skaldoria-writer:desktopTest \
        :skaldoria-canvas:desktopTest \
        :skaldoria-cv-core:test \
        :skaldoria-cv:desktopTest \
        --no-daemon --console=plain "${RENDER_ARGS[@]}"
    ./gradlew \
        :skaldoria-presentation:compileKotlinDesktop \
        :skaldoria-presentation:compileTestKotlinDesktop \
        :skaldoria-shared-ui:compileKotlinDesktop \
        :skaldoria-shared-ui:compileTestKotlinDesktop \
        :skaldoria-writer:compileKotlinDesktop \
        :skaldoria-writer:compileTestKotlinDesktop \
        :skaldoria-canvas:compileKotlinDesktop \
        :skaldoria-canvas:compileTestKotlinDesktop \
        :skaldoria-cv-core:compileKotlin \
        :skaldoria-cv-core:compileTestKotlin \
        :skaldoria-cv:compileKotlinDesktop \
        :skaldoria-cv:compileTestKotlinDesktop \
        -PwarningsAsErrors --no-daemon --console=plain
else
    echo
    echo "[1/4] Verification skipped by request."
fi

echo
echo "[2/4] Building all Linux applications..."
GRADLE_TASKS=()
for app in "${APPLICATIONS[@]}"; do
    IFS='|' read -r module _ _ <<< "${app}"
    GRADLE_TASKS+=(":${module}:createDistributable" ":${module}:packageUberJarForCurrentOS")
done

BUILD_DEB=false
BUILD_RPM=false
if [[ "${SKIP_INSTALLERS}" == false ]]; then
    if command -v dpkg-deb >/dev/null 2>&1 && command -v fakeroot >/dev/null 2>&1; then
        BUILD_DEB=true
    else
        echo "  -> DEB installers skipped: install 'fakeroot' and 'dpkg' in WSL to enable them."
    fi
    if command -v rpmbuild >/dev/null 2>&1; then
        BUILD_RPM=true
    else
        echo "  -> RPM installers skipped: install 'rpm' in WSL to enable them."
    fi
fi
./gradlew "${GRADLE_TASKS[@]}" --no-daemon --console=plain

# jpackage formats share temporary runtime inputs. Build each installer format in a separate
# Gradle invocation so one format cannot clean files still needed by another.
if [[ "${BUILD_DEB}" == true ]]; then
    DEB_TASKS=()
    for app in "${APPLICATIONS[@]}"; do
        IFS='|' read -r module _ _ <<< "${app}"
        DEB_TASKS+=(":${module}:packageDeb")
    done
    ./gradlew "${DEB_TASKS[@]}" --no-daemon --console=plain
fi
if [[ "${BUILD_RPM}" == true ]]; then
    RPM_TASKS=()
    for app in "${APPLICATIONS[@]}"; do
        IFS='|' read -r module _ _ <<< "${app}"
        RPM_TASKS+=(":${module}:packageRpm")
    done
    ./gradlew "${RPM_TASKS[@]}" --no-daemon --console=plain
fi

copy_latest() {
    local source_dir="$1"
    local pattern="$2"
    local destination="$3"
    local candidates=()
    shopt -s nullglob
    candidates=("${source_dir}"/${pattern})
    shopt -u nullglob
    ((${#candidates[@]} > 0)) || { echo "Error: no '${pattern}' artifact in ${source_dir}." >&2; exit 1; }
    cp "${candidates[-1]}" "${destination}"
    echo "  -> $(basename "${destination}")"
}

echo
echo "[3/4] Collecting artifacts..."
for app in "${APPLICATIONS[@]}"; do
    IFS='|' read -r module product package_name <<< "${app}"
    compose_dir="${PROJECT_ROOT}/${module}/build/compose"
    app_dir="${compose_dir}/binaries/main/app/${package_name}"
    [[ -d "${app_dir}" ]] || { echo "Error: distributable was not produced: ${app_dir}" >&2; exit 1; }

    tar_name="${product}-v${VERSION}-linux-${PORTABLE_ARCH}-portable.tar.gz"
    tar -czf "${OUTPUT_DIR}/${tar_name}" -C "${app_dir}" .
    echo "  -> ${tar_name}"

    copy_latest \
        "${compose_dir}/jars" "*${VERSION}*.jar" \
        "${OUTPUT_DIR}/${product}-v${VERSION}-linux-${PORTABLE_ARCH}.jar"

    if [[ "${BUILD_DEB}" == true ]]; then
        copy_latest \
            "${compose_dir}/binaries/main/deb" "*${VERSION}*.deb" \
            "${OUTPUT_DIR}/${product}-v${VERSION}-linux-${DEB_ARCH}.deb"
    fi
    if [[ "${BUILD_RPM}" == true ]]; then
        copy_latest \
            "${compose_dir}/binaries/main/rpm" "*${VERSION}*.rpm" \
            "${OUTPUT_DIR}/${product}-v${VERSION}-linux-${RPM_ARCH}.rpm"
    fi
done

echo
echo "[4/4] Writing SHA-256 checksums..."
(
    cd "${OUTPUT_DIR}"
    sha256sum -- * > checksums-linux-sha256.txt
)
echo "  -> ${OUTPUT_DIR}/checksums-linux-sha256.txt"

if [[ "${PUBLISH_GITHUB}" == true ]]; then
    command -v gh >/dev/null 2>&1 || { echo "Error: gh is required for --publish." >&2; exit 1; }
    gh release create "v${VERSION}" "${OUTPUT_DIR}"/* \
        --title "Skaldoria Suite v${VERSION}" \
        --notes-file "${PROJECT_ROOT}/CHANGELOG.md"
fi

echo
echo "Linux release complete: ${OUTPUT_DIR}"
