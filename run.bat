@echo off
setlocal
pushd "%~dp0" || exit /b 1

java -jar .\target\cgengine.jar
if errorlevel 1 goto failed
popd
exit /b 0

:failed
echo Build or application startup failed. See the output above.
pause
popd
exit /b 1
