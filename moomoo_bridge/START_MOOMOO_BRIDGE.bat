@echo off
title NEWS1 Moomoo Bridge
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  echo Python belum terpasang. Install Python 3 terlebih dahulu dan centang Add Python to PATH.
  pause
  exit /b 1
)
echo [1/2] Memastikan moomoo-api terpasang...
py -m pip install -r requirements.txt
if errorlevel 1 pause & exit /b 1
echo.
echo [2/2] Menjalankan bridge...
py moomoo_bridge.py
pause
