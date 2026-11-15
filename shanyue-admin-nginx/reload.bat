@echo off
chcp 65001 >nul
cd /d "%~dp0nginx-1.20.2"
nginx.exe -s reload
echo [OK] nginx 已按 conf\nginx.conf 重新加载
pause
