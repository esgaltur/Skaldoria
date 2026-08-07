<#
.SYNOPSIS
    Runs the full local verification gate: both test suites and the zero-warning build.

.DESCRIPTION
    There is deliberately no CI for this project — every rule in CONTRIBUTING.md is enforced
    on the machine that cuts the release. This script is that enforcement, in one command, so
    the rules stop depending on someone remembering them:

      1. `desktopTest` and `:skaldoria-markdown:test` — both modules, not just the app.
      2. `compileKotlinDesktop compileTestKotlinDesktop -PwarningsAsErrors` — the zero-warning
         NFR (CONTRIBUTING.md section 6). Production *and* test code must compile clean.

    `package_release.ps1` calls this before it builds anything, so a release cannot be cut
    over a failing suite or a new warning.

.PARAMETER SkipRenderTests
    Stands down the render guards (PLT-08). Use on a box where a display exists but rendering
    should not be attempted; they are then reported as skipped, never as passed.

.EXAMPLE
    .\scripts\verify.ps1
    .\scripts\verify.ps1 -SkipRenderTests
#>

[CmdletBinding()]
param(
    [switch]$SkipRenderTests
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$renderFlag = @()
if ($SkipRenderTests) {
    $renderFlag = @('-PskipRenderTests')
    Write-Host '  -> Render guards stood down (-PskipRenderTests): they will be skipped, not passed.' -ForegroundColor DarkYellow
}

Push-Location $ProjectRoot
try {
    Write-Host "`n[1/2] Running both test suites..." -ForegroundColor Yellow
    & .\gradlew.bat desktopTest :skaldoria-markdown:test --no-daemon @renderFlag
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Test suite failed.'
        exit $LASTEXITCODE
    }
    Write-Host '  -> All tests passed.' -ForegroundColor Green

    Write-Host "`n[2/2] Compiling with warnings as errors..." -ForegroundColor Yellow
    & .\gradlew.bat compileKotlinDesktop compileTestKotlinDesktop -PwarningsAsErrors --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Compilation reported warnings. Fix the cause; do not suppress it (CONTRIBUTING.md section 10).'
        exit $LASTEXITCODE
    }
    Write-Host '  -> Zero warnings.' -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host "`nVerification passed.`n" -ForegroundColor Green
