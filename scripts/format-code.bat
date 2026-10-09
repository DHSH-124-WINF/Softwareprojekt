@echo off
rem Formats backend (Maven formatter plugin) and frontend (Prettier via npm).
setlocal
set "ROOT_DIR=%~dp0.."

echo ==^> Formatting backend
rem Absolute config path needed (relative ones are resolved per module)
pushd "%ROOT_DIR%\backend" || exit /b 1
call mvn -B formatter:format "-Dconfigfile=%CD%\formatter\formatter.xml"
if errorlevel 1 (popd & exit /b 1)
popd

echo ==^> Formatting frontend
rem Requires "npm install" in frontend\ once
pushd "%ROOT_DIR%\frontend" || exit /b 1
call npm run format
if errorlevel 1 (popd & exit /b 1)
popd
