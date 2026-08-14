<#
.SYNOPSIS
    Quick shortcut to build, test, and package Skaldoria releases locally.

.DESCRIPTION
    A thin forwarder to package_release.ps1. Every parameter is optional and only the ones
    you actually pass are forwarded, so the version stays resolved from Gradle
    (`gradlew -q printVersion`) rather than being re-defaulted here. This file used to carry
    its own `1.0.0` default, which silently overrode the build's real version.

.EXAMPLE
    .\scripts\release.ps1
    .\scripts\release.ps1 -PublishGitHub -Draft
#>
param(
    [string]$Version,
    [string]$OutputDirectory,
    [switch]$PublishGitHub,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipTests,
    [switch]$SkipRenderTests,
    [switch]$SkipInstallers
)

$scriptPath = Join-Path $PSScriptRoot "package_release.ps1"
& $scriptPath @PSBoundParameters
