<#
.SYNOPSIS
    Builds every Skaldoria desktop application for Windows.

.DESCRIPTION
    Verifies the repository, builds native Windows MSI/EXE installers, portable ZIP archives,
    and runnable JARs for Studio, Writer, Canvas, and CV, then writes SHA-256 checksums.
    Publishing is local and opt-in; GitHub Actions is not involved.

.PARAMETER OutputDirectory
    Artifact directory, relative to the repository root unless absolute. Defaults to
    dist/windows so Linux artifacts can coexist with the Windows release.

.PARAMETER SkipInstallers
    Builds portable ZIPs and runnable JARs but skips MSI and EXE installers.
#>

[CmdletBinding()]
param(
    [string]$Version,
    [string]$OutputDirectory = 'dist\windows',
    [switch]$PublishGitHub,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipTests,
    [switch]$SkipRenderTests,
    [switch]$SkipInstallers
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$Applications = @(
    [pscustomobject]@{ Module = 'skaldoria-presentation'; Product = 'Skaldoria';       Package = 'Skaldoria' },
    [pscustomobject]@{ Module = 'skaldoria-writer';       Product = 'SkaldoriaWriter'; Package = 'SkaldoriaWriter' },
    [pscustomobject]@{ Module = 'skaldoria-canvas';       Product = 'SkaldoriaCanvas'; Package = 'SkaldoriaCanvas' },
    [pscustomobject]@{ Module = 'skaldoria-cv';           Product = 'SkaldoriaCV';     Package = 'SkaldoriaCV' }
)

function Invoke-Gradle {
    param([string[]]$Tasks)

    Push-Location $ProjectRoot
    try {
        & .\gradlew.bat @Tasks --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Get-GradleVersion {
    Push-Location $ProjectRoot
    try {
        $output = & .\gradlew.bat -q printVersion --console=plain 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read the project version from Gradle:`n$output"
        }
        $resolvedVersion = $output | Where-Object { $_ -match '^\d+\.\d+\.\d+' } | Select-Object -Last 1
        if (-not $resolvedVersion) {
            throw "Gradle did not report a semantic version. Output was:`n$output"
        }
        $resolvedVersion.ToString().Trim()
    } finally {
        Pop-Location
    }
}

function Resolve-OutputDirectory {
    param([string]$Path)

    $resolved = if ([IO.Path]::IsPathRooted($Path)) {
        [IO.Path]::GetFullPath($Path)
    } else {
        [IO.Path]::GetFullPath((Join-Path $ProjectRoot $Path))
    }

    $projectPrefix = $ProjectRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($projectPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "OutputDirectory must be inside the repository: $resolved"
    }
    $resolved
}

function Copy-LatestArtifact {
    param(
        [string]$SourceDirectory,
        [string]$Filter,
        [string]$Destination
    )

    $source = Get-ChildItem -Path $SourceDirectory -Filter $Filter -File -ErrorAction SilentlyContinue |
        Where-Object Name -Like "*$Version*" |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if (-not $source) {
        throw "Expected version $Version artifact '$Filter' was not produced in '$SourceDirectory'."
    }
    Copy-Item -LiteralPath $source.FullName -Destination $Destination -Force
    Write-Host "  -> $(Split-Path $Destination -Leaf)" -ForegroundColor DarkCyan
}

function Write-Checksums {
    param([string]$Directory)

    $checksumPath = Join-Path $Directory 'checksums-windows-sha256.txt'
    $lines = Get-ChildItem -Path $Directory -File |
        Where-Object Name -ne 'checksums-windows-sha256.txt' |
        Sort-Object Name |
        ForEach-Object {
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $($_.Name)"
        }
    if (-not $lines) {
        throw "No Windows release artifacts were collected in '$Directory'."
    }
    $lines | Set-Content -LiteralPath $checksumPath -Encoding utf8
    Write-Host "  -> $checksumPath" -ForegroundColor Green
}

$buildVersion = Get-GradleVersion
if (-not $Version) {
    $Version = $buildVersion
} elseif ($Version -ne $buildVersion) {
    throw "Version mismatch: requested '$Version', but Gradle builds '$buildVersion'. Change appVersion in build.gradle.kts."
}

$distDir = Resolve-OutputDirectory $OutputDirectory
New-Item -ItemType Directory -Path $distDir -Force | Out-Null
Get-ChildItem -LiteralPath $distDir -File | Remove-Item -Force

Write-Host ''
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host ' Skaldoria Suite - Windows Local Release' -ForegroundColor Cyan
Write-Host " Version: $Version" -ForegroundColor Yellow
Write-Host " Output:  $distDir" -ForegroundColor DarkGray
Write-Host '==========================================================' -ForegroundColor Cyan

if (-not $SkipTests) {
    Write-Host "`n[1/4] Verifying every module..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'verify.ps1') -SkipRenderTests:$SkipRenderTests
} else {
    Write-Host "`n[1/4] Verification skipped by request." -ForegroundColor DarkYellow
}

Write-Host "`n[2/4] Building all Windows applications..." -ForegroundColor Yellow
$tasks = foreach ($app in $Applications) {
    ":$($app.Module):createDistributable"
    ":$($app.Module):packageUberJarForCurrentOS"
    if (-not $SkipInstallers) {
        ":$($app.Module):packageMsi"
        ":$($app.Module):packageExe"
    }
}
Invoke-Gradle $tasks

Write-Host "`n[3/4] Collecting artifacts..." -ForegroundColor Yellow
foreach ($app in $Applications) {
    $composeDir = Join-Path $ProjectRoot "$($app.Module)\build\compose"
    $appDir = Join-Path $composeDir "binaries\main\app\$($app.Package)"
    if (-not (Test-Path -LiteralPath $appDir -PathType Container)) {
        throw "Expected distributable was not produced: $appDir"
    }

    $zipPath = Join-Path $distDir "$($app.Product)-v$Version-windows-x64-portable.zip"
    Compress-Archive -Path (Join-Path $appDir '*') -DestinationPath $zipPath -CompressionLevel Optimal -Force
    Write-Host "  -> $(Split-Path $zipPath -Leaf)" -ForegroundColor DarkCyan

    Copy-LatestArtifact `
        -SourceDirectory (Join-Path $composeDir 'jars') `
        -Filter '*.jar' `
        -Destination (Join-Path $distDir "$($app.Product)-v$Version-windows-x64.jar")

    if (-not $SkipInstallers) {
        Copy-LatestArtifact `
            -SourceDirectory (Join-Path $composeDir 'binaries\main\msi') `
            -Filter '*.msi' `
            -Destination (Join-Path $distDir "$($app.Product)-v$Version-windows-x64.msi")
        Copy-LatestArtifact `
            -SourceDirectory (Join-Path $composeDir 'binaries\main\exe') `
            -Filter '*.exe' `
            -Destination (Join-Path $distDir "$($app.Product)-v$Version-windows-x64-setup.exe")
    }
}

Write-Host "`n[4/4] Writing SHA-256 checksums..." -ForegroundColor Yellow
Write-Checksums $distDir

$releaseFiles = Get-ChildItem -LiteralPath $distDir -File | Select-Object -ExpandProperty FullName
if ($PublishGitHub) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI 'gh' is required for -PublishGitHub. Install it and run 'gh auth login'."
    }
    $ghArgs = @('release', 'create', "v$Version") + $releaseFiles + @(
        '--title', "Skaldoria Suite v$Version",
        '--notes-file', (Join-Path $ProjectRoot 'CHANGELOG.md')
    )
    if ($Draft) { $ghArgs += '--draft' }
    if ($Prerelease) { $ghArgs += '--prerelease' }
    & gh @ghArgs
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub release creation failed with exit code $LASTEXITCODE."
    }
}

Write-Host "`nWindows release complete: $distDir`n" -ForegroundColor Green
