# 山月茶咖 SHANYUE TEA & COFFEE

奶茶 / 咖啡点单系统三端完整代码（「苍穹外卖」业务模式的教学重构项目）。

## 仓库结构

| 目录 | 端 | 技术栈 |
|---|---|---|
| `shanyue-mp/` | 微信小程序端（顾客点单） | 原生微信小程序（JS + WXML / WXSS） |
| `shanyue-vue3/` | 管理端前端 + H5 顾客端 | Vue 3 + Vite + TypeScript + Element Plus / Vant 4 + Pinia |
| `shanyue-server-skeleton/` | 后端服务 | Java 17 + Spring Boot 2.7 + MyBatis + MySQL + Redis |
| `shanyue-admin-nginx/` | 管理端部署包（免安装直测） | nginx 1.20.2 + 已构建的 Vue3 产物 + 配好的反代 |

## 快速开始

### ⚠️ IDEA 导入必看（启动类 / yml 识别不到？看这里）

后端是 Maven 聚合工程，**pom.xml 在 `shanyue-server-skeleton/` 这一层，仓库根目录没有 pom**。
用 IDEA `File → Open` 打开时，必须选中 **`shanyue-server-skeleton` 目录本身**（能看到 pom.xml 的那一层），
而不是仓库根目录 `shanyuetea/` —— 打开了仓库根目录，IDEA 会把它当普通文件夹：
Java 文件没有类图标、`ShanyueApplication` 没有绿色运行箭头、`application.yml` 也没有配置图标，什么都运行不了。

导入成功的标志：右下角 Maven 同步完成后，左侧出现 `shanyue / shanyue-common / shanyue-pojo / shanyue-server` 四个模块，
Project SDK 为 **JDK 17**（父 pom 已锁 `java.version=17`，装 8/11 会导致 Maven 导入直接失败，同样表现为"识别不到"）。
详细步骤见 `shanyue-server-skeleton/README.md` 第二节（含阿里云镜像配置）。

### 四个模块都在，却还是"yml 没图标 / 启动类没绿箭头"？（IDE 功能集差异，不是导入失败）

自检：左侧四模块齐全、`src/main/java` 与 `resources` 是蓝色源码根、Java 文件是咖啡杯图标、
打开 `ShanyueApplication.java` 代码不报红——这些都成立，说明 **Maven 导入已经成功**。
此时如果 `application.yml` 只是普通粉色 Y 图标（没有绿叶）、启动类没有绿色运行箭头，
缺的不是导入，而是 **IDE 的 Spring Boot 集成插件没有加载**。

判别方法：**不要看 About 里的版本/许可文字，直接查插件实际状态** ——
`Settings → Plugins` → Installed 标签 → 搜 "Spring Boot"：

- **列表里有 Spring Boot 插件但没勾选** → 勾选启用，重启 IDEA 即可。
  另查 `Settings → Editor → General → Gutter Icons`（关了连 main 方法旁的绿色箭头都不显示）；
  仍不行 `File → Invalidate Caches → Invalidate and Restart` 重建索引。
- **列表里搜不到 Spring Boot 插件 = IDE 正运行在"免费功能集"**（相当于原社区版水平）。两种常见来源：
  ① 老版本 Community 社区版；② **IDEA 2025.3 起「统一分发」**——JetBrains 把社区版/旗舰版合并成一个安装包，
  Spring Boot 支持属于**订阅解锁层**，许可层未解锁时 IDE 会静默降级为免费功能集，而 About 里的
  "Licensed to xxx / Subscription active until …" 字样**并不代表**插件已加载（是否加载以 Plugins 列表为准）。
  这也解释了"升级 IDE 前好好的、升到 2025.3.x 就不行"——与项目代码、Maven 导入均无关。
  免费功能集照样能把项目跑起来：Maven 面板 → `shanyue-server → Plugins → spring-boot → spring-boot:run`，
  或命令行 `mvn spring-boot:run`；想要 yml 绿叶 + 启动类一键绿箭头，二选一：
  ① JetBrains Marketplace 免费安装开源插件 **Spring Explyt**（Apache-2.0，为社区版/免费功能集补齐
  Spring Boot 运行配置与配置文件支持）；
  ② 换用订阅已解锁的 IDEA（学生可申请免费授权 / 30 天试用），或退回之前图标显示正常的旧版本并关闭自动更新。

完整排查步骤见 `shanyue-server-skeleton/README.md` 第九节第 0.5 条。



1. **后端**：按 `shanyue-server-skeleton/README.md` 操作 —— JDK 17 + MySQL 8 + Redis，执行 `sql/schema.sql` 与 `sql/data.sql` 建库，以 Maven 聚合工程方式启动，服务端口 `8080`。（v2 新增功能的后端代码已按需求移除，练手实现见 `shanyue-server-skeleton/docs/新增功能教学文档.md` 自实现教程，建表 DDL 就在各功能节里；评价墙功能已整体移除。）
2. **管理端（免环境直测，推荐）**：无需 Node/nginx 环境，双击 `shanyue-admin-nginx/start.bat`，浏览器打开 `http://localhost` 即可测试（详见 `shanyue-admin-nginx/README.md`；Linux/WSL 用 `start.sh`）。
3. **管理端前端（源码开发模式）**：`cd shanyue-vue3/admin && npm install && npm run dev`
4. **H5 顾客端**：`cd shanyue-vue3/h5 && npm install && npm run dev`
5. **微信小程序**：微信开发者工具导入 `shanyue-mp/` 目录并填写自己的 AppID；联调时把 `shanyue-mp/utils/config.js` 的 `BASE_URL` 指向后端地址。

## 配置说明

- 后端配置位于 `shanyue-server-skeleton/shanyue-server/src/main/resources/`：
  - `application.yml`：端口与 profile，无需改动；
  - `application-dev.yml`：数据库账号密码、JWT 密钥、微信小程序 appid / secret —— **均为占位符，请替换为自己的配置后再运行**。
- 完整接口契约文档见 `shanyue-vue3/docs/api-docs.md`。
