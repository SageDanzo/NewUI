@echo off
setlocal
set "HERE=%~dp0"
cd /d "%HERE%"
where javaw.exe >nul 2>nul
if errorlevel 1 (
    powershell -NoProfile -Command "Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show('Java was not found. Install Java 8 or newer, then run AltOS again.','AltOS')"
    exit /b 1
)
start "" /b javaw.exe -Djava.library.path="%HERE%." -Daltos.recovery.bridge="%HERE%recovery-bridge\publish\AltosRecoveryBridge.exe" -jar "%HERE%altosui-redesigned.jar" %*
endlocal
exit /b 0
