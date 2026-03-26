@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
chcp 65001 >nul
rem Local Windows development launcher. Not intended for Linux/cloud deployment.
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%dev-up.ps1" %*

endlocal
