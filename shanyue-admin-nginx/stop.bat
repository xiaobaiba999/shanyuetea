@echo off
chcp 65001 >nul
cd /d "%~dp0nginx-1.20.2"
nginx.exe -s quit
echo [OK] nginx 已停止
pause
