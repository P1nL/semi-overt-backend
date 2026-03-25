@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
chcp 65001 >nul
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%dev-down.ps1" %*

endlocal
