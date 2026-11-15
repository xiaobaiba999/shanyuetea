# 山月茶咖 · 管理端部署包（nginx 版）

> 一句话：本目录 = **已构建好的管理端前端（html/admin）+ 免安装的 nginx-1.20.2 + 配好的反向代理**。
> clone 本仓库后，电脑**不需要装 Node/Bun、不需要装 nginx**，双击 `start.bat` 即可测试。

【v1.6 更新】本包随仓库同步更新：`html/admin` 为「新中式 UI + 茶小月品牌 IP」版本的构建产物；
本 README 与 GitHub 仓库 `shanyue-admin-nginx/` 目录一一对应。
【沿用 v1.5 行为】页面演示数据（mock）默认关闭：登录、员工管理等一切数据只来自你的后端，
教程没写到的模块显示空状态，随你逐章实现逐章亮起。

## 0. 目录说明

```
shanyue-admin-nginx/
├─ start.bat / stop.bat / reload.bat   ← Windows 双击用（主入口）
├─ start.sh  / stop.sh                ← Linux/WSL 用（需系统已装 nginx）
├─ README.md                          ← 本文件
└─ nginx-1.20.2/                      ← 官方 nginx Windows 版（免安装）
   ├─ nginx.exe                       ← 双击即运行（停掉用 stop.bat）
   ├─ conf/nginx.conf                 ← 反代规则都在这里（本包核心）
   ├─ html/admin/                     ← 管理端前端构建产物（index.html + assets）
   ├─ logs/                           ← access.log / error.log 在这里
   └─ temp/                           ← nginx 工作目录（勿删，删了会启动闪退）
```

## 1. 使用三步走

1. 先启动后端 Spring Boot（端口 8080，见 `shanyue-server-skeleton/` 的 README）
2. 双击 `start.bat`
3. 浏览器打开 **http://localhost**
   默认管理员账号：`admin` / `123456`

> 还没写后端、只想先看看管理端界面？
> 后端没启动时登录会报「网络错误 / Network Error」——这是正常现象（nginx 把 /api 请求
> 转发给 8080，没人应答就报错）。确实想无后端预览界面：进 `shanyue-vue3/admin/`，
> 新建 `.env` 写一行 `VITE_USE_MOCK=true`，装 bun 后 `bun install && bun run dev`。

## 2. nginx 到底做了什么（答辩可讲的知识点）

| 浏览器请求 | nginx 动作 | 实际去向 |
|---|---|---|
| `GET /`、`/login`、`/dashboard` | 静态托管 + `try_files` 回退 | `html/admin/index.html`（Vue history 路由刷新不 404） |
| `/api/employee/login` | 反向代理 + 前缀重写 | `http://localhost:8080/admin/employee/login` |
| `/ws/xxx` | WebSocket 反代（Upgrade 头） | `http://localhost:8080/ws/xxx`（来单提醒） |
| `/upload/xxx.png` | 静态图片代理 | `http://localhost:8080/upload/xxx.png`（后端本地上传目录） |

要点：
- **前后端联调零配置切换**：dev 阶段 `vite` 的 proxy 和这里 conf 是同款 `/api → /admin` 映射，
  前端代码一行不用改。
- **跨域问题为什么消失**：浏览器只跟 `localhost:80`（nginx）打交道，后端对浏览器不可见，
  天然同源。
- **SPA 部署三件套**：静态 root、history 路由 `try_files`、接口反代——毕设里讲这个结构很加分。

## 3. 停止 / 重启 / 改配置

Windows：`stop.bat` 停止；改完 `nginx-1.20.2/conf/nginx.conf` 后 `reload.bat` 生效。
Linux/WSL：`./stop.sh`；`nginx -s reload` 或再跑 `./start.sh`。

## 4. 常见问题（FAQ）

1) **80 端口被占用**
   把 `conf/nginx.conf` 里的 `listen 80` 改成 `listen 8081`，执行 `reload.bat`，改用
   `http://localhost:8081` 访问。（Linux 下 80 端口需要 sudo，普通用户建议直接用 8081）

2) **登录报错 / 接口不通**
   确认后端已在 8080 启动。本包把 `/api/` 请求转发到 `http://localhost:8080/admin/`。

3) **图片不显示**
   图片由后端 `/upload/**` 提供，确认后端已配置本地上传目录
   （`application-dev.yml` 的 `shanyue.localStorage.path`）。

4) **修改了前端代码，想更新本包**
   见下节「本包是怎么生成的」，构建完把 dist 覆盖到 `html/admin/` 即可。

5) **请求日志看哪里**
   `nginx-1.20.2/logs/` 目录（access.log / error.log）。

6) **启动闪退（Windows）**
   多半是 `temp/` 工作目录缺失（git 不追踪空目录，本仓库已放 `.gitkeep` 防住；
   若手动删过，按 conf 同级重建 `temp` 下五个子目录再启动）。

## 5. 本包是怎么生成的（更新流程）

```bash
cd shanyue-vue3/admin
bun install --registry https://registry.npmmirror.com
bun run build                      # 产物在 admin/dist/
# 把 dist 内容覆盖到本目录：
rsync -a --delete dist/ ../../shanyue-admin-nginx/nginx-1.20.2/html/admin/
git add -A && git commit -m "build: 更新管理端 nginx 部署包" && git push
```

> 想验证 conf 语法：`nginx.exe -t`（Windows）或 `nginx -t -p . -c conf/nginx.conf`（Linux）。

---
nginx 以 BSD-2-Clause 许可随包分发，许可证见 `nginx-1.20.2/docs/`。
