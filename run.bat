@echo off
setlocal
chcp 65001 > nul
title Barnocraft Java Edition
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
set "RESULT=%ERRORLEVEL%"
pause
exit /b %RESULT%
