@echo off
chcp 65001 >nul
cd /d "%~dp0nginx-1.20.2"
if not exist "html\admin\index.html" (
  echo [!] html\admin\index.html 不存在，请重新 git pull 或按 README 第五节重新构建
  pause
  exit /b 1
)
start "" nginx.exe
echo.
echo [OK] nginx 已启动，浏览器打开  http://localhost
echo      默认管理员账号：admin / 123456（由你的后端校验）
echo      停止请运行 stop.bat，改配置后运行 reload.bat
echo.
pause
