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
$ReleaseBuildRoot = 'build/release/windows'
$ReleaseBuildRootPath = Join-Path $ProjectRoot 'build\release\windows'

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
        & .\gradlew.bat @Tasks -PwarningsAsErrors "-PreleaseBuildRoot=$ReleaseBuildRoot" --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Assert-NoConcurrentGradleBuild {
    # Gradle does not serialize separate invocations that target the same module build folders.
    # A concurrent IDE/terminal build can therefore remove jpackage or generated-source inputs
    # during a release. Daemon processes are harmless; active wrapper processes are not.
    $activeBuilds = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        if (-not $_.CommandLine -or $_.ProcessId -eq $PID) { return $false }
        $normalizedCommandLine = $_.CommandLine.Replace('/', '\')
        $normalizedCommandLine.IndexOf('gradle-wrapper.jar', [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
            $normalizedCommandLine.IndexOf($ProjectRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0
    }
    if ($activeBuilds) {
        $processIds = ($activeBuilds | Select-Object -ExpandProperty ProcessId) -join ', '
        throw "Another Gradle build is using this checkout (PID: $processIds). Wait for it to finish before releasing."
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

Assert-NoConcurrentGradleBuild

Write-Host ''
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host ' Skaldoria Suite - Windows Local Release' -ForegroundColor Cyan
Write-Host " Version: $Version" -ForegroundColor Yellow
Write-Host " Output:  $distDir" -ForegroundColor DarkGray
Write-Host '==========================================================' -ForegroundColor Cyan

Write-Host "`nPreparing clean Windows build outputs..." -ForegroundColor Yellow
$cleanTasks = @(
    ':skaldoria-markdown:clean',
    ':skaldoria-shared-ui:clean',
    ':skaldoria-cv-core:clean'
) + ($Applications | ForEach-Object { ":$($_.Module):clean" })
Invoke-Gradle $cleanTasks

if (-not $SkipTests) {
    Write-Host "`n[1/4] Verifying every module..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'verify.ps1') -SkipRenderTests:$SkipRenderTests -BuildRoot $ReleaseBuildRoot
} else {
    Write-Host "`n[1/4] Verification skipped by request." -ForegroundColor DarkYellow
}

Write-Host "`n[2/4] Building all Windows applications..." -ForegroundColor Yellow
$tasks = foreach ($app in $Applications) {
    ":$($app.Module):createDistributable"
    ":$($app.Module):packageUberJarForCurrentOS"
}
Invoke-Gradle $tasks
if (-not $SkipInstallers) {
    # Compose's installer tasks share and clean jpackage temporary inputs. Running MSI and EXE
    # in one Gradle invocation lets the first format invalidate inputs of the second, so each
    # format gets its own task graph. Apps of the same format use module-local directories.
    Invoke-Gradle ($Applications | ForEach-Object { ":$($_.Module):packageMsi" })
    Invoke-Gradle ($Applications | ForEach-Object { ":$($_.Module):packageExe" })
}

Write-Host "`n[3/4] Collecting artifacts..." -ForegroundColor Yellow
foreach ($app in $Applications) {
    $composeDir = Join-Path $ReleaseBuildRootPath "$($app.Module)\compose"
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
