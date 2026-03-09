@echo off
setlocal
cd /d "%~dp0"

echo [INFO] starting full-auto launch...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\full_auto_launch.ps1"

echo.
if errorlevel 1 (
  echo [FAILED] full-auto launch failed.
) else (
  echo [SUCCESS] full-auto launch finished.
)
echo.
pause
endlocal
