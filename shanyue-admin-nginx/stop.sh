#!/usr/bin/env bash
cd "$(dirname "$0")/nginx-1.20.2"
nginx -p "$(pwd)" -c conf/nginx.conf -s quit
echo "nginx 已停止"
