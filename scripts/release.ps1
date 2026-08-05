<#
.SYNOPSIS
    Quick shortcut to build, test, and package Skaldoria releases locally.
#>
param(
    [string]$Version = "1.0.0",
    [switch]$PublishGitHub,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipTests
)

$scriptPath = Join-Path $PSScriptRoot "package_release.ps1"
& $scriptPath -Version $Version -PublishGitHub:$PublishGitHub -Draft:$Draft -Prerelease:$Prerelease -SkipTests:$SkipTests
