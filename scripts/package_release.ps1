<#
.SYNOPSIS
    Builds and packages Skaldoria into a standalone distributable ZIP archive for GitHub Releases.

.DESCRIPTION
    Runs gradle createDistributable, packages the standalone application bundle into a clean
    ZIP archive under the 'dist/' folder, and optionally publishes to GitHub Releases via 'gh'.

.EXAMPLE
    .\scripts\package_release.ps1 -Version "1.0.0"
    .\scripts\package_release.ps1 -Version "1.0.0" -PublishGitHub
#>

param(
    [string]$Version = "1.0.0",
    [switch]$PublishGitHub
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 👑 Skaldoria Release Packager v$Version" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Ensure dist output directory exists
$distDir = Join-Path $PSScriptRoot "..\dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

# 2. Build Native Distributable
Write-Host "`n[1/3] Building native desktop application..." -ForegroundColor Yellow
& .\gradlew.bat createDistributable

$appDir = Join-Path $PSScriptRoot "..\build\compose\binaries\main\app\Skaldoria"
if (-not (Test-Path $appDir)) {
    Write-Error "Build output not found at: $appDir"
}

# 3. Create Distribution ZIP Archive
$zipName = "Skaldoria-v$Version-windows-x64.zip"
$zipPath = Join-Path $distDir $zipName

Write-Host "`n[2/3] Compressing release package into $zipName..." -ForegroundColor Yellow
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}
Compress-Archive -Path "$appDir\*" -DestinationPath $zipPath -CompressionLevel Optimal

$fileSizeMB = [math]::Round(((Get-Item $zipPath).Length / 1MB), 2)
Write-Host "-> Package created: $zipPath ($fileSizeMB MB)" -ForegroundColor Green

# 4. Optional GitHub Release upload
if ($PublishGitHub) {
    Write-Host "`n[3/3] Uploading release to GitHub via 'gh' CLI..." -ForegroundColor Yellow
    if (Get-Command "gh" -ErrorAction SilentlyContinue) {
        & gh release create "v$Version" $zipPath --title "Skaldoria v$Version" --notes-file "CHANGELOG.md"
        Write-Host "-> GitHub Release v$Version published successfully!" -ForegroundColor Green
    } else {
        Write-Warning "'gh' CLI not found. Please upload $zipPath manually at https://github.com/your-repo/releases/new"
    }
} else {
    Write-Host "`n[3/3] Done! Ready to upload to GitHub Releases:" -ForegroundColor Green
    Write-Host "   File: $zipPath" -ForegroundColor White
    Write-Host "   URL:  https://github.com/<your-username>/<your-repo>/releases/new`n" -ForegroundColor Gray
}
