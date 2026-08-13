<#
.SYNOPSIS
    Builds all Skaldoria desktop apps and merges them into a single portable bundle.

.DESCRIPTION
    Each application module produces its own jpackage app-image under
    build\compose\binaries\main\app\<Name>\ with three parts:

        <Name>.exe        the native launcher (reads app\<Name>.cfg)
        app\              main jar + content-hashed dependency jars + <Name>.cfg
        runtime\          the bundled Java runtime (identical JBR across all apps)

    Shipping three of these separately duplicates the ~90 MB runtime and the shared
    Compose/Skiko jars three times. This script produces ONE bundle instead:

        <bundle>\
            Skaldoria.exe  SkaldoriaWriter.exe  SkaldoriaCanvas.exe   (+ .ico each)
            app\   union of every app's app\ folder
            runtime\   copied exactly once (shared)

    This is safe because:
      * jpackage launchers locate their config by launcher name (Skaldoria.exe reads
        app\Skaldoria.cfg), so the three .cfg files coexist without collision;
      * dependency jars are content-hashed, so identical jars dedupe by filename and
        any that genuinely differ keep distinct names and coexist;
      * each app's main jar has a unique name;
      * the runtimes are the same JBR build, so one copy serves all three launchers.

    Output (in dist\):
      * Skaldoria-Suite-v<Version>-windows-x64\        the merged folder
      * Skaldoria-Suite-v<Version>-windows-x64.zip     a single portable archive
      * <zip>.sha256                                   checksum of the archive

.PARAMETER Version
    The bundle version. Omit it: the default is read from Gradle (`gradlew -q printVersion`),
    the single source of truth in build.gradle.kts. Passing a value that disagrees with the
    build is refused rather than silently honoured.

.PARAMETER SkipBuild
    Skip the Gradle build and merge whatever app-images already exist on disk. Fails fast if
    any app is missing.

.EXAMPLE
    .\scripts\package_bundle.ps1
    .\scripts\package_bundle.ps1 -SkipBuild
#>

[CmdletBinding()]
param(
    [string]$Version,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# The three app-images to merge. 'Name' is the jpackage launcher/app-image folder name;
# it also names the .exe, the .ico and the .cfg. 'Module' is the Gradle project path.
$Apps = @(
    [pscustomobject]@{ Name = 'Skaldoria';       Module = ':skaldoria-presentation' }
    [pscustomobject]@{ Name = 'SkaldoriaWriter'; Module = ':skaldoria-writer' }
    [pscustomobject]@{ Name = 'SkaldoriaCanvas'; Module = ':skaldoria-canvas' }
    [pscustomobject]@{ Name = 'SkaldoriaCV';     Module = ':skaldoria-cv' }
)

function Get-AppImageDir([pscustomobject]$App) {
    # skaldoria-presentation -> module folder is the module path minus the leading ':'.
    $moduleFolder = $App.Module.TrimStart(':')
    Join-Path $ProjectRoot "$moduleFolder\build\compose\binaries\main\app\$($App.Name)"
}

# The version the build itself stamps into launchers and BuildInfo.VERSION. Artefact names
# must agree with it, so it is read from Gradle rather than assumed.
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

function Invoke-DistributableBuild {
    Write-Host "`n[1/4] Building native app-images for all apps..." -ForegroundColor Yellow
    $tasks = $Apps | ForEach-Object { "$($_.Module):createDistributable" }
    Push-Location $ProjectRoot
    try {
        & .\gradlew.bat @tasks --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw 'Gradle build failed!'
        }
    } finally {
        Pop-Location
    }
}

# Copies one app-image into the shared bundle: launcher + icon to the root, the whole app\
# folder merged in, and the runtime copied only the first time.
function Add-AppToBundle {
    param(
        [pscustomobject]$App,
        [string]$BundleDir,
        [bool]$CopyRuntime
    )

    $src = Get-AppImageDir $App
    if (-not (Test-Path $src)) {
        throw "App-image for '$($App.Name)' not found at:`n  $src`nRun without -SkipBuild, or build $($App.Module):createDistributable first."
    }

    Write-Host "  -> Merging $($App.Name)..." -ForegroundColor DarkCyan

    # Launcher + icon live at the app-image root.
    Get-ChildItem -Path $src -File | ForEach-Object {
        Copy-Item $_.FullName -Destination (Join-Path $BundleDir $_.Name) -Force
    }

    # app\ folder: union of every app's jars/config/resources. Content-hashed jar names make
    # this a safe merge (identical jars overwrite themselves; different ones coexist).
    $bundleAppDir = Join-Path $BundleDir 'app'
    if (-not (Test-Path $bundleAppDir)) { New-Item -ItemType Directory -Path $bundleAppDir | Out-Null }
    Copy-Item -Path (Join-Path $src 'app\*') -Destination $bundleAppDir -Recurse -Force

    # runtime\ is the same JBR for every app; copy it exactly once.
    if ($CopyRuntime) {
        Write-Host "     copying shared runtime..." -ForegroundColor DarkGray
        Copy-Item -Path (Join-Path $src 'runtime') -Destination $BundleDir -Recurse -Force
    }
}

# --- Resolve version -------------------------------------------------------------------
$buildVersion = Get-GradleVersion
if (-not $Version) {
    $Version = $buildVersion
} elseif ($Version -ne $buildVersion) {
    Write-Error "Version mismatch: -Version '$Version' but the build produces '$buildVersion'. Change appVersion in build.gradle.kts instead of overriding it here."
    exit 1
}

Write-Host ''
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host " Skaldoria Suite -- Combined Portable Bundle" -ForegroundColor Cyan
Write-Host " Version: $Version" -ForegroundColor Yellow
Write-Host " Location: $ProjectRoot" -ForegroundColor DarkGray
Write-Host '==========================================================' -ForegroundColor Cyan

# --- Step 1: Build ---------------------------------------------------------------------
if ($SkipBuild) {
    Write-Host "`n[1/4] Skipping build (-SkipBuild). Merging existing app-images." -ForegroundColor DarkYellow
} else {
    Invoke-DistributableBuild
}

# --- Step 2: Merge ---------------------------------------------------------------------
Write-Host "`n[2/4] Assembling merged bundle..." -ForegroundColor Yellow

$distDir = Join-Path $ProjectRoot 'dist'
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir | Out-Null }

$bundleName = "Skaldoria-Suite-v$Version-windows-x64"
$bundleDir  = Join-Path $distDir $bundleName
if (Test-Path $bundleDir) { Remove-Item $bundleDir -Recurse -Force }
New-Item -ItemType Directory -Path $bundleDir | Out-Null

$first = $true
foreach ($app in $Apps) {
    Add-AppToBundle -App $app -BundleDir $bundleDir -CopyRuntime:$first
    $first = $false
}

# --- Step 3: Zip -----------------------------------------------------------------------
Write-Host "`n[3/4] Creating portable archive..." -ForegroundColor Yellow
$zipPath = Join-Path $distDir "$bundleName.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path (Join-Path $bundleDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
Write-Host "  -> $bundleName.zip" -ForegroundColor DarkCyan

# --- Step 4: Checksum ------------------------------------------------------------------
Write-Host "`n[4/4] Generating SHA-256 checksum..." -ForegroundColor Yellow
$hash = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash.ToLower()
$checksumPath = "$zipPath.sha256"
"$hash  $bundleName.zip" | Out-File -FilePath $checksumPath -Encoding utf8

$zipSizeMB    = [math]::Round(((Get-Item $zipPath).Length / 1MB), 2)
$bundleSizeMB = [math]::Round((( Get-ChildItem $bundleDir -Recurse -File | Measure-Object Length -Sum ).Sum / 1MB), 2)

Write-Host ''
Write-Host 'DONE. Combined bundle ready:' -ForegroundColor Green
Write-Host "  Folder : dist\$bundleName ($bundleSizeMB MB)" -ForegroundColor Gray
Write-Host "  Archive: dist\$bundleName.zip ($zipSizeMB MB)" -ForegroundColor Gray
Write-Host "  SHA-256: $hash" -ForegroundColor Gray
Write-Host ''
Write-Host '  Launchers in the bundle root:' -ForegroundColor White
$Apps | ForEach-Object { Write-Host "    - $($_.Name).exe" -ForegroundColor Gray }
Write-Host ''
