@echo off
setlocal
cd /d "%~dp0"

echo [INFO] starting one-click no-cli init and smoke test...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\one_click_no_cli.ps1"

echo.
if errorlevel 1 (
  echo [FAILED] one-click workflow failed.
) else (
  echo [SUCCESS] one-click workflow finished.
)
echo.
pause
endlocal
