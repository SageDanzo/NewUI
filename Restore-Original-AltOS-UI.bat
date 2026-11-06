@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Switch-Installed-AltOS-UI.ps1" -Restore
if errorlevel 1 echo The original UI could not be restored automatically. Check the message above.
echo.
pause
