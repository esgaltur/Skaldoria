@echo off
setlocal
echo Launching Skaldoria Windows and Linux Release Packager...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release-all.ps1" %*
pause
