<#
.SYNOPSIS
    Builds the complete Skaldoria release for Windows and Linux locally.

.DESCRIPTION
    Runs the Windows packager on the host and the Linux packager inside WSL 2. Artifacts for
    all four desktop apps are kept under dist/windows and dist/linux, with a combined checksum
    manifest at dist/checksums-sha256.txt. No GitHub Actions workflow is used.

.EXAMPLE
    .\scripts\release-all.ps1
    .\scripts\release-all.ps1 -WslDistribution Ubuntu -PublishGitHub -Draft
#>

[CmdletBinding()]
param(
    [string]$Version,
    [string]$WslDistribution = 'Ubuntu',
    [string]$OutputDirectory = 'dist',
    [switch]$PublishGitHub,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipTests,
    [switch]$SkipRenderTests,
    [switch]$SkipWindows,
    [switch]$SkipLinux,
    [switch]$SkipWindowsInstallers,
    [switch]$SkipLinuxInstallers
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Resolve-ReleaseRoot {
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

function Get-RelativePath {
    param(
        [string]$BasePath,
        [string]$TargetPath
    )

    # System.IO.Path.GetRelativePath is unavailable in Windows PowerShell 5.1, which is what
    # the .bat launcher uses. Uri.MakeRelativeUri keeps the script compatible with both 5.1
    # and modern PowerShell.
    $baseWithSeparator = $BasePath.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $baseUri = [Uri]$baseWithSeparator
    $targetUri = [Uri]$TargetPath
    [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace('/', '\')
}

function Get-GradleVersion {
    Push-Location $ProjectRoot
    try {
        $output = & .\gradlew.bat -q printVersion --console=plain 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Could not read the Gradle version:`n$output" }
        $resolvedVersion = $output | Where-Object { $_ -match '^\d+\.\d+\.\d+' } | Select-Object -Last 1
        if (-not $resolvedVersion) { throw "Gradle did not report a semantic version." }
        $resolvedVersion.ToString().Trim()
    } finally {
        Pop-Location
    }
}

if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    throw 'WSL 2 is required. Install it with: wsl --install -d Ubuntu'
}

$buildVersion = Get-GradleVersion
if (-not $Version) {
    $Version = $buildVersion
} elseif ($Version -ne $buildVersion) {
    throw "Version mismatch: requested '$Version', but Gradle builds '$buildVersion'. Change appVersion in build.gradle.kts."
}

$releaseRoot = Resolve-ReleaseRoot $OutputDirectory
$windowsDir = Join-Path $releaseRoot 'windows'
$linuxDir = Join-Path $releaseRoot 'linux'
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null

Write-Host ''
Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host ' Skaldoria Suite - Windows + Linux Local Release' -ForegroundColor Cyan
Write-Host " Version: $Version" -ForegroundColor Yellow
Write-Host " WSL:     $WslDistribution" -ForegroundColor DarkGray
Write-Host " Output:  $releaseRoot" -ForegroundColor DarkGray
Write-Host '==========================================================' -ForegroundColor Cyan

if ($SkipWindows) {
    if (-not (Get-ChildItem -LiteralPath $windowsDir -File -ErrorAction SilentlyContinue)) {
        throw "-SkipWindows requires existing artifacts in '$windowsDir'."
    }
    Write-Host "`n[1/4] Preserving existing Windows release (-SkipWindows)." -ForegroundColor DarkYellow
} else {
    Write-Host "`n[1/4] Building Windows release..." -ForegroundColor Yellow
    $windowsArgs = @{
        Version = $Version
        OutputDirectory = $windowsDir
        SkipTests = $SkipTests
        SkipRenderTests = $SkipRenderTests
        SkipInstallers = $SkipWindowsInstallers
    }
    & (Join-Path $PSScriptRoot 'package_release.ps1') @windowsArgs
}

if ($SkipLinux) {
    if (-not (Get-ChildItem -LiteralPath $linuxDir -File -ErrorAction SilentlyContinue)) {
        throw "-SkipLinux requires existing artifacts in '$linuxDir'."
    }
    Write-Host "`n[2/4] Preserving existing Linux release (-SkipLinux)." -ForegroundColor DarkYellow
} else {
    Write-Host "`n[2/4] Checking WSL and building Linux release..." -ForegroundColor Yellow
    & wsl.exe -d $WslDistribution -- bash -lc 'command -v java >/dev/null && command -v tar >/dev/null && command -v sha256sum >/dev/null'
    if ($LASTEXITCODE -ne 0) {
        throw "WSL distribution '$WslDistribution' needs Java, tar, and sha256sum. Install a JDK 17 or newer plus coreutils."
    }

    # wsl.exe treats unquoted backslashes as Bash escapes. Literal single quotes are intentionally
    # passed through so wslpath receives one intact Windows path, including any spaces.
    $wslProjectRoot = (& wsl.exe -d $WslDistribution -- wslpath -a "'$ProjectRoot'").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $wslProjectRoot) {
        throw "Could not translate the project path for WSL distribution '$WslDistribution'."
    }
    $relativeLinuxOutput = (Get-RelativePath -BasePath $ProjectRoot -TargetPath $linuxDir).Replace('\', '/')
    $linuxArgs = @(
        '-d', $WslDistribution, '--',
        'bash', "$wslProjectRoot/scripts/build_linux.sh",
        '--version', $Version,
        '--output-dir', "$wslProjectRoot/$relativeLinuxOutput"
    )
    if ($SkipTests) { $linuxArgs += '--skip-tests' }
    if ($SkipRenderTests) { $linuxArgs += '--skip-render-tests' }
    if ($SkipLinuxInstallers) { $linuxArgs += '--skip-installers' }
    & wsl.exe @linuxArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Linux release failed in WSL with exit code $LASTEXITCODE."
    }
}

Write-Host "`n[3/4] Writing combined SHA-256 checksums..." -ForegroundColor Yellow
$combinedChecksumPath = Join-Path $releaseRoot 'checksums-sha256.txt'
$checksumLines = Get-ChildItem -LiteralPath $releaseRoot -Recurse -File |
    Where-Object FullName -ne $combinedChecksumPath |
    Sort-Object FullName |
    ForEach-Object {
        $relativePath = (Get-RelativePath -BasePath $releaseRoot -TargetPath $_.FullName).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relativePath"
    }
$checksumLines | Set-Content -LiteralPath $combinedChecksumPath -Encoding utf8
Write-Host "  -> $combinedChecksumPath" -ForegroundColor Green

Write-Host "`n[4/4] Finalizing release..." -ForegroundColor Yellow
if ($PublishGitHub) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI 'gh' is required for -PublishGitHub. Install it and run 'gh auth login'."
    }
    $releaseFiles = Get-ChildItem -LiteralPath $releaseRoot -Recurse -File | Select-Object -ExpandProperty FullName
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
} else {
    Write-Host '  -> Artifacts are ready locally; nothing was uploaded.' -ForegroundColor Green
}

Write-Host "`nComplete Windows and Linux release: $releaseRoot`n" -ForegroundColor Green
