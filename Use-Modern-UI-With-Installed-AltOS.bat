@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Switch-Installed-AltOS-UI.ps1"
if errorlevel 1 echo The switch did not complete. Read the error above; your original backup is retained.
echo.
pause
