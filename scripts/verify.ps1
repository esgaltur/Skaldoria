<#
.SYNOPSIS
    Runs the full local verification gate: every module test suite and the zero-warning build.

.DESCRIPTION
    Nothing verifies this project automatically — .github/workflows/ci.yml runs the same two
    checks, but only when someone dispatches it by hand (PLT-01). So every rule in
    CONTRIBUTING.md is enforced on the machine that cuts the release, and this script is that
    enforcement, in one command, so the rules stop depending on someone remembering them:

      1. Every module test suite — Presentation, Markdown, shared UI, Writer, and Canvas.
      2. Every module's production and test sources compiled with `-PwarningsAsErrors`.

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
    [switch]$SkipRenderTests,
    [string]$BuildRoot
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$renderFlag = @()
if ($SkipRenderTests) {
    $renderFlag = @('-PskipRenderTests')
    Write-Host '  -> Render guards stood down (-PskipRenderTests): they will be skipped, not passed.' -ForegroundColor DarkYellow
}

$buildRootFlag = @()
if ($BuildRoot) {
    $buildRootFlag = @("-PreleaseBuildRoot=$BuildRoot")
}

Push-Location $ProjectRoot
try {
    Write-Host "`n[1/2] Running all test suites..." -ForegroundColor Yellow
    & .\gradlew.bat :skaldoria-presentation:desktopTest :skaldoria-markdown:test :skaldoria-shared-ui:desktopTest :skaldoria-writer:desktopTest :skaldoria-canvas:desktopTest :skaldoria-cv-core:test :skaldoria-cv:desktopTest -PwarningsAsErrors --no-daemon @renderFlag @buildRootFlag
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Test suite failed.'
        exit $LASTEXITCODE
    }
    Write-Host '  -> All tests passed.' -ForegroundColor Green

    Write-Host "`n[2/2] Compiling with warnings as errors..." -ForegroundColor Yellow
    & .\gradlew.bat :skaldoria-presentation:compileKotlinDesktop :skaldoria-presentation:compileTestKotlinDesktop :skaldoria-shared-ui:compileKotlinDesktop :skaldoria-shared-ui:compileTestKotlinDesktop :skaldoria-writer:compileKotlinDesktop :skaldoria-writer:compileTestKotlinDesktop :skaldoria-canvas:compileKotlinDesktop :skaldoria-canvas:compileTestKotlinDesktop :skaldoria-cv-core:compileKotlin :skaldoria-cv-core:compileTestKotlin :skaldoria-cv:compileKotlinDesktop :skaldoria-cv:compileTestKotlinDesktop -PwarningsAsErrors --no-daemon @buildRootFlag
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Compilation reported warnings. Fix the cause; do not suppress it (CONTRIBUTING.md section 10).'
        exit $LASTEXITCODE
    }
    Write-Host '  -> Zero warnings.' -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host "`nVerification passed.`n" -ForegroundColor Green
