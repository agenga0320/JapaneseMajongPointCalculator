@echo off
rem Build signed APK, commit and push. With a version, also bump it and publish a GitHub Release.
rem   release.bat
rem   release.bat 1.5.0 "release notes"
chcp 65001 > nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" %*
pause
