# 山月茶咖 SHANYUE TEA & COFFEE

奶茶 / 咖啡点单系统三端完整代码（「苍穹外卖」业务模式的教学重构项目）。

## 仓库结构

| 目录 | 端 | 技术栈 |
|---|---|---|
| `shanyue-mp/` | 微信小程序端（顾客点单） | 原生微信小程序（JS + WXML / WXSS） |
| `shanyue-vue3/` | 管理端前端 + H5 顾客端 | Vue 3 + Vite + TypeScript + Element Plus / Vant 4 + Pinia |
| `shanyue-server-skeleton/` | 后端服务 | Java 17 + Spring Boot 2.7 + MyBatis + MySQL + Redis |

## 快速开始

1. **后端**：按 `shanyue-server-skeleton/README.md` 操作 —— JDK 17 + MySQL 8 + Redis，先执行 `sql/schema.sql` 与 `sql/data.sql` 建库，再以 Maven 聚合工程方式启动，服务端口 `8080`。
2. **管理端前端**：`cd shanyue-vue3/admin && npm install && npm run dev`
3. **H5 顾客端**：`cd shanyue-vue3/h5 && npm install && npm run dev`
4. **微信小程序**：微信开发者工具导入 `shanyue-mp/` 目录并填写自己的 AppID；联调时把 `shanyue-mp/utils/config.js` 的 `BASE_URL` 指向后端地址。

## 配置说明

- 后端配置位于 `shanyue-server-skeleton/shanyue-server/src/main/resources/`：
  - `application.yml`：端口与 profile，无需改动；
  - `application-dev.yml`：数据库账号密码、JWT 密钥、微信小程序 appid / secret —— **均为占位符，请替换为自己的配置后再运行**。
- 完整接口契约文档见 `shanyue-vue3/docs/api-docs.md`。
