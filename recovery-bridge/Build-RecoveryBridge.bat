@echo off
setlocal
set "HERE=%~dp0"
where dotnet.exe >nul 2>nul
if errorlevel 1 (
    echo .NET 8 SDK is required to build the BLE recovery bridge.
    exit /b 1
)
dotnet publish "%HERE%AltosRecoveryBridge.csproj" --configuration Release --runtime win-x64 --self-contained true --output "%HERE%publish"
if errorlevel 1 exit /b 1
if not exist "%HERE%publish\AltosRecoveryBridge.exe" (
    echo .NET did not produce the recovery bridge executable.
    exit /b 1
)
echo BLE recovery bridge built at "%HERE%publish\AltosRecoveryBridge.exe"
endlocal
