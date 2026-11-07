@echo off
setlocal
set "HERE=%~dp0"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
where javac.exe >nul 2>nul
if errorlevel 1 (
    echo A JDK with javac is required to build the redesigned AltOS UI.
    exit /b 1
)
where jar.exe >nul 2>nul
if errorlevel 1 (
    echo The JDK jar utility was not found.
    exit /b 1
)

set "BUILD=%HERE%build\java"
set "CLASSES=%BUILD%\classes"
if not exist "%CLASSES%" mkdir "%CLASSES%"
javac.exe -encoding UTF-8 -source 8 -target 8 -cp "%HERE%altosui-redesigned.jar;%HERE%altoslib_14.jar;%HERE%altosuilib_14.jar;%HERE%freetts.jar" -d "%CLASSES%" "%HERE%AltosDesktopTheme.java" "%HERE%AltosRecoveryLink.java" "%HERE%AltosFlightVoice.java" "%HERE%AltosUI.java"
if errorlevel 1 exit /b 1

copy /y "%HERE%altosui-redesigned.jar" "%BUILD%\altosui-redesigned.jar.building" >nul
if errorlevel 1 exit /b 1
jar.exe uf "%BUILD%\altosui-redesigned.jar.building" -C "%CLASSES%" altosui -C "%CLASSES%" org\altusmetrum\altosuilib_14\AltosFlightVoice.class
if errorlevel 1 (
    del "%BUILD%\altosui-redesigned.jar.building"
    exit /b 1
)
move /y "%BUILD%\altosui-redesigned.jar.building" "%HERE%altosui-redesigned.jar" >nul
if errorlevel 1 exit /b 1
echo Redesigned AltOS UI built successfully.
endlocal
