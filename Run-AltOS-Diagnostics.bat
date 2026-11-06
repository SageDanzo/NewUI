@echo off
setlocal
set "HERE=%~dp0"
cd /d "%HERE%"
java -Djava.library.path="%HERE%." -jar "%HERE%altosui-redesigned.jar" %*
echo.
echo AltOS exited. Any startup error is displayed above.
pause
endlocal
