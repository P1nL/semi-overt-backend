@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
chcp 65001 >nul
set "PS_EXE=powershell.exe"
where pwsh >nul 2>nul
if not errorlevel 1 set "PS_EXE=pwsh"
"%PS_EXE%" -ExecutionPolicy Bypass -File "%SCRIPT_DIR%dev-down.ps1" %*

endlocal
