<#
.SYNOPSIS
    Builds, tests, packages, and optionally publishes Skaldoria to GitHub Releases locally.

.DESCRIPTION
    1. Runs automated unit and integration tests.
    2. Builds native Windows MSI installer, EXE installer, portable ZIP bundle, and universal runnable JAR.
    3. Calculates SHA-256 cryptographic checksums.
    4. Uploads all release binaries directly to GitHub Releases using 'gh' CLI without needing GitHub Actions CI/CD.

.PARAMETER Version
    The release version tag (e.g. "1.0.0"). Default is "1.0.0".

.PARAMETER PublishGitHub
    When set, automatically publishes the release to GitHub via 'gh release create'.

.PARAMETER Draft
    Creates the GitHub release as an unpublished draft.

.PARAMETER Prerelease
    Marks the GitHub release as a pre-release.

.PARAMETER SkipTests
    Skips running the test suite before packaging.

.EXAMPLE
    .\scripts\package_release.ps1 -Version "1.0.0"
    .\scripts\package_release.ps1 -Version "1.0.0" -PublishGitHub
    .\scripts\package_release.ps1 -Version "1.0.0" -PublishGitHub -Draft
#>

[CmdletBinding()]
param(
    [string]$Version = '1.0.0',
    [switch]$PublishGitHub,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

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

# Step 1: Run Automated Verification Tests
if (-not $SkipTests) {
    Write-Host '[1/5] Running test suite...' -ForegroundColor Yellow
    Push-Location $ProjectRoot
    try {
        & .\gradlew.bat desktopTest --no-daemon
        if ($LASTEXITCODE -ne 0) {
            Write-Error 'Test suite failed! Aborting release packaging.'
            exit $LASTEXITCODE
        }
        Write-Host '  -> All unit and integration tests passed successfully!' -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host '[1/5] Skipping tests (-SkipTests specified)' -ForegroundColor DarkYellow
}

# Step 2: Build Standalone Distributable and Universal JAR
Write-Host "`n[2/5] Building native standalone application and universal JAR..." -ForegroundColor Yellow
Push-Location $ProjectRoot
try {
    & .\gradlew.bat createDistributable packageUberJarForCurrentOS --no-daemon
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

# Copy MSI installer if generated
$msiDir = Join-Path $ProjectRoot 'build\compose\binaries\main\msi'
if (Test-Path $msiDir) {
    Get-ChildItem -Path $msiDir -Filter '*.msi' | ForEach-Object {
        $dest = Join-Path $distDir "Skaldoria-v$Version-windows-x64.msi"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected MSI Installer: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
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

# Copy Universal Uber JAR
$uberJarDir = Join-Path $ProjectRoot 'build\compose\jars'
if (Test-Path $uberJarDir) {
    Get-ChildItem -Path $uberJarDir -Filter '*.jar' | ForEach-Object {
        $dest = Join-Path $distDir "Skaldoria-v$Version-universal.jar"
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  -> Collected Universal Runnable JAR: $(Split-Path $dest -Leaf)" -ForegroundColor DarkCyan
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
    Write-Host "    .\release.ps1 -Version `"$Version`" -PublishGitHub" -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  Or manually upload the files located in the dist folder:' -ForegroundColor White
    Get-ChildItem -Path $distDir -File | ForEach-Object {
        $sizeMB = [math]::Round(($_.Length / 1MB), 2)
        Write-Host "    - $($_.Name) ($sizeMB MB)" -ForegroundColor Gray
    }
}

Write-Host ''
