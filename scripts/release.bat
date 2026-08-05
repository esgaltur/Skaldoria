@echo off
setlocal
echo Launching Skaldoria Release Packager...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package_release.ps1" %*
pause
