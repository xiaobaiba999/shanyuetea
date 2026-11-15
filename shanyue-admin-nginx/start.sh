#!/usr/bin/env bash
# Linux / WSL 用（需本机已安装 nginx；80 端口需要 sudo，或先把 conf 里的 listen 80 改成 8081）
cd "$(dirname "$0")/nginx-1.20.2"
nginx -p "$(pwd)" -c conf/nginx.conf
echo "nginx 已启动：http://localhost （停止：./stop.sh）"
