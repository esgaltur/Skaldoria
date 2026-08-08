<#
.SYNOPSIS
    Builds, tests, packages, and optionally publishes Skaldoria to GitHub Releases locally.

.DESCRIPTION
    1. Runs the local verification gate (scripts/verify.ps1): both test suites and the
       zero-warning compile. Nothing verifies a push automatically — the CI workflow is
       manual-dispatch only (PLT-01) — so this is where those rules are enforced.
    2. Builds native Windows MSI installer, EXE installer, portable ZIP bundle, and universal runnable JAR.
    3. Calculates SHA-256 cryptographic checksums.
    4. Uploads all release binaries directly to GitHub Releases using 'gh' CLI without needing GitHub Actions CI/CD.

.PARAMETER Version
    The release version tag (e.g. "1.2.0").

    Omit it. The default is read from Gradle (`gradlew -q printVersion`), which is the single
    source of truth in build.gradle.kts. This script used to default to a literal "1.0.0",
    so running it without an argument stamped 1.0.0 filenames onto a 1.2.0 build. Passing a
    value that disagrees with the build is refused rather than silently honoured.

.PARAMETER PublishGitHub
    When set, automatically publishes the release to GitHub via 'gh release create'.

.PARAMETER Draft
    Creates the GitHub release as an unpublished draft.

.PARAMETER Prerelease
    Marks the GitHub release as a pre-release.

.PARAMETER SkipTests
    Skips running the test suite before packaging.

.PARAMETER SkipRenderTests
    Forwarded to the verification gate; stands the render guards down (PLT-08).

.EXAMPLE
    .\scripts\package_release.ps1
    .\scripts\package_release.ps1 -PublishGitHub
    .\scripts\package_release.ps1 -PublishGitHub -Draft
#>

[CmdletBinding()]
param(
    [string]$Version,
    [switch]$PublishGitHub,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipTests,
    [switch]$SkipRenderTests
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# The version the build itself will stamp into the installers and into BuildInfo.VERSION.
# Artefact filenames must agree with it, so it is read rather than assumed.
function Get-GradleVersion {
    Push-Location $ProjectRoot
    try {
        $output = & .\gradlew.bat -q printVersion --console=plain 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read the project version from Gradle:`n$output"
        }
        $resolved = ($output | Where-Object { $_ -match '^\d+\.\d+\.\d+' } | Select-Object -Last 1)
        if (-not $resolved) {
            throw "Gradle did not report a version. Output was:`n$output"
        }
        return $resolved.ToString().Trim()
    } finally {
        Pop-Location
    }
}

$buildVersion = Get-GradleVersion
if (-not $Version) {
    $Version = $buildVersion
} elseif ($Version -ne $buildVersion) {
    Write-Error "Version mismatch: -Version '$Version' but the build produces '$buildVersion'. Change appVersion in build.gradle.kts instead of overriding it here."
    exit 1
}

Write-Host ''
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host " Skaldoria Studio -- Local Release and Packaging Pipeline" -ForegroundColor Cyan
Write-Host " Version: $Version" -ForegroundColor Yellow
Write-Host " Location: $ProjectRoot" -ForegroundColor DarkGray
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host ''

# Ensure dist output directory exists
$distDir = Join-Path $ProjectRoot 'dist'
if (Test-Path $distDir) {
    Remove-Item (Join-Path $distDir '*') -Recurse -Force -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

# Step 1: Run the local verification gate
#
# Delegated to verify.ps1 so the release and a plain local check cannot drift apart. It runs
# BOTH modules — this step used to run `desktopTest` alone, so :skaldoria-markdown could be
# red while a release was cut — and the zero-warning compile.
if (-not $SkipTests) {
    Write-Host '[1/5] Running local verification gate (tests + zero-warning build)...' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'verify.ps1') -SkipRenderTests:$SkipRenderTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Verification failed! Aborting release packaging.'
        exit $LASTEXITCODE
    }
} else {
    Write-Host '[1/5] Skipping verification (-SkipTests specified)' -ForegroundColor DarkYellow
    Write-Host '      Nothing else checks this build. Do not publish an unverified release.' -ForegroundColor DarkYellow
}

# Step 2: Build Standalone Distributable and Universal JAR
Write-Host "`n[2/5] Building native standalone application and universal JAR..." -ForegroundColor Yellow
Push-Location $ProjectRoot
try {
    & .\gradlew.bat createDistributable packageUberJarForCurrentOS :skaldoria-writer:createDistributable :skaldoria-writer:packageUberJarForCurrentOS --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Gradle build failed!'
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

# Step 3: Package MSI / EXE / ZIP Artifacts
Write-Host "`n[3/5] Bundling installer packages..." -ForegroundColor Yellow

$appDir = Join-Path $ProjectRoot 'build\compose\binaries\main\app\Skaldoria'
if (Test-Path $appDir) {
    $zipName = "Skaldoria-v$Version-windows-x64-portable.zip"
    $zipPath = Join-Path $distDir $zipName
    Write-Host "  -> Creating portable archive: $zipName..." -ForegroundColor DarkCyan
    Compress-Archive -Path "$appDir\*" -DestinationPath $zipPath -CompressionLevel Optimal
}

$writerAppDir = Join-Path $ProjectRoot 'skaldoria-writer\build\compose\binaries\main\app\SkaldoriaWriter'
if (Test-Path $writerAppDir) {
    $writerZipName = "SkaldoriaWriter-v$Version-windows-x64-portable.zip"
    $writerZipPath = Join-Path $distDir $writerZipName
    Write-Host "  -> Creating Writer portable archive: $writerZipName..." -ForegroundColor DarkCyan
    Compress-Archive -Path "$writerAppDir\*" -DestinationPath $writerZipPath -CompressionLevel Optimal
}

# Copy MSI installer if generated
$msiDir = Join-Path $ProjectRoot 'build\compose\binaries\main\msi'
if (Test-Path $msiDir) {
    Get-ChildItem -Path $msiDir -Filter '*.msi' | ForEach-Object {
        $dest = Join-Path $distDir "Skaldoria-v$Version-windows-x64.msi"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected MSI Installer: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
    }
}
$writerMsiDir = Join-Path $ProjectRoot 'skaldoria-writer\build\compose\binaries\main\msi'
if (Test-Path $writerMsiDir) {
    Get-ChildItem -Path $writerMsiDir -Filter '*.msi' | ForEach-Object {
        $dest = Join-Path $distDir "SkaldoriaWriter-v$Version-windows-x64.msi"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected Writer MSI Installer: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
    }
}

# Copy EXE installer if generated
$exeDir = Join-Path $ProjectRoot 'build\compose\binaries\main\exe'
if (Test-Path $exeDir) {
    Get-ChildItem -Path $exeDir -Filter '*.exe' | ForEach-Object {
        $dest = Join-Path $distDir "Skaldoria-v$Version-windows-x64-setup.exe"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected EXE Setup: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
    }
}
$writerExeDir = Join-Path $ProjectRoot 'skaldoria-writer\build\compose\binaries\main\exe'
if (Test-Path $writerExeDir) {
    Get-ChildItem -Path $writerExeDir -Filter '*.exe' | ForEach-Object {
        $dest = Join-Path $distDir "SkaldoriaWriter-v$Version-windows-x64-setup.exe"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected Writer EXE Setup: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
    }
}

# Copy Universal Uber JAR
$uberJarDir = Join-Path $ProjectRoot 'build\compose\jars'
if (Test-Path $uberJarDir) {
    Get-ChildItem -Path $uberJarDir -Filter '*.jar' | ForEach-Object {
        $dest = Join-Path $distDir "Skaldoria-v$Version-universal.jar"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected Universal Runnable JAR: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
    }
}
$writerUberJarDir = Join-Path $ProjectRoot 'skaldoria-writer\build\compose\jars'
if (Test-Path $writerUberJarDir) {
    Get-ChildItem -Path $writerUberJarDir -Filter '*.jar' | ForEach-Object {
        $dest = Join-Path $distDir "SkaldoriaWriter-v$Version-universal.jar"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected Writer Universal Runnable JAR: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
    }
}

# Step 4: Generate SHA-256 Checksums
Write-Host "`n[4/5] Generating SHA-256 Checksums..." -ForegroundColor Yellow
$checksumFile = Join-Path $distDir 'checksums-sha256.txt'
$checksums = @()

Get-ChildItem -Path $distDir -File | Where-Object { $_.Name -ne 'checksums-sha256.txt' } | ForEach-Object {
    $hash = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash.ToLower()
    $line = "$hash  $($_.Name)"
    $checksums += $line
    $sizeMB = [math]::Round(($_.Length / 1MB), 2)
    Write-Host "  * $($_.Name) ($sizeMB MB) -> $hash" -ForegroundColor Gray
}

$checksums | Out-File -FilePath $checksumFile -Encoding utf8
Write-Host '  -> Checksums saved to dist/checksums-sha256.txt' -ForegroundColor Green

# Step 5: Upload / Publish to GitHub Releases
Write-Host "`n[5/5] GitHub Release..." -ForegroundColor Yellow

$releaseFiles = Get-ChildItem -Path $distDir -File | Select-Object -ExpandProperty FullName

if ($PublishGitHub) {
    if (-not (Get-Command 'gh' -ErrorAction SilentlyContinue)) {
        Write-Warning "'gh' GitHub CLI is not installed."
        Write-Host 'Install it via: winget install --id GitHub.cli' -ForegroundColor Yellow
        Write-Host 'Then run: gh auth login' -ForegroundColor Yellow
    } else {
        Write-Host "  Creating GitHub Release v$Version with GitHub CLI..." -ForegroundColor Cyan

        $ghArgs = @('release', 'create', "v$Version")
        $ghArgs += $releaseFiles
        $ghArgs += @('--title', "Skaldoria Studio v$Version")

        $changelogPath = Join-Path $ProjectRoot 'CHANGELOG.md'
        if (Test-Path $changelogPath) {
            $ghArgs += @('--notes-file', $changelogPath)
        } else {
            $ghArgs += @('--notes', "Official release of Skaldoria Studio v$Version")
        }

        if ($Draft) { $ghArgs += '--draft' }
        if ($Prerelease) { $ghArgs += '--prerelease' }

        & gh @ghArgs

        if ($LASTEXITCODE -eq 0) {
            Write-Host "`nSUCCESS: GitHub Release v$Version has been created and published!" -ForegroundColor Green
        } else {
            Write-Warning "gh command exited with code $LASTEXITCODE. Please check your GitHub permissions."
        }
    }
} else {
    Write-Host "  Ready to upload! Packages are prepared in: $distDir" -ForegroundColor Green
    Write-Host ''
    Write-Host '  To publish directly to GitHub from PowerShell anytime, run:' -ForegroundColor White
    Write-Host '    .\scripts\release.ps1 -PublishGitHub' -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  Or manually upload the files located in the dist folder:' -ForegroundColor White
    Get-ChildItem -Path $distDir -File | ForEach-Object {
        $sizeMB = [math]::Round(($_.Length / 1MB), 2)
        Write-Host "    - $($_.Name) ($sizeMB MB)" -ForegroundColor Gray
    }
}

Write-Host ''
