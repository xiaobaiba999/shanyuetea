# 山月茶咖 · 分阶段开发指引（后端改造版）

> 读者：已跟着课程手敲完苍穹外卖 Day1~Day8（会 Spring Boot + MyBatis 写 CRUD、Redis 入门）的你。
> 用法：这不是"抄作业手册"，而是学长带你做改造的**任务卡**。每个阶段讲清楚「为什么改 → 改什么 → 怎么改 → 留给你动手的部分 → 怎么验收 → 面试怎么讲」。
> 铁律：**代码必须自己敲**。文档里的代码片段是"讲解用的关键片段"，凡是标了 ✍️ 的都是留给你完成的。整个项目要经得起查重和面试追问。

---

## 文档地图（v2.0 目录）

| 板块 | 内容 |
|---|---|
| 开始之前 | 改造总览 + 全局约定 + 总工期 |
| 阶段 0~8 | 工程改名跑通 → 数据库 → 管理端三件套/饮品规格/订单 → C 端菜单购物车/下单取餐号 → 缓存与实时提醒 → 统计收尾 |
| **阶段 6.5（v2.0 新增）** | **C 端微信登录与地址簿**（user 表 + JWT user 版拦截器 + BaseContext + jscode2session/mock + AddressBook 7 接口） |
| 扩展任务卡 | T9 优惠券与会员 ｜ T10 积分体系 ｜ T11 评价体系 ｜ **T12 月售动态统计（v2.0 新增）** |
| 附录 A | 前端联调总核对清单（v2.0 · 81 个接口 × 对应页面） |
| 附录 B | 常见联调坑 12 条 |
| **附录 C（v2.0 新增）** | **nginx 部署与前后端联调**（Windows 手把手 + 小程序 baseUrl + 排错 10 条） |
| 附录 D | 简历项目描述模板（v2.0 起由原附录 C 顺延，内容未动） |

配套文档（同目录 `docs/`）：**`framework-guide.md`——后端基础框架精讲手册**（骨架工程逐文件精讲、两条登录链路时序、12 大坑自检，基础薄弱先读它）；**`api-docs.md` v2.0——接口契约唯一事实来源**。

---

## 开始之前：五分钟看懂这次改造

### 你有什么

苍穹外卖工程（三模块 Maven）：

```
sky-take-out
├── sky-common      # 工具/常量/异常/Result/PageResult/JwtUtil/BaseContext
├── sky-pojo        # entity(11) / dto(21) / vo(17)
└── sky-server      # controller(admin 9 + user 8) / service / mapper / config / aspect / websocket / task
```

你已经掌握的、且这次**原样复用**的东西：

| 苍穹里的东西 | 位置 | 这次改造 |
|---|---|---|
| Result / PageResult | sky-common/result | 原样复用 |
| JWT 双端拦截器 | sky-server/interceptor | **只留 admin 端**，C 端删掉登录 |
| AutoFill 公共字段填充 | anno + aspect | 原样复用（注意切点包名要跟着改） |
| PageHelper 分页 | 全项目 | 原样复用 |
| RedisTemplate 配置 | config/RedisConfiguration | 原样复用，取餐号/缓存要用 |
| WebSocketServer | websocket/WebSocketServer | 原样复用，来单/催单/叫号要用 |
| OrderTask 定时任务 | task/OrderTask | 思路复用，改造成超时取消 |
| 全局异常处理器 | handler/GlobalExceptionHandler | 原样复用 |
| Employee/Category/Shop 控制器 | controller/admin | 改名 + 微调 |

### 这次要改成什么

「山月茶咖」：**茶饮自取点单**系统。业务上砍掉了"外卖配送"，所以：

| 变化 | 原因 |
|---|---|
| 菜品 Drink → **饮品 Drink** | 换业务名词，结构大改 |
| 口味 drink_flavor（字符串）→ **规格组 spec_group / spec_option（两张结构化表）** | 茶饮的杯型/温度/糖度/浓度要参与**算价**，字符串存不了 |
| 微信支付/地址簿/配送 → **删掉** | 自取模式：下单即完成支付语义，扫码自取 |
| 订单状态 6 个 → **5 个**（1待接单/2制作中/3待取餐/4已完成/5已取消） | 没有配送环节 |
| 用户微信登录 → **C 端匿名** | 自取点单不需要账号，取餐号就是凭证 |
| 新增 **取餐号**（按天重置 A01/A02…） | 叫号取餐，用 Redis INCR 实现 |
| 阿里云 OSS → **本地磁盘存储** | 不依赖云账号，本地跑通 |

> **v2.0 注**：C 端已改为微信登录模式，见阶段 6.5——上表「C 端匿名」为 v1.0~v1.6 的历史决策，保留在此供对照；v2.0 起 user 表 + JWT user 版拦截器回归（取餐号等设计不变）。

接口的唯一事实来源是 `docs/api-docs.md`（由 api-spec 生成），**联调时一切以前端实际发出的请求为准**，本文档所有路径/字段都与之对齐。

### 全局约定（先把这几条贴在显示器边上）

1. **统一响应结构**：`Result{ code, msg, data }`，code=1 成功、0 失败 —— 你工程里的 `Result` 类原样能用。
2. **分页响应**：`data = { total, records }` —— `PageResult` 原样能用。
3. **价格单位一律用"分"（int）**：`1590` = 15.90 元。这是和苍穹最大的差别之一，原因见阶段 1。
4. **时间格式**：`yyyy-MM-dd HH:mm:ss` 字符串 —— 苍穹的 `JacksonObjectMapper` 默认是 `yyyy-MM-dd HH:mm`，**要改**（阶段 0）。
5. **管理端**：除登录外所有 `/admin/**` 请求头带 `token: <jwt>`（苍穹已如此）。
6. **C 端**：`/user/**` 全部免登录。
   > **v2.0 注**：C 端已改为微信登录模式，见阶段 6.5——除 `/user/user/login`、`/user/shop/status` 外，`/user/**` 一律带请求头 `token`（JWT user 版），先登录后下单。
7. 订单状态：`1 待接单 / 2 制作中 / 3 待取餐 / 4 已完成 / 5 已取消`。
8. **（v2.0）微信登录先登录后下单**：小程序 `wx.login()` 换 code → `POST /user/user/login` 换 token → 存本地 storage → 之后所有请求带请求头 `token`；服务端一律从 ThreadLocal（BaseContext）取 userId，不信任前端传参（防越权）。
9. **（v2.0）双模式订单**：下单带 `deliveryMode`——`1` 门店自取（生成取餐号 A01，Redis 按天自增）/ `2` 外送（必填 addressBookId，收货信息快照进订单，配送费 5 元）；支付走两步 `PUT /user/order/payment` + `PUT /user/order/paySuccess`（小程序契约）。
10. **（v2.0）月售动态**：C 端饮品列表的 `monthSales` 是服务端动态统计字段（已完成订单 SUM 聚合），不是写死的数字，见扩展任务卡 T12。

### 两份配套说明

- **《framework-guide.md》（后端基础框架精讲手册）**：骨架工程逐文件精讲 + 管理端登录/小程序微信登录两条链路时序 + 新手 12 大坑。写业务卡住时先翻它，再回来做任务卡。
- **《api-docs.md》v2.0**：接口契约唯一事实来源（v2.0 新增微信登录/资料/地址簿 9 个接口，C 端订单改双模式与两步支付口径），联调争议一律以它为准。

### 总工期参考

| 阶段 | 内容 | 参考工期 |
|---|---|---|
| 0 | 工程改名与跑通 | 0.5 天 |
| 1 | 数据库改造 | 1~2 天 |
| 2 | 管理端基础三件套（员工/分类/店铺） | 2~3 天 |
| 3 | 管理端饮品 + 规格组（**核心难点**） | 3~4 天 |
| 4 | 管理端订单管理 | 2~3 天 |
| 5 | C 端菜单 + 购物车 | 2~3 天 |
| 6 | C 端下单 + 取餐号 | 2~3 天 |
| 6.5 | **C 端微信登录 + 地址簿（v2.0 新增）** | 2~3 天 |
| 7 | 缓存 + WebSocket + 超时取消（**面试亮点**） | 3~4 天 |
| 8 | 数据统计 + 部署收尾 | 2 天 |

每个阶段结束都要求**用已交付的前端联调验证**，不要闷头写完 8 个阶段再联调——那等于把所有 bug 攒到最后一周。

---

# 阶段 0：工程改名与跑通（半天）

## 为什么改

项目要写进简历、放上 GitHub。满屏的 `com.sky`、`sky_take_out` 会让人一眼看出"课程项目"；而且数据库里躺着外卖的表结构，后面每一步都会被它干扰。改名半天，越晚改成本越高。

## 改什么

### 1. 工程与模块改名（IDEA 操作）

目标：`sky-take-out` → `shanyue-tea`，模块 `sky-common/sky-pojo/sky-server` → `shanyue-common/shanyue-pojo/shanyue-server`，包 `com.sky` → `com.shanyue`。

推荐顺序（少踩坑）：

1. **包名先行**：在 IDEA 里对 `src/main/java/com/sky` 目录按 `Shift+F6`（Refactor → Rename → Rename Package），三个模块各做一次。IDEA 会自动改所有 `import` 和全限定名。
2. **目录/模块名**：`File → Project Structure → Modules` 里改模块名；再改磁盘文件夹名 + 三个模块 `pom.xml` 的 `artifactId`，以及父 pom 的 `<modules>` 列表和 `sky-common`/`sky-pojo` 的依赖坐标（`com.sky` → `com.shanyue`，`sky-common` → `shanyue-common`）。
3. **启动类**：`SkyApplication` → `ShanyueTeaApplication`。

改完后**手动全局搜索**（Ctrl+Shift+F）这几处字符串，IDEA 重构容易漏：

- [ ] `AutoFillAspect` 切点表达式：`@Pointcut("execution(* com.sky.mapper.*.*(..)) ...")` —— 一定要变成 `com.shanyue.mapper`，否则公共字段填充**静默失效**（不报错、字段全是 null，很难查）
- [ ] `application.yml`：`mybatis.type-aliases-package: com.sky.entity` → `com.shanyue.entity`
- [ ] `application.yml`：`logging.level.com.sky` → `com.shanyue`
- [ ] `WebMvcConfiguration` 里 OpenAPI 的 title「苍穹外卖项目接口文档」→「山月茶咖项目接口文档」

### 2. yml 改动清单

`application.yml` / `application-dev.yml` 改成下面这样（只保留需要的）：

```yaml
# application-dev.yml
sky:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database: shanyue_tea        # ← 改库名
    username: root
    password: 你的密码
  redis:
    host: localhost
    port: 6379
    database: 1

shanyue:
  upload:
    path: D:/upload              # Windows；Mac/Linux 写 /tmp/upload 或 ~/upload
```

**必须删掉的配置**（`application.yml` 和 `application-dev.yml` 两边都要删干净，包括 `${sky.xxx}` 占位引用）：

- `sky.alioss` —— ⚠️ **安全提醒**：你原工程 dev 配置里是**真实的阿里云 AK/SK**，已随代码存在本地多时。请立即去阿里云控制台**吊销/禁用**这对密钥，再删配置。
- `sky.wechat`、`sky.baidu`、`sky.shop.address`（自取模式用不到）

对应地，删除或不管这些类（推荐直接删，免得 bean 装配报错）：

- `config/OssConfiguration.java`、`properties/AliOssProperties.java`、`utils/AliOssUtil.java`
- `WeChatPayUtil` / `WeChatProperties` / 百度地图相关：**文件可以保留**（不影响启动），但 `OrderServiceImpl.payment/paySuccess` 里对微信支付的调用阶段 6 会重写，先不动

### 3. 保留哪些、删哪些（本阶段只做"圈定"，不强求删文件）

| 内容 | 处理 |
|---|---|
| Setmeal 套餐全套（entity/controller/service/mapper） | **文件保留、不开发、不联调**。简历上被问"为什么有 Setmeal"就答：从苍穹迁移时保留了文件，本产品无套餐业务，后续可扩展"咖啡+甜品组合"。阶段 0 不必删除，避免大动干戈 |
| user 端微信登录（UserController、JwtTokenUserInterceptor） | `WebMvcConfiguration` 里**删掉 user 拦截器注册**（C 端匿名，见下），登录类文件保留不调用。**v2.0 注：C 端已改为微信登录模式，见阶段 6.5——该行做法已被取代，user 拦截器恢复注册（骨架 shanyue-server-skeleton 即 v2.0 接线范本）** |
| AddressBook 地址簿 | 保留文件不开发 |
| 微信支付 | 保留文件不调用 |

`WebMvcConfiguration.addInterceptors` 改完应该是：

```java
// shanyue-server/src/main/java/com/shanyue/config/WebMvcConfiguration.java
public void addInterceptors(InterceptorRegistry registry) {
    log.info("开始注册自定义拦截器...");
    registry.addInterceptor(jwtTokenAdminInterceptor)
            .addPathPatterns("/admin/**")
            .excludePathPatterns("/admin/employee/login");
    // C 端匿名自取，/user/** 不再注册任何拦截器
}
```

> **v2.0 注**：C 端已改为微信登录模式，见阶段 6.5——`/user/**` 重新注册 `JwtTokenUserInterceptor`（放行 `/user/user/login` 与 `/user/shop/status`），骨架 `shanyue-server-skeleton` 的 `WebMvcConfiguration` 即 v2.0 接线范本。

### 4. OSS → 本地磁盘存储（完整方案）

**为什么**：OSS 要云账号，图片上传是你马上要用的功能（饮品图）。本地磁盘 + 静态资源映射半小时搞定，原理还和 OSS 一致：存文件 → 返回可访问 URL。

三步：

**① 上传接口改写**（原来是 `AliOssUtil.upload`，现在写磁盘）：

```java
// shanyue-server/src/main/java/com/shanyue/controller/admin/CommonController.java
@RestController
@RequestMapping("/admin/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

    @Value("${shanyue.upload.path}")
    private String uploadPath;   // D:/upload 或 /tmp/upload

    @PostMapping("/upload")
    @Operation(summary = "文件上传")
    public Result upload(MultipartFile file) {
        log.info("文件上传：{}", file.getOriginalFilename());

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID() + extension;   // UUID 重命名，防重名覆盖

        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败");
        }
        // 注意：data 里返回 { url: ... } 对象，不是裸字符串（前端按 res.data.url 取值）
        String url = "http://localhost:8080/upload/" + fileName;
        return Result.success(Map.of("url", url));
    }
}
```

> ⚠️ 苍穹这里 `Result.success(filePath)` 返回的是**裸字符串**，接口文档约定返回 `{ "url": ... }` 对象，别照抄旧代码。

**② 静态资源映射**（让 `/upload/xxx.png` 能被浏览器访问到磁盘文件）：

```java
// shanyue-server/src/main/java/com/shanyue/config/WebMvcConfiguration.java
@Value("${shanyue.upload.path}")
private String uploadPath;

@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 注意 file: 后的路径必须以 / 结尾，否则拼 URL 会丢目录
    registry.addResourceHandler("/upload/**")
            .addResourceLocations("file:" + uploadPath + "/");
}
```

**③ 请求大小**：`application.yml` 里 `spring.servlet.multipart.max-file-size: 5MB` 苍穹已配，保留即可。

### 5. 时间序列化格式（小改，不然后面天天烦）

```java
// shanyue-common/src/main/java/com/shanyue/json/JacksonObjectMapper.java
public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";  // 原来是 yyyy-MM-dd HH:mm
```

不改的后果：前端表格里订单时间是 `2025-09-22 10:30`，接口文档要求带秒；前端做时间范围筛选、排序对比时都可能错位。

### 6. 先建一张最小的表保证"跑通"

阶段 1 才给全量 DDL，这里为了让工程能启动、能登录，先跑两句 SQL（后面会被阶段 1 的完整脚本覆盖）：

```sql
CREATE DATABASE IF NOT EXISTS shanyue_tea DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shanyue_tea;
-- 最小员工表（阶段1会给完整版，一致）
CREATE TABLE employee (
  id BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  name VARCHAR(32) NOT NULL COMMENT '姓名',
  username VARCHAR(32) NOT NULL COMMENT '用户名',
  password VARCHAR(64) NOT NULL COMMENT '密码(MD5)',
  phone VARCHAR(11) DEFAULT '' COMMENT '手机号',
  sex VARCHAR(2) DEFAULT '1' COMMENT '性别 1男 2女',
  id_number VARCHAR(18) DEFAULT '' COMMENT '身份证号',
  status INT DEFAULT 1 COMMENT '状态 0禁用 1启用',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '修改时间',
  create_user BIGINT COMMENT '创建人',
  update_user BIGINT COMMENT '修改人'
) COMMENT '员工表';
INSERT INTO employee (name, username, password, status, create_time, update_time)
VALUES ('店长', 'admin', MD5('123456'), 1, NOW(), NOW());
```

## ✍️ 动手任务清单

- [ ] 包名 `com.sky` → `com.shanyue` 全局重构，工程编译通过（`mvn compile`）
- [ ] 模块/工程/artifactId 改名，父 pom `<modules>` 与依赖坐标同步
- [ ] 检查 AutoFillAspect 切点、application.yml 的 type-aliases-package、logging 三处包名
- [ ] 删除 alioss/wechat/baidu 配置（application.yml 占位引用 + dev 配置），删除 Oss 三件套类
- [ ] **吊销泄露的阿里云 AK/SK**（重要，写进你的提交记录）
- [ ] `CommonController` 本地上传改造 + `addResourceHandlers` 静态映射，手工放一张图到上传目录验证 URL 可访问
- [ ] `JacksonObjectMapper` 时间格式改为 `yyyy-MM-dd HH:mm:ss`
- [ ] 删除 user 拦截器注册；OpenAPI 标题改「山月茶咖」
- [ ] 建 `shanyue_tea` 库 + employee 最小表 + admin 账号，应用启动成功

## ✅ 验收标准

1. `mvn compile` 无错，`ShanyueTeaApplication` 能启动。
2. 浏览器打开 `http://localhost:8080/swagger-ui.html`，分组是「管理端接口 / 用户端接口」，标题是山月茶咖。
3. 用 Postman/curl 登录：`POST /admin/employee/login` body `{"username":"admin","password":"123456"}`，返回 `{code:1, data:{...token...}}`。
4. admin 前端 mock 演示数据默认已关闭（v1.5.2 起），无需任何设置；如果你在 `.env` 里设过 `VITE_USE_MOCK=true` 请删掉。

## 💡 面试讲点

- **"你的项目是从苍穹外卖改的，怎么体现你自己的工作？"** —— 讲清改造决策链：外卖→自取，所以砍掉配送状态机/地址/微信支付，订单状态从 6 个收敛到 5 个；口味字符串升级为结构化规格表以支撑动态算价；这些是业务建模能力，不是换皮。
- **"文件上传你怎么做的？和 OSS 的区别？"** —— 本地磁盘 + `addResourceHandlers` 静态映射；OSS 本质是把"存文件 + 静态域名访问"外包给云服务，换 URL 拼接方式而已。可再补一句生产上为什么用 OSS（容量、CDN、备份）。
- **"AK/SK 硬编码在配置里有什么风险？"** —— 密钥泄露即资源被盗用；应放环境变量/配置中心，泄露后立即轮换。你可以真实讲出"我排查到了已泄露的 AK 并吊销了它"。

---

# 阶段 1：数据库改造（1~2 天）

## 为什么改

外卖库的 drink/drink_flavor/orders 承载不了茶饮业务：口味是自由字符串没法算价；订单背着 20 个配送/支付字段。这一步建好新库 `shanyue_tea`，后面所有代码都对着它写。

## 改什么：完整 DDL

新建一个 `sql/shanyue_tea.sql` 放到工程根目录（以后放进 README，作为交付物）。下面是**完整可执行**脚本（MySQL 8，utf8mb4）：

```sql
CREATE DATABASE IF NOT EXISTS shanyue_tea DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shanyue_tea;

-- ============ 1. 员工表：沿用苍穹，原样重建 ============
CREATE TABLE employee (
  id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  name        VARCHAR(32) NOT NULL COMMENT '姓名',
  username    VARCHAR(32) NOT NULL COMMENT '用户名',
  password    VARCHAR(64) NOT NULL COMMENT '密码(MD5)',
  phone       VARCHAR(11)  DEFAULT '' COMMENT '手机号',
  sex         VARCHAR(2)   DEFAULT '1' COMMENT '性别 1男 2女',
  id_number   VARCHAR(18)  DEFAULT '' COMMENT '身份证号',
  status      INT          DEFAULT 1 COMMENT '状态 0禁用 1启用',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '修改时间',
  create_user BIGINT COMMENT '创建人',
  update_user BIGINT COMMENT '修改人',
  UNIQUE KEY uk_username (username)
) COMMENT '员工表';

-- ============ 2. 饮品分类：去掉 type（只有一种分类），新增 icon（C端 emoji 图标） ============
CREATE TABLE category (
  id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  name        VARCHAR(32) NOT NULL COMMENT '分类名称',
  icon        VARCHAR(8)  DEFAULT '' COMMENT 'C端分类图标(emoji),如 ⭐ ☕ 🍵',
  sort        INT         DEFAULT 0 COMMENT '排序',
  status      INT         DEFAULT 1 COMMENT '状态 0禁用 1启用',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '修改时间',
  create_user BIGINT COMMENT '创建人',
  update_user BIGINT COMMENT '修改人'
) COMMENT '饮品分类';

-- ============ 3. 饮品表：drink 改造而来 ============
-- 关键差异：price 用 INT 存"分"；新增 featured(招牌)、sold(销量)；去掉苍穹的口味关联
CREATE TABLE drink (
  id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  name        VARCHAR(64) NOT NULL COMMENT '饮品名称',
  category_id BIGINT NOT NULL COMMENT '分类id',
  price       INT NOT NULL DEFAULT 0 COMMENT '基础价(单位:分,如1590=15.90元)',
  image       VARCHAR(255) DEFAULT '' COMMENT '图片URL',
  description VARCHAR(255) DEFAULT '' COMMENT '描述',
  featured    TINYINT DEFAULT 0 COMMENT '是否招牌 0否 1是',
  sold        INT DEFAULT 0 COMMENT '累计销量',
  status      TINYINT DEFAULT 0 COMMENT '状态 0停售 1起售(新增默认停售,与苍穹一致)',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '修改时间',
  create_user BIGINT COMMENT '创建人',
  update_user BIGINT COMMENT '修改人',
  KEY idx_category (category_id)
) COMMENT '饮品';

-- ============ 4. 规格组：杯型/温度/糖度/浓度，一行一组 ============
CREATE TABLE spec_group (
  id       BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  drink_id BIGINT NOT NULL COMMENT '所属饮品id',
  name     VARCHAR(32) NOT NULL COMMENT '规格组名(杯型/温度/糖度/浓度)',
  required TINYINT DEFAULT 1 COMMENT '是否必选 0否 1是',
  sort     INT DEFAULT 0 COMMENT '排序(同时决定 specKey 的拼接顺序)',
  KEY idx_drink (drink_id)
) COMMENT '规格组';

-- ============ 5. 规格选项：一行一选项，price_delta 参与算价 ============
CREATE TABLE spec_option (
  id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  group_id    BIGINT NOT NULL COMMENT '所属规格组id',
  drink_id    BIGINT NOT NULL COMMENT '所属饮品id(冗余字段,便于先删后插与级联删除)',
  name        VARCHAR(32) NOT NULL COMMENT '选项名(中杯/大杯/冰/热...)',
  price_delta INT DEFAULT 0 COMMENT '加价(单位:分,0表示不加价)',
  sort        INT DEFAULT 0 COMMENT '组内排序',
  KEY idx_group (group_id),
  KEY idx_drink (drink_id)
) COMMENT '规格选项';

-- ============ 6. 购物车：匿名自取版 ============
-- v2.0 注：C 端已改为微信登录模式，见阶段 6.5——user_id 不再为 NULL，一律由后端从 ThreadLocal 取当前登录用户回填；列结构不变。
CREATE TABLE shopping_cart (
  id         BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  user_id    BIGINT DEFAULT NULL COMMENT '用户id(本期匿名自取预留,不填)',
  drink_id   BIGINT NOT NULL COMMENT '饮品id',
  drink_name VARCHAR(64) NOT NULL COMMENT '饮品名(快照)',
  image      VARCHAR(255) COMMENT '图片(快照)',
  spec_key   VARCHAR(100) DEFAULT '' COMMENT '规格key,形如 1-12,2-31,3-45 (groupId-optionId按组sort排序,逗号分隔,服务端生成)',
  spec_text  VARCHAR(255) DEFAULT '' COMMENT '规格文案,形如 大杯/冰/五分糖',
  price      INT NOT NULL DEFAULT 0 COMMENT '单价=基础价+Σprice_delta(分,服务端计算)',
  number     INT NOT NULL DEFAULT 1 COMMENT '数量',
  amount     INT NOT NULL DEFAULT 0 COMMENT '小计=price*number(分)',
  create_time DATETIME COMMENT '创建时间',
  UNIQUE KEY uk_drink_spec (drink_id, spec_key)
) COMMENT '购物车';

-- ============ 7. 订单：自取模式精简版 ============
CREATE TABLE orders (
  id                     BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  order_number           VARCHAR(50) NOT NULL COMMENT '订单号 yyyyMMdd+3位当日序号',
  pick_up_code           VARCHAR(8)  NOT NULL COMMENT '取餐号 A01,按天重置',
  status                 TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1待接单 2制作中 3待取餐 4已完成 5已取消',
  amount                 INT NOT NULL DEFAULT 0 COMMENT '订单总额(分)',
  remark                 VARCHAR(255) COMMENT '顾客备注',
  phone                  VARCHAR(11) COMMENT '取餐联系手机号(可选)',
  estimated_wait_minutes INT DEFAULT 10 COMMENT '预计等待分钟数',
  order_time             DATETIME COMMENT '下单时间',
  checkout_time          DATETIME COMMENT '结账时间(自取模式=下单时间)',
  cancel_reason          VARCHAR(255) COMMENT '取消原因(商家取消/用户取消/超时取消)',
  cancel_time            DATETIME COMMENT '取消时间',
  user_id                BIGINT DEFAULT NULL COMMENT '预留:接入登录后使用',
  KEY idx_status (status),
  KEY idx_pickup (pick_up_code),
  KEY idx_order_time (order_time)
) COMMENT '订单(自取模式)';

-- ============ 8. 订单明细：快照式 ============
CREATE TABLE order_item (
  id         BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
  order_id   BIGINT NOT NULL COMMENT '订单id',
  drink_id   BIGINT NOT NULL COMMENT '饮品id',
  drink_name VARCHAR(64) COMMENT '饮品名(快照)',
  image      VARCHAR(255) COMMENT '图片(快照)',
  spec_key   VARCHAR(100) COMMENT '规格key',
  spec_text  VARCHAR(255) COMMENT '规格文案',
  price      INT COMMENT '单价(分,下单时快照)',
  number     INT COMMENT '数量',
  amount     INT COMMENT '小计(分)',
  KEY idx_order (order_id)
) COMMENT '订单明细';

-- ============ 9. 门店信息：单行表 ============
CREATE TABLE shop (
  id         BIGINT PRIMARY KEY COMMENT '固定为1',
  name       VARCHAR(64) COMMENT '门店名称',
  status     TINYINT DEFAULT 1 COMMENT '营业状态 1营业中 0打烊',
  notice     VARCHAR(255) COMMENT '门店公告',
  open_hours VARCHAR(32) COMMENT '营业时间,如 09:00-22:00'
) COMMENT '门店信息(单行表,id=1)';
```

### 设计说明（面试会被逐条追问，先想清楚）

**① 为什么 price 用 INT 存"分"，不用苍穹的 BigDecimal？**

- 前后端、数据库**三处单位统一**，接口文档约定就是分；BigDecimal 序列化到 JSON 是 `15.9`，前端按分处理就全乱了（这是苍穹转本项目的第一大坑）。
- 金额加减（`price + Σ price_delta`）用 int 天然精确，不存在 `0.1+0.2` 浮点问题。
- 展示时前端 `price / 100` 保留两位即可。

**② orders 表砍掉了什么？** `address_book_id/consignee/address/delivery_status/estimated_delivery_time/delivery_time/pack_amount/tableware_number/pay_method/pay_status/rejection_reason/user_name`。自取模式没有配送与支付环节；`cancel_reason` 保留（商家取消、用户取消、超时自动取消三种场景共用）。每砍一个字段你都要能答"为什么能砍"。

**③ spec_option 为什么冗余 drink_id？** 规范范式上 option 只依赖 group。冗余一份 drink_id 后，"先删后插"和"删饮品级联删规格"都只需一个条件 `where drink_id = ?`，不用先查 group 再 `in`。这是**以空间换简单**的反范式设计，面试可以主动讲这个取舍。

**④ 购物车为什么存快照字段（drink_name/image）？** 和苍穹一致：购物车、订单明细都是"下单那一刻的状态"，饮品后来改名/改图不影响历史记录。

### 旧 drink 数据迁移思路（不强制，但简历加分）

如果你想把苍穹库里做过的菜品带过来：

1. **drink → drink**（可自动，价格 decimal 元 → int 分）：

```sql
INSERT INTO drink (name, category_id, price, image, description, status, create_time, update_time)
SELECT name, category_id, CAST(price * 100 AS SIGNED), image, description, status, create_time, update_time
FROM sky_take_out.drink;
```

2. **drink_flavor → spec_group/spec_option**：无法自动迁移——苍穹口味是 `name="辣度", value="[不辣,微辣]"` 的字符串，规格需要拆成行并带加价。两个办法：
   - **推荐**：对照已交付的种子数据文件（13 款饮品、每款 4 组规格，见交付工程内 mock 数据源 JSON），手写 INSERT（规模很小）；
   - 进阶：写个一次性 Java main 方法或 Excel 整理后导入，可以当"数据迁移"面试素材。
3. `featured`（招牌）、`sold`（销量）迁移后按运营需要手工补。

### 最小种子数据（跑通联调用）

```sql
-- 员工（教师演示账号）
INSERT INTO employee (name, username, password, status, create_time, update_time)
VALUES ('店长', 'admin', MD5('123456'), 1, NOW(), NOW());

-- 分类（C端 8.2 会返回 icon）
INSERT INTO category (id, name, icon, sort, status, create_time, update_time) VALUES
(1, '招牌推荐', '⭐', 1, 1, NOW(), NOW()),
(2, '经典咖啡', '☕', 2, 1, NOW(), NOW()),
(3, '鲜煮茶饮', '🍵', 3, 1, NOW(), NOW()),
(4, '季节限定', '🧋', 4, 1, NOW(), NOW()),
(5, '烘焙小食', '🥐', 5, 1, NOW(), NOW());

-- 门店
INSERT INTO shop (id, name, status, notice, open_hours)
VALUES (1, '山月茶咖·科创园区店', 1, '今日桂花限定买一送一，欢迎品尝', '09:00-22:00');

-- 三个饮品（新增后手动置为起售，方便马上联调）
INSERT INTO drink (id, name, category_id, price, image, description, featured, sold, status, create_time, update_time) VALUES
(1, '桂花拿铁', 1, 1890, '/menu/osmanthus-latte.png', '秋日限定，桂花香与醇厚奶咖', 1, 156, 1, NOW(), NOW()),
(2, '生椰拿铁', 2, 1590, '/menu/coconut-latte.png',   '厚椰乳与浓缩的经典组合',     1, 321, 1, NOW(), NOW()),
(3, '茉莉奶绿', 3, 1290, '/menu/jasmine-milk-tea.png','茉莉花茶底与鲜奶',           0,  98, 1, NOW(), NOW());

-- 生椰拿铁的 4 组规格（与原型数据一致）
INSERT INTO spec_group (id, drink_id, name, required, sort) VALUES
(1, 2, '杯型', 1, 1),
(2, 2, '温度', 1, 2),
(3, 2, '糖度', 1, 3),
(4, 2, '浓度', 0, 4);

INSERT INTO spec_option (group_id, drink_id, name, price_delta, sort) VALUES
(1, 2, '中杯',   0, 1), (1, 2, '大杯', 300, 2),
(2, 2, '冰',     0, 1), (2, 2, '热',   0, 2), (2, 2, '少冰', 0, 3),
(3, 2, '标准糖', 0, 1), (3, 2, '五分糖', 0, 2), (3, 2, '无糖', 0, 3),
(4, 2, '标准',   0, 1), (4, 2, '加浓', 200, 2);
```

> 图片说明：mock 数据用的是 `/menu/xxx.png` 相对路径（前端 public 目录里带了同名图片）。你后端正式上传的图会存 `http://localhost:8080/upload/uuid.png`，两种都能显示，联调时注意域名归属（见附录 B 坑 7）。

## ✍️ 动手任务清单

- [ ] 完整 DDL 在本地 MySQL 8 跑通，`desc` 每张表确认字段与注释
- [ ] 写入最小种子数据（员工/分类/门店/3 饮品/4 规格组/10 选项）
- [ ] （可选）从 sky_take_out 库迁移旧 drink 数据并手工补规格
- [ ] 把 sql 脚本纳入工程版本管理（建 `sql/` 目录）

## ✅ 验收标准

1. `use shanyue_tea; show tables;` 能看到 9 张表。
2. `SELECT * FROM drink d JOIN spec_group g ON d.id=g.drink_id JOIN spec_option o ON g.id=o.group_id WHERE d.id=2 ORDER BY g.sort, o.sort;` 能按"杯型→温度→糖度→浓度"的顺序打出 10 个选项。
3. `SELECT MD5('123456')` 结果与 employee 表中 admin 密码一致。

## 💡 面试讲点

- **"为什么金额用分不用元？"** 避免浮点误差、前后端单位统一、int 计算精确，展示时再除以 100。
- **"规格为什么拆两张表？"** 第一范式：苍穹口味把多值塞一个字符串，无法按选项算价/统计销量；结构化后 `price_delta` 可以参与服务端算价，也能做"加浓最畅销"这类统计。
- **"orders 表你怎么做减法的？"** 按新业务（自取）重新建模，能砍掉配送/支付等 10+ 字段，说明你理解字段服务于业务而不是越多越好。

---

# 阶段 2：管理端基础三件套（员工/分类/店铺，2~3 天）

## 为什么先做这三个

它们是苍穹现成代码"改名即用"的部分，用一天半把改造套路练熟（DTO/VO 对齐接口文档、拦截器联调、前端联调），再进入真正要动脑的阶段 3。

## 改什么

### 1. 员工模块：几乎零改动

对照接口文档核对即可：

- `POST /admin/employee/login` → data `{ id, name, userName, token }`：苍穹 `EmployeeLoginVO` 字段一致（注意是 `userName`，实体是 `username`，别改错）。
- 分页/新增/编辑/启用禁用 → 苍穹 `EmployeeController` 原样可用（阶段 0 已完成包名重构）。
- 默认账号 admin/123456 已在阶段 1 种子里。

### 2. 分类模块：两处小改

**① 去掉 `type` 字段**：苍穹分类用 type 区分菜品/套餐，本项目只有饮品一种。`Category` 实体删掉 `type`，新增/修改的 XML 与 service 同步删掉对 type 的处理。

**② 新增 `icon` 字段**：C 端分类列表要返回 emoji 图标。给 `Category` 实体加 `private String icon;`，对应 XML 的 insert/update 补上 `icon` 列。

**③ C 端接口顺手做掉**（很小的活）：

```java
// shanyue-server/src/main/java/com/shanyue/controller/user/CategoryController.java
@RestController("userCategoryController")   // 与 admin 包同名 Bean 冲突时指定名字，苍穹已有此写法
@RequestMapping("/user/category")
@Tag(name = "C端-分类接口")
@Slf4j
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    @GetMapping("/list")
    @Operation(summary = "查询分类列表")
    public Result<List<Category>> list() {
        // 只返回启用状态，按 sort 升序（✍️ service/mapper 自己写，参照苍穹 drink 的 selectList）
        List<Category> list = categoryService.list(1);
        return Result.success(list);
    }
}
```

**④ 删除约束**：分类下有饮品时删除要失败并提示「该分类下存在商品，无法删除」（前端会 toast 这句）。参照苍穹 `CategoryServiceImpl.delete` 里 `countDrink` 的写法，换成 `drinkMapper.countByCategoryId(categoryId)`，抛 `DeletionNotAllowedException`。

### 3. 店铺模块：从"只存 Redis"改为"表 + 公告"

苍穹的店铺状态存在 Redis（`redisTemplate.opsForValue().set(ShopStatusConstant.KEY, status)`），因为外卖场景状态读极频繁。本项目接口要求返回**店名/公告/营业时间/状态**，所以改为 shop 表为主：

```java
// shanyue-server/src/main/java/com/shanyue/controller/admin/ShopController.java
@RestController
@RequestMapping("/admin/shop")
@Tag(name = "店铺管理")
@Slf4j
public class ShopController {

    @Autowired
    private ShopService shopService;

    @GetMapping("/status")
    @Operation(summary = "获取营业状态")
    public Result<Integer> getShopStatus() {
        return Result.success(shopService.getStatus());   // data 直接是 1 或 0，不包对象
    }

    @PutMapping("/status/{status}")
    @Operation(summary = "修改营业状态")
    public Result setShopStatus(@PathVariable Integer status) {
        log.info("设置店铺营业状态：{}", status == 1 ? "营业中" : "打烊");
        shopService.setStatus(status);
        return Result.success();
    }

    @PutMapping("/notice")
    @Operation(summary = "修改公告")
    public Result setNotice(@RequestBody ShopNoticeDTO shopNoticeDTO) {
        log.info("修改店铺公告：{}", shopNoticeDTO.getNotice());
        shopService.setNotice(shopNoticeDTO.getNotice());
        return Result.success();
    }
}
```

✍️ 需要你完成的：`ShopNoticeDTO`（一个 notice 字段）、`ShopService`/`Impl`（对 shop 表 id=1 做 update；getStatus/setNotice 同理）、`ShopMapper`（注解 SQL 即可：`@Update("update shop set status=#{status} where id=1")` 风格）。

C 端配套 `GET /user/shop/status` 返回完整 `ShopVO { name, status, notice, openHours }`——查 shop 表 id=1 一行，✍️ 自己写（先别管 h5 联调，阶段 5 会用到）。

> 进阶思考（写进笔记）：苍穹把营业状态放 Redis 是"读多写少 + 抗高并发读"的取舍。你这里也可以双写（DB + Redis），C 端读 Redis——留到阶段 7 讲缓存时再回头看这个方案，想想"两处状态如何保持一致"。

### 4. 拦截器与 C 端匿名（阶段 0 已动，这里联调确认）

- admin 前端登录后每个请求带 `token` 头 → `JwtTokenAdminInterceptor` 校验 → 放行。401 说明 token 没带上或没 exclude login（见附录 B）。
- `/user/**` 无拦截器。
  > **v2.0 注**：C 端已改为微信登录模式，见阶段 6.5——`/user/**` 由 `JwtTokenUserInterceptor` 校验（放行登录与店铺状态接口），本小节其余内容为历史口径保留。

## ✍️ 动手任务清单

- [ ] Category：删 type、加 icon，insert/update XML 同步，写 C 端 `/user/category/list`
- [ ] Category：删除约束（有饮品禁删，msg「该分类下存在商品，无法删除」）
- [ ] Shop：`ShopNoticeDTO` + Service + Mapper，三个 admin 接口 + C 端 `/user/shop/status`
- [ ] Employee：跑通登录→分页→新增→编辑→禁用一遍（改名为主，有 bug 顺手修）

## ✅ 验收标准（开始用前端联调！）

1. admin 前端工程 mock 默认已关闭，直接 `npm run dev`（5173 端口）。
2. **登录页**：admin/123456 登录成功，进工作台；错误密码 toast 失败。
3. **员工页**：分页能出"店长"；新增一个员工再搜索到它；编辑手机号保存后刷新仍生效；把该员工禁用，列表状态变灰。
4. **分类页**：新增「 test 分类」出现；改名为「测试分类」；删除它成功；删除「经典咖啡」被拦截 toast「该分类下存在商品，无法删除」。
5. **店铺页/顶栏**：切换营业状态，顶栏徽标变"打烊中"，刷新页面状态保持（说明落库了）；修改公告保存后重新打开页面公告是新的。
6. F12 Network：每个请求头都有 `token`；响应都是 `{code:1,...}`。

## 💡 面试讲点

- **"JWT 拦截器怎么工作的？"** 登录签发含 empId 的 JWT → 拦截器解析放入 ThreadLocal（BaseContext）→ AutoFill 切面取它填 create_user。追问点：为什么 afterCompletion 要 remove（线程池复用脏数据）。
- **"分类删除为什么要校验？"** 引用完整性：物理外键 vs 业务校验的取舍（本项目用业务校验 + 明确报错文案，体验更好）。
- **"店铺状态为什么从 Redis 改成表？"** 数据语义变了（从单个 int 变成含公告的完整信息），且单机教学项目并发读不高；同时你能讲出苍穹放 Redis 的原因和双写一致性问题。

---

# 阶段 3：管理端饮品 + 规格组（3~4 天，全项目核心难点）

## 为什么这是核心难点

这一步你在苍穹 Drink 的基础上做**结构升级**：一对一的口味字符串 → 一对多的规格组、组里再套多对多的选项，且选项带加价。难点不在 CRUD，在：**DTO 嵌套设计、一次请求落三张表的事务、修改时的整体覆盖策略**。这个写明白了，项目 80% 的技术含量就有了。

## 改什么

### 1. 实体与 DTO/VO（sky-pojo 模块）

```java
// shanyue-pojo/src/main/java/com/shanyue/entity/Drink.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Drink implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;        // 饮品名称
    private Long categoryId;    // 分类id
    private Integer price;      // 基础价(分) —— 注意是 Integer，不是 BigDecimal
    private String image;
    private String description;
    private Integer featured;   // 0否 1是 招牌
    private Integer sold;       // 销量
    private Integer status;     // 0停售 1起售
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
```

```java
// shanyue-pojo/src/main/java/com/shanyue/entity/SpecGroup.java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SpecGroup implements Serializable {
    private Long id;
    private Long drinkId;
    private String name;     // 杯型/温度/糖度/浓度
    private Integer required; // 1必选 0可选
    private Integer sort;
}
// shanyue-pojo/src/main/java/com/shanyue/entity/SpecOption.java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SpecOption implements Serializable {
    private Long id;
    private Long groupId;
    private Long drinkId;    // 冗余
    private String name;     // 中杯/大杯...
    private Integer priceDelta; // 加价(分)
    private Integer sort;
}
```

嵌套 DTO（前端提交"饮品 + 规格组 + 选项"一整棵树）：

```java
// shanyue-pojo/src/main/java/com/shanyue/dto/DrinkDTO.java
@Data
public class DrinkDTO implements Serializable {
    private Long id;            // 编辑时必传
    private String name;
    private Long categoryId;
    private Integer price;
    private String image;
    private String description;
    private Integer featured;
    private List<SpecGroupDTO> specGroups;   // 整棵规格树
}

// shanyue-pojo/src/main/java/com/shanyue/dto/SpecGroupDTO.java
@Data
public class SpecGroupDTO implements Serializable {
    private Long id;
    private String name;
    private Integer required;
    private Integer sort;
    private List<SpecOptionDTO> specOptions;
}

// shanyue-pojo/src/main/java/com/shanyue/dto/SpecOptionDTO.java
@Data
public class SpecOptionDTO implements Serializable {
    private Long id;
    private String name;
    private Integer priceDelta;
    private Integer sort;
}
```

VO（查询回显）：`DrinkVO extends Drink` 加 `categoryName` 与 `List<SpecGroupVO> specGroups`；`SpecGroupVO extends SpecGroup` 加 `List<SpecOption> specOptions`（选项直接用实体即可）。✍️ 自己建这四个类。

### 2. DrinkController（6 个接口骨架）

```java
// shanyue-server/src/main/java/com/shanyue/controller/admin/DrinkController.java
@RestController
@RequestMapping("/admin/drink")
@Tag(name = "饮品接口")
@Slf4j
public class DrinkController {

    @Autowired
    private DrinkService drinkService;

    @PostMapping
    @Operation(summary = "新增饮品")
    public Result save(@RequestBody DrinkDTO drinkDTO) {
        log.info("新增饮品：{}", drinkDTO);
        drinkService.saveWithSpec(drinkDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @Operation(summary = "饮品分页查询")
    public Result<PageResult> page(DrinkPageQueryDTO drinkPageQueryDTO) {
        log.info("饮品分页查询：{}", drinkPageQueryDTO);
        return Result.success(drinkService.pageQuery(drinkPageQueryDTO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询饮品(含规格组)")
    public Result<DrinkVO> getById(@PathVariable Long id) {
        return Result.success(drinkService.getByIdWithSpec(id));
    }

    @PutMapping
    @Operation(summary = "修改饮品")
    public Result update(@RequestBody DrinkDTO drinkDTO) {
        log.info("修改饮品：{}", drinkDTO);
        drinkService.updateWithSpec(drinkDTO);
        return Result.success();
    }

    @DeleteMapping
    @Operation(summary = "批量删除饮品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除饮品：{}", ids);
        drinkService.deleteBatch(ids);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "起售/停售")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("起售/停售：status={}, id={}", status, id);
        drinkService.startOrStop(status, id);
        return Result.success();
    }
}
```

`DrinkPageQueryDTO`：`name, categoryId, status, page, pageSize`，照抄苍穹 `DrinkPageQueryDTO` 改名。

### 3. 核心：新增/修改的"先删后插"整体覆盖（重点理解）

```java
// shanyue-server/src/main/java/com/shanyue/service/impl/DrinkServiceImpl.java
@Service
public class DrinkServiceImpl implements DrinkService {

    @Autowired private DrinkMapper drinkMapper;
    @Autowired private SpecGroupMapper specGroupMapper;
    @Autowired private SpecOptionMapper specOptionMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private ShoppingCartMapper shoppingCartMapper;

    @Transactional
    public void saveWithSpec(DrinkDTO drinkDTO) {
        Drink drink = new Drink();
        BeanUtils.copyProperties(drinkDTO, drink);
        drink.setStatus(0);                       // 新增默认停售（与苍穹一致），再手动起售
        drink.setSold(0);
        drinkMapper.insert(drink);                // useGeneratedKeys 回填 id
        saveSpecTree(drink.getId(), drinkDTO.getSpecGroups());
    }

    @Transactional
    public void updateWithSpec(DrinkDTO drinkDTO) {
        Drink drink = new Drink();
        BeanUtils.copyProperties(drinkDTO, drink);
        drinkMapper.update(drink);                // 动态 set，参照苍穹 DrinkMapper.update
        // 先删旧树，再插新树（整体覆盖）
        specOptionMapper.deleteByDrinkId(drinkDTO.getId());
        specGroupMapper.deleteByDrinkId(drinkDTO.getId());
        saveSpecTree(drinkDTO.getId(), drinkDTO.getSpecGroups());
    }

    /** 把 DTO 规格树落两张表 */
    private void saveSpecTree(Long drinkId, List<SpecGroupDTO> groups) {
        if (groups == null || groups.isEmpty()) return;
        List<SpecGroup> groupList = new ArrayList<>();
        for (SpecGroupDTO g : groups) {
            SpecGroup entity = new SpecGroup();
            BeanUtils.copyProperties(g, entity);
            entity.setDrinkId(drinkId);
            groupList.add(entity);
        }
        specGroupMapper.insertBatch(groupList);   // useGeneratedKeys 批量回填组 id
        List<SpecOption> optionList = new ArrayList<>();
        for (SpecGroup entity : groupList) {
            // 从 DTO 树里找到同一组的选项（按位置对应）
            SpecGroupDTO dto = groups.get(groupList.indexOf(entity));
            for (SpecOptionDTO o : dto.getSpecOptions()) {
                SpecOption option = new SpecOption();
                BeanUtils.copyProperties(o, option);
                option.setGroupId(entity.getId());
                option.setDrinkId(drinkId);
                optionList.add(option);
            }
        }
        if (!optionList.isEmpty()) {
            specOptionMapper.insertBatch(optionList);
        }
    }
}
```

批量插入 XML（对照苍穹 `DrinkFlavorsMapper.xml` 的 `<foreach>` 写法）：

```xml
<!-- shanyue-server/src/main/resources/mapper/SpecGroupMapper.xml -->
<insert id="insertBatch" useGeneratedKeys="true" keyProperty="id">
    insert into spec_group (drink_id, name, required, sort)
    values
    <foreach collection="list" item="g" separator=",">
        (#{g.drinkId}, #{g.name}, #{g.required}, #{g.sort})
    </foreach>
</insert>

<!-- shanyue-server/src/main/resources/mapper/SpecOptionMapper.xml -->
<insert id="insertBatch">
    insert into spec_option (group_id, drink_id, name, price_delta, sort)
    values
    <foreach collection="list" item="o" separator=",">
        (#{o.groupId}, #{o.drinkId}, #{o.name}, #{o.priceDelta}, #{o.sort})
    </foreach>
</insert>

<delete id="deleteByDrinkId">
    delete from spec_option where drink_id = #{drinkId}
</delete>
```

**为什么"先删后插"，不做 diff 更新？（必背）**

- 前端编辑页每次提交的是**整棵树**（组可能增删、组内选项也可能增删、还可能换序）。做 diff 要比较"哪些组是新增/哪些被删/哪些 id 保留但内容变了"，三态逻辑复杂且容易漏。
- 规格树数据量极小（一个饮品最多几十行），全删重插的成本可忽略。
- 代价：option 的 id 会变化。所以购物车/订单存的 `spec_text`（文案）+ 服务端按名字匹配算价，不依赖 option id 的稳定性；`specKey` 只要求**服务端生成规则自洽**即可。这是典型的"简单可靠优先"取舍。

> 注意 `@Transactional`：三张表同生共死。没有事务时，"删了旧组、插新组失败"会把规格删光。阶段 5 购物车、阶段 6 下单同理。

### 4. 分页查询（带分类名）

```xml
<!-- shanyue-server/src/main/resources/mapper/DrinkMapper.xml -->
<select id="pageQuery" resultType="com.shanyue.vo.DrinkVO">
    select d.*, c.name as categoryName
    from drink d left join category c on d.category_id = c.id
    <where>
        <if test="name != null and name != ''">
            and d.name like concat('%', #{name}, '%')
        </if>
        <if test="categoryId != null">
            and d.category_id = #{categoryId}
        </if>
        <if test="status != null">
            and d.status = #{status}
        </if>
    </where>
    order by d.create_time desc
</select>
```

Service 里 `PageHelper.startPage(...)` + `new PageResult(page.getTotal(), page.getResult())`，和苍穹一模一样。✍️ 自己写。

### 5. 回显查询（编辑页要回显整棵规格树）

```java
public DrinkVO getByIdWithSpec(Long id) {
    Drink drink = drinkMapper.getById(id);
    DrinkVO vo = new DrinkVO();
    BeanUtils.copyProperties(drink, vo);
    // 组一次查出，组内选项循环查（数据量小可接受；面试可聊 N+1 与连表/嵌套 resultMap）
    List<SpecGroup> groups = specGroupMapper.getByDrinkId(id);
    List<SpecGroupVO> groupVOs = new ArrayList<>();
    for (SpecGroup g : groups) {
        SpecGroupVO gvo = new SpecGroupVO();
        BeanUtils.copyProperties(g, gvo);
        gvo.setSpecOptions(specOptionMapper.getByGroupId(g.getId()));
        groupVOs.add(gvo);
    }
    vo.setSpecGroups(groupVOs);
    return vo;
}
```

### 6. 删除与起售停售（连带校验的取舍）

删除：校验"起售中的饮品不能删"（苍穹 Drink 同款逻辑，消息改成「起售中的饮品不能删除」），然后**一个事务里删 drink + spec_option + spec_group**（drink_id 冗余的好处体现了）。

起售/停售：苍穹 `startOrStop` 直接复用。**要不要连带处理购物车？** 这是个真实的业务设计题，两个方案：

- **方案 A（推荐）：停售不动购物车，在下单时兜底校验**。理由：购物车是"顾客的意向"，商家停售瞬间去删顾客购物车体验很差；而下单服务端会重算金额并校验在售（阶段 6），下架饮品的购物车行根本无法成交。**校验放在"钱的关口"，而不是每个写入口**。
- **方案 B：停售即删除购物车中该饮品行**（一行代码 `shoppingCartMapper.deleteByDrinkId(id)`）。好处是顾客不会看到"加了却买不了"的条目；坏处是顾客购物车会"自己变少"，且停售接口耦合了购物车逻辑。

面试就讲 A + 为什么。✍️ 你实现 A（阶段 6 提交校验），B 的一行代码可以注释保留当对比素材。

## ✍️ 动手任务清单

- [ ] Drink/SpecGroup/SpecOption 实体 + DrinkDTO/SpecGroupDTO/SpecOptionDTO + DrinkVO/SpecGroupVO
- [ ] DrinkMapper/SpecGroupMapper/SpecOptionMapper（接口 + XML，insert 带 useGeneratedKeys）
- [ ] DrinkService：saveWithSpec / updateWithSpec（先删后插 + @Transactional）/ pageQuery / getByIdWithSpec / deleteBatch / startOrStop
- [ ] 分页连表带 categoryName；删除校验起售状态
- [ ] （可选实验）故意在 insertBatch 后抛异常，验证没有事务时数据被删了一半、有事务时回滚——把现象记进笔记

## ✅ 验收标准

1. admin 前端 → 饮品管理：新增「生椰拿铁」+ 4 组规格（杯型/温度/糖度/浓度，大杯+3元、加浓+2元），保存成功。
2. 列表页显示分类名、价格（分→元由前端处理）、招牌标记、销量。
3. 点编辑：规格组编辑器**完整回显** 4 组 10 个选项；把"大杯+300"改成"+500"、删掉"少冰"、新增一组"冰量"，保存后重新打开编辑页，看到的就是改后的树（先删后插生效）。
4. 数据库 `select * from spec_option where drink_id=2` 与编辑器一致；旧 id 已变化（对照验证整体覆盖）。
5. 停售该饮品 → 列表变灰；尝试删除被拦截；重新起售后可删除。
6. 批量勾选两个饮品删除 → 都没了，`spec_group/spec_option` 无残留行。

## 💡 面试讲点

- **"规格修改为什么先删后插？"** 见上文四条理由——能讲出 diff 方案为什么被放弃，比只会说"先删后插"高一个层级。
- **"三张表一起写，怎么保证一致性？"** `@Transactional`（Spring 声明式事务底层是 AOP），并说明回滚验证实验。
- **"停售了购物车里还有，怎么办？"** 讲方案 A/B 取舍：把校验放在金额结算关口，写入口保持简单。

---

# 阶段 4：管理端订单管理（2~3 天）

## 为什么现在做

管理端只剩订单这一块硬骨头。订单是全项目状态最多的实体，这一阶段重点练：**状态机思维 + 多条件动态查询 + 分组统计**。C 端下单（阶段 6）产生的订单，就从这里被商家消化掉。

## 改什么

### 1. 状态机（先背下来再写代码）

| 状态值 | 含义 | 从哪来 | 到哪去 |
|---|---|---|---|
| 1 | 待接单 | C 端下单 / 用户可取消(→5) | 接单(→2) / 商家取消(→5) / 超时自动取消(→5) |
| 2 | 制作中 | 接单 | 出餐叫号(→3) / 商家取消(→5) / 用户可催单 |
| 3 | 待取餐 | 出餐叫号 | 完成订单(→4) |
| 4 | 已完成 | 完成 | 终态（计入营业额） |
| 5 | 已取消 | 各处取消 | 终态 |

与苍穹的差异：苍穹是 1待付款/2待接单/3已接单/4派送中/5已完成/6已取消——砍掉了"待付款"（自取线下结账语义）和"派送中"（无配送），编号整体前移。**建议新建常量类，不要在代码里写裸数字**：

```java
// shanyue-common/src/main/java/com/shanyue/constant/OrderStatus.java
public class OrderStatus {
    public static final Integer TO_BE_CONFIRMED = 1;  // 待接单
    public static final Integer MAKING = 2;           // 制作中
    public static final Integer READY = 3;            // 待取餐
    public static final Integer COMPLETED = 4;        // 已完成
    public static final Integer CANCELLED = 5;        // 已取消
}
```

### 2. 多条件分页 conditionSearch

接口：`GET /admin/order/conditionSearch`，query：`number(订单号模糊)/pickUpCode/orderStatus/phone/beginTime/endTime + page/pageSize`。

```xml
<!-- shanyue-server/src/main/resources/mapper/OrderMapper.xml -->
<select id="conditionSearch" resultType="com.shanyue.entity.Orders">
    select * from orders
    <where>
        <if test="number != null and number != ''">
            and order_number like concat('%', #{number}, '%')
        </if>
        <if test="pickUpCode != null and pickUpCode != ''">
            and pick_up_code = #{pickUpCode}
        </if>
        <if test="phone != null and phone != ''">
            and phone like concat('%', #{phone}, '%')
        </if>
        <if test="orderStatus != null">
            and status = #{orderStatus}
        </if>
        <if test="beginTime != null">
            and order_time &gt;= #{beginTime}
        </if>
        <if test="endTime != null">
            and order_time &lt;= #{endTime}
        </if>
    </where>
    order by order_time desc
</select>
```

✍️ `OrdersPageQueryDTO`（字段与 query 对齐）、Service 里 PageHelper 套路、Controller 照苍穹 `OrderController.conditionSearch`。

### 3. 状态流转接口：统一校验写法（推荐做法）

五个操作接口：`PUT /admin/order/confirm`（1→2）、`PUT /admin/order/ready`（2→3）、`PUT /admin/order/cancel`（1/2→5，body 带 cancelReason）、`PUT /admin/order/complete`（3→4）、`GET /admin/order/details/{id}`。

❌ 不推荐：每个方法里手写 `if (order.getStatus() != 1) throw ...`——状态一多就散落一地，漏一个就是线上事故。

✅ 推荐：一个"查单 + 校验"私有方法，允许状态用集合表达：

```java
// shanyue-server/src/main/java/com/shanyue/service/impl/OrderServiceImpl.java
/** 查询订单并校验当前状态是否允许本次操作 */
private Orders getByIdWithStatusCheck(Long id, Set<Integer> allowedStatuses, String actionName) {
    Orders orders = orderMapper.getById(id);
    if (orders == null) {
        throw new BaseException("订单不存在");
    }
    if (!allowedStatuses.contains(orders.getStatus())) {
        throw new BaseException("当前状态不可" + actionName);
    }
    return orders;
}

public void confirm(OrdersConfirmDTO confirmDTO) {
    Orders orders = getByIdWithStatusCheck(confirmDTO.getId(),
            Set.of(OrderStatus.TO_BE_CONFIRMED), "接单");
    orders.setStatus(OrderStatus.MAKING);
    orderMapper.update(orders);
}

public void ready(OrdersConfirmDTO confirmDTO) {
    Orders orders = getByIdWithStatusCheck(confirmDTO.getId(),
            Set.of(OrderStatus.MAKING), "出餐");
    orders.setStatus(OrderStatus.READY);
    orderMapper.update(orders);
    // ✍️ 出餐叫号：通过 WebSocket 推送 READY_CALL（阶段 7 实现，先 TODO）
}

public void cancel(OrdersCancelDTO cancelDTO) {
    Orders orders = getByIdWithStatusCheck(cancelDTO.getId(),
            Set.of(OrderStatus.TO_BE_CONFIRMED, OrderStatus.MAKING), "取消");
    orders.setStatus(OrderStatus.CANCELLED);
    orders.setCancelReason(cancelDTO.getCancelReason());
    orders.setCancelTime(LocalDateTime.now());
    orderMapper.update(orders);
}

public void complete(OrdersConfirmDTO confirmDTO) {
    Orders orders = getByIdWithStatusCheck(confirmDTO.getId(),
            Set.of(OrderStatus.READY), "完成");
    orders.setStatus(OrderStatus.COMPLETED);
    orders.setCheckoutTime(LocalDateTime.now());
    orderMapper.update(orders);
}
```

非法流转（比如对"已完成"的订单点接单）返回 `code=0, msg="当前状态不可接单"`——前端会 toast，联调时逐个点一遍验证。

> 进阶：更优雅的是**状态机枚举 + 每个状态声明允许的目标操作**（enum 里放 `Set<OrderAction>`），或策略模式。面试时说明"当前规模用集合校验 + 集中方法，规模大了升级为枚举状态机"即可，不要为了模式而模式。

### 4. 订单详情（主表 + 明细）

`GET /admin/order/details/{id}` 返回 `OrderVO`（Orders 字段 + `orderItems: List<OrderItemVO>`）。两步查询：先查 orders，再 `select * from order_item where order_id = ?`，组装进 VO。✍️ 自己写（苍穹 `OrderVO`/`OrderDetailVO` 可改名复用，注意明细字段是 `specText/specKey/price/number/amount`）。

### 5. statistics：五个 tab 的数字

接口：`GET /admin/order/statistics`，data：`{ toBeConfirmed, making, ready, completed, cancelled }`。

**写法一：GROUP BY 一次往返（推荐）**

```xml
<select id="countGroupByStatus" resultType="com.shanyue.vo.OrderStatusCountVO">
    select status as status, count(*) as count from orders group by status
</select>
```

```java
public OrderStatisticsVO statistics() {
    List<OrderStatusCountVO> list = orderMapper.countGroupByStatus();
    Map<Integer, Integer> map = list.stream()
            .collect(Collectors.toMap(OrderStatusCountVO::getStatus, OrderStatusCountVO::getCount));
    return OrderStatisticsVO.builder()
            .toBeConfirmed(map.getOrDefault(OrderStatus.TO_BE_CONFIRMED, 0))
            .making(map.getOrDefault(OrderStatus.MAKING, 0))
            .ready(map.getOrDefault(OrderStatus.READY, 0))
            .completed(map.getOrDefault(OrderStatus.COMPLETED, 0))
            .cancelled(map.getOrDefault(OrderStatus.CANCELLED, 0))
            .build();
}
```

**写法二：5 个 count**（`select count(*) from orders where status=?` ×5）。简单直白，但 5 次数据库往返；订单表有 status 索引时也不慢。**数据量小随便选，能讲出区别就赢了**：GROUP BY 一次扫描全量状态、次数 O(1)；多次 count 每次都能命中 `idx_status` 索引，往返 O(5)。教学推荐 GROUP BY，顺便练 stream 装配。

## ✍️ 动手任务清单

- [ ] OrderStatus 常量类（删掉苍穹 Orders 实体里的旧状态常量，避免混淆）
- [ ] conditionSearch：DTO + XML 动态 SQL + Service 分页
- [ ] confirm / ready / cancel / complete 四个流转接口 + 统一状态校验
- [ ] details/{id} 主表+明细组装
- [ ] statistics（GROUP BY 版），VO 五个字段名严格对齐接口文档

## ✅ 验收标准

1. admin 前端 → 订单管理：顶部五个 tab 数字与 statistics 返回一致（可先用 SQL 手插两条 status=1/2 的测试订单）。
2. 用 `pickUpCode` 搜索框输入测试订单取餐号 → 精确命中；输入订单号片段 → 模糊命中；按状态筛选、按时间范围筛选分别生效。
3. 点"接单"：待接单→制作中，tab 数字同步变化；对已完成订单调接单接口（可用 swagger）→ `code=0, msg=当前状态不可接单`。
4. 制作中订单点"出餐叫号"→ 待取餐；点"完成"→ 已完成，且营业额统计能计入（阶段 8）。
5. 详情弹窗：订单信息 + 明细列表（含 specText 如"大杯/冰/五分糖"）完整展示。

## 💡 面试讲点

- **"状态流转怎么防非法操作？"** 集中校验方法 + 允许状态集合；进一步可升级枚举状态机；核心是"前置状态校验必须服务端做，前端按钮置灰只是体验"。
- **"为什么砍掉派送状态机？"** 业务建模决定：自取模式没有履约配送环节，保留只会带来永远走不到的状态分支；字段和状态一起做减法。
- **"动态多条件查询怎么写的？"** MyBatis `<where>+<if>` 拼接；注意 like 用 concat 防注入、日期用 `&gt;=`。

---

# 阶段 5：C 端菜单 + 购物车（2~3 天）

## 为什么

开始做 C 端（h5 前端，5174）。C 端两个关键词：**匿名**（没有登录）和**规格算价**（服务端说了算）。菜单接口是"一拖三"的嵌套结构，购物车是"同款合并 + 金额重算"，都是新东西。

## 改什么

### 1. 匿名设计说明（面试高频）

> **v2.0 注**：C 端已改为微信登录模式，见阶段 6.5——本节「不引入微信登录」为 v1.x 历史决策，保留作对照；user_id 回填与先登录后下单见阶段 6.5，面试话术可升级为「为什么从匿名切换为微信登录（券/积分/地址簿都挂在用户身上）」。骨架 `shanyue-server-skeleton` 的 `UserServiceImpl` 含 mock/真实双模式登录范本。

- 自取点单场景：顾客到店 → 扫码/点单屏 → 选饮品 → 留手机号（可选）→ 拿取餐号 → 取餐。**没有账号体系**也能完成闭环，所以不引入微信登录（省开发与审核成本）。
- 数据层面：`shopping_cart/orders.user_id` 字段**预留 NULL**。本期单机演示，购物车按 `(drink_id, spec_key)` 全表合并；真实多顾客要区分身份时，用"设备号 header"或微信登录回填 user_id 即可，表结构不用动。
- 这不是偷懒，是**场景驱动的最小实现**——面试讲出"什么场景可以不要登录"本身就是加分项。

### 2. /user/drink/list：一次带出规格组

接口：`GET /user/drink/list?categoryId=`（不传返回全部在售，只含 status=1，按 sold 降序，每个饮品内嵌 specGroups→specOptions）。

**做法一：Service 分步组装（苍穹 `DrinkServiceImpl.getByCategoryId` 的套路，推荐先写这个）**

```java
public List<DrinkVO> listForUser(Long categoryId) {
    List<Drink> drinks = drinkMapper.listByCondition(categoryId, StatusConstant.ENABLE); // status=1, order by sold desc
    List<DrinkVO> result = new ArrayList<>();
    for (Drink drink : drinks) {
        DrinkVO vo = new DrinkVO();
        BeanUtils.copyProperties(drink, vo);
        List<SpecGroupVO> groupVOs = new ArrayList<>();
        for (SpecGroup g : specGroupMapper.getByDrinkId(drink.getId())) {
            SpecGroupVO gvo = new SpecGroupVO();
            BeanUtils.copyProperties(g, gvo);
            gvo.setSpecOptions(specOptionMapper.getByGroupId(g.getId()));
            groupVOs.add(gvo);
        }
        vo.setSpecGroups(groupVOs);
        result.add(vo);
    }
    return result;
}
```

逻辑直白、和阶段 3 的 getByIdWithSpec 复用同一个组装私有方法（✍️ 抽出来）。缺点是 1+N+M 次查询（饮品 N、每饮品组 M 组）。

**做法二：MyBatis 嵌套 resultMap + collection（进阶，面试提一嘴）**

```xml
<resultMap id="DrinkWithSpecMap" type="com.shanyue.vo.DrinkVO">
    <id property="id" column="id"/>
    <result property="name" column="name"/>
    <result property="categoryId" column="category_id"/>
    <result property="price" column="price"/>
    <result property="image" column="image"/>
    <result property="description" column="description"/>
    <result property="featured" column="featured"/>
    <result property="sold" column="sold"/>
    <result property="status" column="status"/>
    <collection property="specGroups" ofType="com.shanyue.entity.SpecGroup"
                column="id" select="listGroupByDrink">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <result property="required" column="required"/>
        <result property="sort" column="sort"/>
        <collection property="specOptions" ofType="com.shanyue.entity.SpecOption"
                    column="id" select="listOptionByGroup"/>
    </collection>
</resultMap>

<select id="listWithSpec" resultMap="DrinkWithSpecMap">
    select * from drink where status = 1
    <if test="categoryId != null"> and category_id = #{categoryId}</if>
    order by sold desc
</select>
<select id="listGroupByDrink" resultType="com.shanyue.entity.SpecGroup">
    select * from spec_group where drink_id = #{drinkId} order by sort
</select>
<select id="listOptionByGroup" resultType="com.shanyue.entity.SpecOption">
    select * from spec_option where group_id = #{groupId} order by sort
</select>
```

两条路殊途同归（嵌套 select 底层同样是分步查），resultMap 写法把组装下沉到 XML，Service 更干净。**建议：先写做法一跑通联调，再用做法二重构一次**，对比代码量与可读性——这本身就是很好的面试故事。

### 3. 购物车：实体与"先查后插"

```java
// shanyue-pojo/src/main/java/com/shanyue/entity/ShoppingCart.java（苍穹同名类改造）
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ShoppingCart implements Serializable {
    private Long id;
    private Long userId;        // 预留
    private Long drinkId;
    private String drinkName;   // 快照
    private String image;       // 快照
    private String specKey;     // 服务端生成的规格标识
    private String specText;    // 展示文案
    private Integer price;      // 单价(分,服务端算)
    private Integer number;
    private Integer amount;     // 小计(分)
    private LocalDateTime createTime;
}
```

C 端购物车控制器路径与苍穹完全一致（`/user/shoppingCart/add|list|sub|clean`），直接在苍穹 `ShopCartController` 上改。

**specKey 是什么？** 一杯"生椰拿铁·大杯·冰·五分糖·标准"的唯一标识。规则（**服务端生成，前端只透传**）：

```
按 spec_group.sort 升序，把每组的 groupId-optionId 用逗号拼接：
"1-12,2-31,3-45,4-67"
specText 同序用 "/" 拼："大杯/冰/五分糖/标准"
```

> 接口文档示例里的 `"1-12,31,45,67"` 是简写展示；实现时只要保证"同一饮品不同组合 → 不同字符串、同组合 → 同字符串"即可，前端把它当黑盒透传。**务必由服务端统一生成**——客户端拼接可以被伪造，且规则改了只动一处。

**add 请求体**（注意是名字不是 id，前端只发它选中的文案）：

```json
{ "drinkId": 1, "specs": [ {"groupName":"杯型","optionName":"大杯"}, {"groupName":"温度","optionName":"冰"} ] }
```

### 4. add 核心逻辑：服务端重算金额（必背）

```java
// shanyue-server/src/main/java/com/shanyue/service/impl/ShopCartServiceImpl.java
@Transactional
public void add(ShoppingCartDTO cartDTO) {
    // ① 查饮品并校验在售
    Drink drink = drinkMapper.getById(cartDTO.getDrinkId());
    if (drink == null || drink.getStatus() != 1) {
        throw new BaseException("该饮品已下架");
    }
    // ② 一次查出该饮品的组+选项，按组 sort 遍历，与请求的 specs 按名字匹配
    List<SpecGroup> groups = specGroupMapper.getByDrinkId(drink.getId());
    Map<String, String> reqMap = cartDTO.getSpecs().stream()      // groupName -> optionName
            .collect(Collectors.toMap(SpecItemDTO::getGroupName, SpecItemDTO::getOptionName));

    int unitPrice = drink.getPrice();
    List<String> texts = new ArrayList<>();
    List<String> keyParts = new ArrayList<>();
    for (SpecGroup group : groups) {
        SpecOption matched = null;
        String wantName = reqMap.get(group.getName());
        if (wantName != null) {
            for (SpecOption option : specOptionMapper.getByGroupId(group.getId())) {
                if (option.getName().equals(wantName)) { matched = option; break; }
            }
        }
        // 必选组没给，或给了查不到的选项名 → 规格非法（防篡改）
        if (matched == null) {
            if (group.getRequired() == 1) throw new BaseException("规格信息有误");
            continue;   // 可选组允许不选
        }
        unitPrice += matched.getPriceDelta();      // ③ 加价求和
        texts.add(matched.getName());
        keyParts.add(group.getId() + "-" + matched.getId());
    }
    String specKey = String.join(",", keyParts);
    String specText = String.join("/", texts);

    // ④ 相同 drinkId+specKey 合并数量
    ShoppingCart exist = shoppingCartMapper.getByDrinkIdAndSpecKey(drink.getId(), specKey);
    if (exist != null) {
        exist.setNumber(exist.getNumber() + 1);
        exist.setAmount(exist.getPrice() * exist.getNumber());
        shoppingCartMapper.update(exist);
        return;
    }
    // ⑤ 新行（金额一律服务端算）
    ShoppingCart cart = ShoppingCart.builder()
            .drinkId(drink.getId()).drinkName(drink.getName()).image(drink.getImage())
            .specKey(specKey).specText(specText)
            .price(unitPrice).number(1).amount(unitPrice)
            .createTime(LocalDateTime.now())
            .build();
    shoppingCartMapper.insert(cart);
}
```

表上 `(drink_id, spec_key)` 唯一键兜底并发重复（前面"先查后插"处理正常路径，唯一键兜底竞态，插入冲突时捕获后改为数量+1 或直接重试）。✍️ 把这个兜底补上。

**为什么金额必须服务端算？** 前端传来的任何金额都可被 Postman 伪造；价格基准（drink.price）与加价（price_delta）只有服务端数据库里是真值。C 端展示的价格是"仅供参考的预览"，**下单入库的金额一律以服务端重算为准**——这句话请写进你的 README。

### 5. sub 与 clean

- `POST /user/shoppingCart/sub`：body `{drinkId, specKey}` → 找到该行 number-1，减到 0 删除该行。✍️ 仿照 add 的合并查询写。
- `DELETE /user/shoppingCart/clean`：`delete from shopping_cart`（匿名模式就是全清）。✍️（v2.0 注：C 端已改为微信登录模式，见阶段 6.5——clean 改为按当前登录用户 `delete from shopping_cart where user_id = ?`）

## ✍️ 动手任务清单

- [ ] /user/drink/list（先做法一，跑通后用做法二 resultMap 重构一遍）
- [ ] /user/category/list（阶段 2 已做，确认 h5 能拿到 icon）
- [ ] ShoppingCart 实体/DTO/VO + Mapper（getByDrinkIdAndSpecKey / update / insert / sub / clean）
- [ ] add 全流程：校验在售 → 名字匹配规格 → 重算单价 → specKey/specText → 合并或插入
- [ ] sub：数量-1 到 0 删行；clean：清空
- [ ] 唯一键并发兜底（捕获 DuplicateKeyException 转合并）

## ✅ 验收标准

1. h5 前端（5174）`VITE_USE_MOCK=false`：首页分类侧栏 + 按销量排序的商品卡正常渲染。
2. 点商品卡 → 规格弹窗四组选项齐全、必选组默认选中；切"大杯"价格 +3 元（前端预览）。
3. 加入购物车 → 底部购物车栏数量/金额正确；**再点一次同规格** → 合并为一行 number=2；换规格再加 → 出现第二行。
4. 数据库：`select drink_id, spec_key, spec_text, price, number, amount from shopping_cart`，price = 1590+300=1890，amount=1890×2。
5. 购物车行 +1 / -1 / 减到 0 行消失 / 清空按钮全通。
6. 用 Postman 直接 POST add，把 specs 里塞一个不存在的选项名 → `code=0, msg=规格信息有误`（服务端校验生效）。

## 💡 面试讲点

- **"金额为什么服务端算？"** 防篡改 + 数据唯一真值在库里；前端价格只是预览。
- **"匿名购物车怎么区分用户？"** 本期场景单点自取 + 单机演示做最小实现；字段预留 user_id，扩展路径是设备号/微信登录；讲出"什么时候必须加身份"的边界。（v2.0 注：C 端已改为微信登录模式，见阶段 6.5——该问题已落地为 user_id 回填方案，可按新口径作答）
- **"先查后插的并发问题？"** 查与插之间有时间窗，两个请求可能同时判断"不存在"；用 (drink_id, spec_key) 唯一键兜底，冲突后转合并。能顺口讲"检查-然后-行动"竞态就很好。

---

# 阶段 6：C 端下单 + 取餐号（2~3 天）

## 为什么

项目的高光时刻：顾客点完单，屏幕蹦出一个大大的 **A01**，商家后台同时弹出来单提醒。这一步把前面所有东西串起来，还要做一个经典小设计——**按天自增的取餐号**。

## 改什么

### 1. submit 流程（六步）

接口：`POST /user/order/submit`，body `{ remark, phone }`（可选），响应 data：

```json
{ "id": 100, "orderNumber": "20250922001", "pickUpCode": "A01", "orderAmount": 1890, "estimatedWaitMinutes": 10 }
```

流程（全部在一个 `@Transactional` 方法里，除 Redis 操作外）：

```
① 购物车判空（空购物车不能下单）
② 逐行校验饮品仍在售（停售兜底，呼应阶段 3 方案 A）
③ 金额重算：orderAmount = Σ(行 amount)（行金额在加购时已是服务端算的，这里复核一遍 drink.price+Σdelta 与行 price 是否一致，不一致以库为准并更新行）
④ 生成 orderNumber + pickUpCode（Redis，见下）
⑤ 写 orders 一行 + 批量写 order_item N 行（购物车行 → 明细行的快照转换）
⑥ 清空购物车；estimated_wait_minutes 按单量简单估算（如 10 + (当前未完成订单数/5)*5）
```

```java
// shanyue-server/src/main/java/com/shanyue/service/impl/OrderServiceImpl.java
@Transactional
public OrderSubmitVO submit(OrdersSubmitDTO submitDTO) {
    // ①~③ ✍️ 按上面六步自己写
    List<ShoppingCart> carts = shoppingCartMapper.list();
    if (carts == null || carts.isEmpty()) throw new BaseException("购物车为空");

    int totalAmount = 0;
    for (ShoppingCart cart : carts) {
        Drink drink = drinkMapper.getById(cart.getDrinkId());
        if (drink == null || drink.getStatus() != 1) {
            throw new BaseException(cart.getDrinkName() + " 已下架，请移除后重新下单");
        }
        totalAmount += cart.getAmount();
    }

    // ④ 取号（见下节）
    String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // 20250922
    Long seq = pickUpCodeGenerator.next(today);
    Orders orders = Orders.builder()
            .orderNumber(today + String.format("%03d", seq))
            .pickUpCode(String.format("A%02d", seq))
            .status(OrderStatus.TO_BE_CONFIRMED)
            .amount(totalAmount)
            .remark(submitDTO.getRemark())
            .phone(submitDTO.getPhone())
            .estimatedWaitMinutes(10)
            .orderTime(LocalDateTime.now())
            .checkoutTime(LocalDateTime.now())
            .build();
    orderMapper.insert(orders);                        // useGeneratedKeys 回填 id

    // ⑤ 明细批量插入 ✍️（购物车行转 OrderItem，insertBatch <foreach>）
    // ⑥ 清空购物车 ✍️
    // 阶段 7：webSocketServer 推送 NEW_ORDER ✍️（先 TODO）
    return OrderSubmitVO.builder()
            .id(orders.getId()).orderNumber(orders.getOrderNumber())
            .pickUpCode(orders.getPickUpCode()).orderAmount(totalAmount)
            .estimatedWaitMinutes(orders.getEstimatedWaitMinutes())
            .build();
}
```

### 2. 取餐号按天自增：三种方案对比（本文档最重要的面试点之一）

| 方案 | 思路 | 并发 | 复杂度 | 问题 |
|---|---|---|---|---|
| ① **Redis INCR（推荐）** | key=`shanyue:order:seq:20250922`，INCR 原子自增，首次设过期 | 天然原子，无并发问题 | 极低 | Redis 数据非持久强一致；重启丢失可接受（取餐号断号无业务损失） |
| ② MySQL 当日 max+1 | `select max(pick_up_code) from orders where date(order_time)=curdate()` 再 +1 | ❌ 两个请求同时读到同一 max → 重号 | 低 | 必须 `select ... for update` 锁住或靠唯一键重试，代码绕；高峰全挤在一行锁上 |
| ③ 独立序列表 | `seq_table(name, value)`，UPDATE value=value+1 | 原子但要行锁 | 中 | 多一个表、一次事务往返；本质是把计数器搬进 MySQL，杀鸡用牛刀 |

**推荐 ①** 的理由：INCR 是 Redis 原子命令，单线程模型天然并发安全；取餐号只要求"当天不重号"，不要求"连续不断号"，对持久性要求极低——正好匹配 Redis 的特性。而且**入门 Redis 练手正合适**：一个 key、一条命令、一个过期时间。

**完整代码**（新建一个小组件，别把 Redis 细节糊在 Service 里）：

```java
// shanyue-server/src/main/java/com/shanyue/utils/PickUpCodeGenerator.java
@Component
public class PickUpCodeGenerator {

    private static final String KEY_PREFIX = "shanyue:order:seq:";
    private final StringRedisTemplate stringRedisTemplate;

    public PickUpCodeGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 取当天下一个序号（1 开始）。原子性由 Redis INCR 保证。
     * @param today 形如 20250922
     */
    public long next(String today) {
        String key = KEY_PREFIX + today;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            // 当天第一个订单：给计数器设 25 小时过期（跨过当天零点仍可用，第二天是新 key）
            stringRedisTemplate.expire(key, Duration.ofHours(25));
        }
        return seq == null ? 1L : seq;
    }
}
```

要点讲给面试官听：

- `increment` 对应 Redis 的 `INCR`，**原子**，不需要加锁；
- key 带日期 → 天然"按天重置"，第二天是全新 key；
- `expire` 防止计数器 key 永久堆积（垃圾数据回收）；
- 挂一档两个产物：`pickUpCode = A%02d`（A01…A99，超过 99 自动变 A100 也不出错）、`orderNumber = yyyyMMdd + %03d`，**同一次 INCR 保证两号同源一致**。

### 3. 其余 C 端订单接口（小）

- `GET /user/order/byPickUpCode/{code}`：`select * from orders where pick_up_code=#{code} and date(order_time)=curdate() limit 1`（限当天，防跨天撞号），组装明细返回 OrderVO。✍️
- `PUT /user/order/reminder/{id}`：仅 1/2 状态可催单（复用阶段 4 的 `getByIdWithStatusCheck`），✍️ 阶段 7 接上 WebSocket REMINDER。
- `PUT /user/order/cancel/{id}`：仅 1（待接单）可取消，写 cancel_reason「用户取消」。✍️

## ✍️ 动手任务清单

- [ ] OrdersSubmitDTO / OrderSubmitVO / OrderItem（实体改造）
- [ ] PickUpCodeGenerator + Redis 连通测试（redis-cli 里 `keys shanyue:*` 看得到计数器）
- [ ] submit 六步全流程（事务注解别忘）
- [ ] byPickUpCode / reminder / cancel 三个小接口
- [ ] （可选）estimatedWaitMinutes 按未完成订单数动态估算

## ✅ 验收标准

1. h5 点单 → 规格弹窗加购几杯 → 去结算 → 填备注手机号 → 提交：弹出**取餐号大字（A01）**。
2. `select * from orders`：order_number=2025xxxx001、pick_up_code=A01、金额等于购物车合计、状态 1；`order_item` 行数与购物车一致；**购物车已清空**。
3. 再下一单 → A02（同一天连续）；把 Redis key 删掉再下一单 → 重新从 A01 开始（验证 key 机制）。
4. **admin 前端订单页**：新订单实时出现在"待接单" tab（阶段 7 之前刷新可见即可），数字 +1。
5. h5 订单查询页：输入 A01 → 查到订单详情；对它催单/取消，状态校验生效（已完成的不能取消）。
6. Postman 伪造 amount 提交购物车不存在的操作无效（金额只在服务端出现）。

## 💡 面试讲点

- **"取餐号为什么用 Redis？"** 背三种方案对比表 + "取餐号要原子自增、不要持久强一致，恰好匹配 Redis INCR 特性"；能讲出方案②的重号竞态细节最加分。
- **"下单怎么保证订单和明细一致？"** 单个事务；明细是快照；清购物车放事务内。
- **"取消订单有哪些入口？"** 用户取消（仅待接单）、商家取消（待接单/制作中）、超时自动取消（阶段 7）——同一个状态收敛到 5，但 cancel_reason 区分来源。

---

# 阶段 6.5：C 端微信登录与地址簿（2~3 天，v2.0 新增）

> 定位：v2.0 把 C 端从「匿名自取」升级为「微信登录」模式——积分、优惠券、地址簿、我的订单都挂在用户身上，营销与留存功能才有落点。骨架工程已带全套范本（UserController / UserServiceImpl / JwtTokenUserInterceptor / UserLoginVO），本阶段以**联调确认 + 补齐地址簿**为主，难度低于阶段 5/6。

## 为什么

- 匿名模式（阶段 5 的历史决策）撑不起「会员」：优惠券领取、积分签到、我的地址都要求"下次来还能认出你"；
- 微信登录对用户感知最轻（一键授权），对后端成本也最低（一条 SQL 查/建用户 + 一个 JWT）；
- AddressBook 七个接口是课程已学的标准 CRUD，几乎零成本补齐，却直接换来小程序「外送模式」的完整闭环。

## 改什么

### 1. 数据与鉴权：user 表 + JWT user 版拦截器 + BaseContext

- user 表骨架已给（`openid` 唯一索引兑底，种子用户 `mock_openid_demo`，无昵称头像的隐私字段允许 NULL）；
- `JwtTokenUserInterceptor` 拦 `/user/**`，放行 `/user/user/login` 与 `/user/shop/status`；解析成功把 `userId` 放进 `BaseContext`（与 admin 端同构，**密钥隔离**：user-secret-key 与 admin-secret-key 不同）；
- 此后所有 C 端接口的 userId **一律取 ThreadLocal**，不信任前端传参——购物车、订单、地址、资料全按此改造（原匿名版购物车的全表合并改为按 `user_id` 查询，见阶段 5 的 v2.0 注）。

### 2. jscode2session 流程（文本图）与骨架 mock 模式

```
wx.login() ──code──► POST /user/user/login ──code+appid+secret──► 微信 jscode2session
                        │                                          │
                        │◄──────────── openid ─────────────────────┘
                        ├─ user 表按 openid 查：无 → builder 新用户 → insert（自动注册）
                        ├─ JwtUtil.createJWT(userSecretKey, userTtl, {userId})
                        └─ 返回 {id, openid, token, nickName, avatarUrl}
                          小程序 wx.setStorageSync 存登录态 → 之后请求带头 token
```

- **无真实 appid 先跑通**：骨架 `UserServiceImpl` 提供 mock 模式（yml 配 `shanyue.wechat.mock-openid: true`），openid 直接取 `"mock_openid_" + code` 跳过微信接口——登录态/JWT/购物车/下单全链路与真实模式**完全一致**，只是身份来源是假的；上线时关开关、填真实 appid/secret，业务代码零改动；
- `sql/data.sql` 预置了 `mock_openid_demo` 用户：传 `code=demo` 即命中它，适合 curl 自测；
- 安全红线：secret 只能存服务端配置，绝不能进小程序代码或前端仓库。

### 3. AddressBook 七接口（课程已学，几乎零成本）

`GET /user/addressBook/list`、`GET /user/addressBook/{id}`、`POST /user/addressBook`、`PUT /user/addressBook`、`DELETE /user/addressBook?id=`（query 传 id，课程同款约定）、`GET /user/addressBook/default`、`PUT /user/addressBook/default`（body 传 id）。

- 新增/修改时 user_id 取 ThreadLocal（不从 body 信任）；设默认用「先清旧默认再置新默认」两步 update，包在一个事务里；
- 外送下单（阶段 6 双模式 `deliveryMode=2`）直接消费这批接口：结算页查默认地址、提交带 addressBookId、订单快照收货人/电话/地址。

## ✍️ 动手任务清单

- [ ] yml 打开 mock 开关（或填真实 appid/secret），按骨架 README 自测 `POST /user/user/login {"code":"demo"}` 返回 token
- [ ] 购物车/订单/地址全部改为按 `BaseContext.getCurrentId()` 查询（替换匿名版全表合并写法）
- [ ] AddressBookController + Service + Mapper 七接口（含默认地址事务）
- [ ] 小程序 `utils/config.js` 改 `USE_MOCK=false`：登录页 → 菜单 → 购物车 → 结算全链路带 token
- [ ] 外送模式：新增/选默认地址 → 结算页带 addressBookId 下单 → 订单详情显示收货信息快照

## ✅ 验收标准（前端联调，小程序全流程点通）

1. 小程序登录页一键登录成功，storage 里有 token；杀掉小程序重进，token 未过期时免登录；
2. 未登录直接点下单 → 401 → 自动拉起登录页（先登录后下单闭环）；
3. 地址簿：新增/编辑/删除/设默认全通，结算页默认地址回显正确；
4. 外送下单：金额含 5 元配送费，订单快照收货人/电话/地址（改地址不影响历史订单）；
5. `select * from user` 能看到 mock/真实 openid 用户，重复登录不重复建户。

## 💡 面试讲点

- **"JWT 双端怎么隔离？"** 两套密钥 + 两个拦截器 + 路径分工（/admin/** 与 /user/**），一端泄露伪造不出另一端令牌；
- **"为什么先登录后下单？"** 身份是一切用户数据（券/积分/地址/订单）的外键；userId 取自 ThreadLocal 防横向越权（不传 id、查不了别人）；
- **"openid 是什么？"** 微信用户在某个小程序下的唯一标识，unionid 才跨应用——能说清两者边界即可。

---

# 阶段 7：缓存与实时提醒（3~4 天，面试亮点）

## 为什么

前六个阶段功能全通，但有两个"工程味"的问题：① C 端菜单每次都连表查三层，读多写少，天然适合缓存；② 商家不能一直刷新页面等订单——下单/催单/出餐要**服务端主动推**。这一阶段是整个项目简历上最亮的部分。

## 改什么

### 1. Redis 缓存 C 端菜单

**key 设计**（缓存的是"全部在售饮品（含规格树）"）：

```
shanyue:cache:drink:list            → 全部
shanyue:cache:drink:list:{categoryId} → 按分类
```

统一前缀 + 冒号分层是 Redis key 的通用命名法（等价于"命名空间"，`keys shanyue:cache:drink:list*` 一把全清）。

**读**（在 C 端 `DrinkService.listForUser` 外包一层，苍穹 DrinkController 缓存的同款套路）：

```java
public List<DrinkVO> listForUserWithCache(Long categoryId) {
    String key = "shanyue:cache:drink:list" + (categoryId == null ? "" : ":" + categoryId);
    // 命中直接回
    List<DrinkVO> cache = (List<DrinkVO>) redisTemplate.opsForValue().get(key);
    if (cache != null) {
        log.info("缓存命中：{}", key);
        return cache;
    }
    // 未命中查库并回填
    List<DrinkVO> list = this.listForUser(categoryId);
    redisTemplate.opsForValue().set(key, list);
    return list;
}
```

> 苍穹用 `RedisTemplate` 直接存对象（value 是 JDK 序列化）；进阶做法是 `StringRedisTemplate` + 手动 JSON 序列化，redis-cli 里能直接看到内容、跨语言可读。两种都写一遍再选型，就是很好的笔记素材。

**写（缓存一致性）：管理端一改，缓存立刻清**。清缓存时机矩阵：

| 管理端操作 | 影响 | 清理动作 |
|---|---|---|
| 新增饮品 | list 变化 | 清 `list:*`（或至少全量 key） |
| 修改饮品（含改价/改名） | 数据+价格变 | 清 `list:*` |
| 修改规格组/选项 | 规格树变 | 清 `list:*` |
| 起售/停售 | list 过滤结果变 | 清 `list:*` |
| 批量删除 | list 变 | 清 `list:*` |
| 修改分类（改名/icon） | 分类侧栏变（若分类也缓存则同理） | 清对应 key |

因为新增/修改时你知道 categoryId，可以只清 `list:该分类` + 全量 `list`；但**删除/起售停售是批量操作**，逐个清太啰嗦——苍穹用 `redisTemplate.keys("drink_*")` 模式匹配批量删，你也这么做（封装一个私有方法）：

```java
// shanyue-server/src/main/java/com/shanyue/controller/admin/DrinkController.java
private void clearDrinkCache() {
    Set<String> keys = redisTemplate.keys("shanyue:cache:drink:list*");
    if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
    }
}
```

> **关于 CacheEvict**：Spring Cache 的 `@Cacheable/@CacheEvict` 注解能把"读回填/写清除"声明式化，苍穹没用而是手写。你能讲出两者取舍即可：注解优雅但 key 粒度受 SpEL 限制、且"先改库再清缓存"的顺序要自己保证；手写啰嗦但一眼看懂时机。本项目保持手写（与苍穹一致，代码评审友好）。
>
> **为什么是"删缓存"不是"更新缓存"？** 改一次库就重算一次缓存，若短时间改 N 次就是 N 次无效重算；删掉让下次读惰性回填，实现简单且不易脏。（这是缓存一致性的经典结论，面试常问。）
>
> **防穿透一句话**：查一个不存在的 categoryId 会每次穿透到库。简单方案：查不到时缓存一个空列表并设短过期（空值缓存）；更强的是布隆过滤器（判断"一定不存在"拦截请求）——本项目数据量小，**知道概念、说得出取舍即可，不实现**。

### 2. WebSocket 来单/催单/叫号

苍穹的 `WebSocketServer`（`@ServerEndpoint("/ws/{sid}")` + sessionMap + `sendToAllClient`）原样复用；确认 `WebSocketConfiguration` 里注册了 `ServerEndpointExporter`。

admin 前端已预留 ws 消息处理：连 `ws://localhost:8080/ws/{shopId}`，收到 `{ "type": "NEW_ORDER" | "REMINDER" | "READY_CALL", "data": {...} }` 就弹提示。你只需要在三个业务点推消息：

```java
// 注入
@Autowired
private WebSocketServer webSocketServer;

// 统一推一个私有方法（放个工具或直接在 Service 里）
private void pushWs(String type, Map<String, Object> data) {
    Map<String, Object> message = new HashMap<>();
    message.put("type", type);
    message.put("data", data);
    // 苍穹工程已引入 fastjson2
    webSocketServer.sendToAllClient(com.alibaba.fastjson2.JSON.toJSONString(message));
}

// 推送点 ①：C端下单成功后（OrderServiceImpl.submit 末尾，注意要在事务提交后推更严谨，教学项目事务内推可接受）
pushWs("NEW_ORDER", Map.of("pickUpCode", orders.getPickUpCode(),
                           "orderNumber", orders.getOrderNumber()));

// 推送点 ②：C端催单（reminder 校验通过后）
pushWs("REMINDER", Map.of("pickUpCode", orders.getPickUpCode()));

// 推送点 ③：商家出餐叫号（ready 方法里）
pushWs("READY_CALL", Map.of("pickUpCode", orders.getPickUpCode()));
```

✍️ 把三处推送接上（阶段 4/5/6 留的 TODO）。

### 3. 订单超时自动取消

**先回顾苍穹的做法（Spring Task 轮询）**——`OrderTask` 每分钟扫一次超时未支付订单。你改造它：

```java
// shanyue-server/src/main/java/com/shanyue/task/OrderTask.java
@Scheduled(cron = "0 * * * * ?")   // 每分钟
public void cancelTimeoutOrders() {
    LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);
    // ✍️ mapper：select * from orders where status=1 and order_time < #{deadline}
    List<Orders> timeoutOrders = orderMapper.getByStatusAndOrderTimeBefore(OrderStatus.TO_BE_CONFIRMED, deadline);
    for (Orders orders : timeoutOrders) {
        orders.setStatus(OrderStatus.CANCELLED);
        orders.setCancelReason("超时未接单，自动取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }
}
```

记得启动类加 `@EnableScheduling`。轮询的问题：**有延迟（最多 1 分钟）、白扫全表（订单量大时浪费）、多实例部署会重复执行**。

**升级方案：RabbitMQ 延迟消息（面试加分项，做不做都先读懂）**

思路对比：

- **思路一：TTL + 死信队列（无需插件）**——下单后发一条消息进"延迟队列"（队列设 15 分钟 TTL，无消费者），TTL 到期消息被路由到死信交换机 → 落到"取消队列" → 消费者收到订单号，**校验状态仍是 1 才取消**。注意：队列 TTL 是队头阻塞（前一条没到期后面全堵）——固定 15 分钟刚好没这个问题；若每种时长建一个队列就规避了。
- **思路二：rabbitmq_delayed_message_exchange 插件**——交换机直接支持 `x-delayed-message`，消息头带 delay，到点投递。更灵活，但要装插件。

骨架代码（思路一）：

```xml
<!-- shanyue-server/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```yaml
# application-dev.yml 追加
spring:
  rabbitmq:
    host: localhost
    port: 5672
    virtual-host: /
    username: guest
    password: guest
```

```java
// shanyue-server/src/main/java/com/shanyue/config/RabbitMQConfiguration.java
@Configuration
public class RabbitMQConfiguration {

    /** 延迟队列：15 分钟 TTL，无消费者，到期进死信 */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable("order.delay.queue")
                .withArgument("x-message-ttl", 15 * 60 * 1000)
                .withArgument("x-dead-letter-exchange", "order.cancel.exchange")
                .withArgument("x-dead-letter-routing-key", "order.cancel")
                .build();
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable("order.cancel.queue").build();
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange("order.cancel.exchange");
    }

    @Bean
    public Binding cancelBinding() {
        return BindingBuilder.bind(orderCancelQueue()).to(orderCancelExchange()).with("order.cancel");
    }
}
```

```java
// 下单成功后发消息（submit 里，事务外发更稳，教学可先事务内）
rabbitTemplate.convertAndSend("order.delay.queue", orders.getId());   // 直接发到队列名

// 消费者：收到"到期"的订单号，校验状态再取消
@Component
@Slf4j
public class OrderTimeoutConsumer {
    @Autowired private OrderMapper orderMapper;

    @RabbitListener(queues = "order.cancel.queue")
    public void onTimeout(Long orderId) {
        log.info("订单超时检查：{}", orderId);
        Orders orders = orderMapper.getById(orderId);
        // 双重校验：15 分钟里商家可能已接单/顾客已取消——只有仍是待接单才取消
        if (orders != null && orders.getStatus() == OrderStatus.TO_BE_CONFIRMED) {
            orders.setStatus(OrderStatus.CANCELLED);
            orders.setCancelReason("超时未接单，自动取消");
            orders.setCancelTime(LocalDateTime.now());
            orderMapper.update(orders);
        }
    }
}
```

✍️ 任务：先把 Spring Task 版跑通（简单可靠）；再用 RabbitMQ 版替换（或做成开关 `@ConditionalOnProperty`），把"为什么消费者里还要再校验状态"想明白（**消息只表达"时间到了"，不表达"状态一定还是 1"**——消费端永远要以库为准）。

## ✍️ 动手任务清单

- [ ] listForUser 加缓存 + 管理端 5 个写操作接 clearDrinkCache
- [ ] 实验：缓存生效前后对比 SQL 日志（`logging.level.com.shanyue.mapper: debug` 能看到第二次没查库）
- [ ] WebSocket 三处推送 + admin 前端验证弹提示
- [ ] Spring Task 超时取消版
- [ ] （进阶）RabbitMQ TTL+死信版，插桩日志观察消息 15 分钟后到达
- [ ] 笔记：写一段"删缓存 vs 更新缓存 / 空值缓存 vs 布隆"的理解

## ✅ 验收标准

1. h5 打开菜单 → admin 改某个饮品价格并保存 → **不重启**：h5 侧栏再次请求拿到新价格（缓存被清）；redis-cli `keys shanyue:cache:*` 观察键的生灭。
2. 关闭 Redis 再启动应用：菜单仍可用（缓存异常要能降级到查库，try-catch 包住读缓存——✍️ 补上这个健壮性）。
3. admin 开着页面，h5 下单 → admin **不用刷新**弹出"新订单 A02"；h5 催单 → 弹催单提醒；admin 点出餐 → 弹叫号 A02。
4. 手工把一条测试订单 order_time 改到 20 分钟前 → 等下一次定时调度 → 状态自动变 5，cancel_reason「超时未接单，自动取消」。
5. （进阶）RabbitMQ 版：下单 → 管理台 `order.delay.queue` 有一条消息 → 15 分钟后 `order.cancel.queue` 收到 → 订单被取消；中途接单则消息到期后**不会**取消（状态校验生效）。

## 💡 面试讲点

- **"缓存一致性怎么处理？"** 读：未命中回填；写：先更库后删缓存（讲清为什么不更新缓存）；批量操作模式匹配删除；可延伸先删后更/延迟双删的讨论。
- **"缓存穿透/雪崩怎么办？"** 穿透：空值缓存/布隆；雪崩：过期时间加随机、多级缓存——本项目做了什么、为什么够用。
- **"WebSocket 和轮询的区别？"** 服务端主动推 vs 客户端定时拉；sessionMap 管理连接；来单提醒对"实时性"的价值。
- **"超时取消为什么从定时任务升级到延迟消息？"** 精确触发、不用全表扫描、天然支持集群（每条消息只被消费一次）；并强调消费端状态校验。

---

# 阶段 8：数据统计 + 收尾（2 天）

## 为什么

给管理端首页一块"今日数据"看板，然后像模像样地把项目交付出去：README、打包、部署。面试官点开你的仓库，第一眼看到的就是这些。

## 改什么

### 1. 今日数据 `GET /admin/report/todayData`

data：`{ "turnover": 38650, "orderCount": 21, "completedCount": 15, "toBeConfirmedCount": 2 }`（turnover 单位分）。

```xml
<!-- 两种写法对比：多次 count（清晰）vs 单条条件聚合（一次往返） -->
<select id="sumTodayTurnover" resultType="java.lang.Long">
    select ifnull(sum(amount), 0) from orders
    where status = 4 and order_time between #{begin} and #{end}
</select>
<select id="countToday" resultType="java.lang.Long">
    select count(*) from orders
    <where>
        order_time between #{begin} and #{end}
        <if test="status != null"> and status = #{status}</if>
    </where>
</select>
```

✍️ Service 里 `LocalDate.now().atStartOfDay()` 到 `LocalDateTime.now()` 作为区间，装配 `todayDataVO`。想秀一下可以写单条 SQL 的 `SUM(IF(status=4, amount, 0))` + `COUNT(IF(status=1,1,null))` 版本，两种都留在笔记里。

### 2. 扩展接口（可选，时间富余再做）

- `GET /admin/report/turnoverStatistics`：按 begin/end 出每日营业额列表（`GROUP BY date(order_time)`），前端画趋势。
- `GET /admin/report/top10`：`select oi.drink_name, sum(oi.number) as saleCount from order_item oi join orders o on oi.order_id=o.id where o.status in (4) and o.order_time between ... group by oi.drink_name order by saleCount desc limit 10`——注意苍穹把 top10 建在明细表上，你也一样（orders 表没有商品维度）。

### 3. README 与部署

**后端打包**：

```bash
mvn clean package -DskipTests
java -jar shanyue-server/target/shanyue-server-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

**前端打包与 nginx 托管**（两个前端各自 `npm run build`，产物在 `dist/`）。核心是三件事：静态文件托管 + 接口反代 + WebSocket 反代：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 管理后台静态资源（如工程 vite base 配置为 /admin/）
    location /admin/ {
        alias /opt/shanyue/admin-dist/;
        try_files $uri $uri/ /admin/index.html;   # history 路由回退
    }

    # H5 静态资源
    location /h5/ {
        alias /opt/shanyue/h5-dist/;
        try_files $uri $uri/ /h5/index.html;
    }

    # 后端接口反代（前缀与 vite.config.ts 里 proxy 的配置保持一致）
    location /admin/api/  { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }
    location /user/       { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }
    location /upload/     { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }

    # WebSocket 反代
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;
    }
}
```

> 前缀以两个前端工程 `vite.config.ts` 的 `server.proxy` 实际配置为准（开发环境它们已把对应前缀代理到 8080，生产由 nginx 做同样的事）。开发期**不需要**在后端配任何 CORS。

### 4. 全项目 README（模板见仓库根 README.md，补上后端部分）

✍️ 把下面内容补进 README：技术栈一句话、架构图（可手绘上传）、数据库设计一段（9 张表关系：category 1-N drink 1-N spec_group 1-N spec_option；orders 1-N order_item）、启动方式、演示账号、功能截图。

## ✍️ 动手任务清单

- [ ] todayData（两种 SQL 写法试一遍，选一种上）
- [ ] （可选）turnoverStatistics / top10
- [ ] mvn 打包 + 本地 java -jar 启动成功
- [ ] 两个前端 build + nginx（或本地静态服务）跑通一条龙：浏览器打开 → 登录 → 下单 → 接单
- [ ] README 补全后端部分 + 截图

## ✅ 验收标准

1. admin 首页"今日数据"四个数字与手写 SQL 对账一致（营业额只算 status=4）。
2. `java -jar` 起服务，浏览器访问 nginx 代理后的页面全流程可用（登录→点单→接单→WebSocket 提醒）。
3. README 从零开始的人 10 分钟能把项目跑起来（让同学照着试一遍）。

## 💡 面试讲点

- **"营业额怎么算的？"** 只统计已完成订单（status=4），当日时间区间；说出"为什么不算待接单/已取消"就是业务理解。
- **"你的项目怎么部署？"** 前后端分离：静态资源 nginx、接口反代、ws 升级头、jar 常驻（可延伸 systemd/Docker 一句）。
- **"项目里你最得意的设计？"** 三个候选：结构化规格算价链路 / Redis 取餐号 / 缓存一致性处理——挑一个能讲五分钟的。

---

# 扩展任务卡 T9：优惠券与会员（3~4 天，简历业务增量亮点）

> 阶段 0~8 走完后可以做的**业务扩展**：优惠券是茶饮点单相对苍穹外卖最大的业务增量，也是面试里"你比课程项目多做了什么"的标准答案。前端（admin/h5 交付工程）已经全部实现并留好了契约，按本卡补后端即可联调；接口定义见 api-docs.md 模块 13（C 端 5 个）+ 模块 14（管理端券模板 5 个）。

## 为什么

- 苍穹外卖没有营销模块；点单系统的"领券 → 下单选券 → 核销"是一条完整、可讲深度的业务链路（状态机 + 事务 + 防超发）。
- 规模可控：两张表、五个只读+两个写接口，3~4 天能做完，性价比极高。

## 改什么

### 1. 两张表（放在阶段 1 DDL 之后补执行即可）

```sql
-- 券模板（管理端配置；本阶段可先用 SQL 手工插 3 条演示数据）
CREATE TABLE coupon (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(64)  NOT NULL COMMENT '券名称',
  type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 满减券 2 无门槛券',
  threshold   INT          NOT NULL DEFAULT 0 COMMENT '使用门槛（分），0 无门槛',
  discount    INT          NOT NULL COMMENT '抵扣金额（分）',
  valid_days  INT          NOT NULL DEFAULT 7 COMMENT '领取后有效天数',
  total_count INT          NOT NULL DEFAULT 0 COMMENT '发放总量',
  remain_count INT         NOT NULL DEFAULT 0 COMMENT '剩余可领数量',
  description VARCHAR(128) NOT NULL DEFAULT '' COMMENT '券说明',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架'
) COMMENT '优惠券模板';

-- 用户券（领取时生成；下单核销时回写 status 与 used_order_number）
CREATE TABLE user_coupon (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  coupon_id    BIGINT      NOT NULL COMMENT '模板 id',
  name         VARCHAR(64) NOT NULL COMMENT '券名称快照',
  type         TINYINT     NOT NULL,
  threshold    INT         NOT NULL DEFAULT 0,
  discount     INT         NOT NULL,
  receive_time DATETIME    NOT NULL,
  expire_time  DATETIME    NOT NULL,
  status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1 可用 2 已核销 3 已过期',
  used_order_number VARCHAR(32) NOT NULL DEFAULT '' COMMENT '核销订单号',
  INDEX idx_uc_status (status)
) COMMENT '用户优惠券';
```

### 2. 五个接口（对应 api-docs 模块 13）

- `GET /user/coupon/templates`：查 coupon 表 status=1，返回含 remainCount。
- `POST /user/coupon/receive/{templateId}`：核心是**防超发 + 限领一张**。更新剩余数量用乐观锁：
  `UPDATE coupon SET remain_count = remain_count - 1 WHERE id = ? AND remain_count > 0`，影响行数为 0 就是"已领完"；同一用户重复领取先查 user_coupon 存在性（或建唯一索引兜底）。
- `GET /user/coupon/list?status=`：查 user_coupon；读取时惰性修正过期（`expire_time < now` 且 status=1 的顺手改成 3，等价于懒版定时任务）。
- `GET /user/coupon/usable?amount=`：`status=1 AND expire_time > now AND amount >= threshold`，按 discount 降序。
- `GET /user/member/summary`：积分 = 已完成订单实付分/100 取整（一条 SUM 聚合）。

### 3. 管理端券模板接口（对应 api-docs 模块 14，CRUD 基础款）

- `GET /admin/coupon/page`：分页 + 名称模糊 + status 筛选（PageHelper，注意紧贴第一条查询）。
- `POST /admin/coupon` / `PUT /admin/coupon`：校验名称唯一、满减券门槛 ≥ 面额；修改时发放总量 ≥ 已领取张数（`total_count >= total_count - remain_count`）。
- `POST /admin/coupon/status/{status}`：上下架，只改 C 端券广场可见性，不影响已领取未核销的券。
- `DELETE /admin/coupon`：**已领取（total_count > remain_count）不可删**返回 code=0；生产可改为逻辑删除。

### 4. 下单核销（改造 11.1 submit，重点）

在原 submit 流程的「重算金额」步骤后插入：

1. 请求带了 userCouponId 才走核销分支；查 user_coupon 并校验：存在、status=1、未过期、订单商品总额 ≥ threshold；
2. 抵扣金额 discountAmount = min(券 discount, 商品总额)，实付 orderAmount = 商品总额 - discountAmount；
3. **同一事务（@Transactional）内**：插入订单 → `UPDATE user_coupon SET status = 2, used_order_number = ? WHERE id = ? AND status = 1`（影响行数为 0 说明被并发核销，抛异常回滚）；
4. OrderSubmitVO / OrderVO 增加 discountAmount 字段返回。

## ✍️ 动手清单

- [ ] 建 coupon / user_coupon 两表，插入 3 条演示模板（无门槛 ¥5 / 满25减4 / 满40减8）
- [ ] CouponController + CouponService + UserCouponMapper（含乐观锁扣减 SQL）
- [ ] 管理端券模板 5 接口（模块 14）：分页/新增/修改/上下架/删除，联调 admin 优惠券管理页
- [ ] submit 改造：校验 → 重算 → 同事务核销（重点写好并发注释）
- [ ] 联调：领券广场 → 我的券 → 结算页选券 → 提交后券变「已核销」→ 订单显示券抵扣
- [ ] （加分）用 JMeter/ab 并发压领券接口，验证 remain_count 不被扣成负数

## ✅ 验收标准（前端联调）

- h5 会员中心可领取每张模板（重复领取报"已领取过"），库存随领取递减；
- 结算页弹层只出现满足门槛的可用券，选券后合计 = 商品总额 - 券面额；
- 下单成功后：订单查询页显示"券抵扣 -¥x"，会员中心该券进入"历史券-已核销"并带订单号；
- admin 订单列表金额 = 实付（已扣券）；
- admin 优惠券管理页可增改券模板、下架后 h5 券广场不再展示、已领取的券删除接口返回 code=0 提示。

## 💡 面试讲点

- 防超发：数据库乐观锁 vs Redis DECR + 异步落库，各自适用场景；
- 核销并发：`UPDATE ... WHERE status = 1` 的条件更新天然幂等，配合事务回滚保证"订单和券同生共死"；
- 过期处理为什么用"读时惰性修正 + 定时任务兜底"双保险；
- 如果做秒杀券，还需要：Redis 预扣库存、消息队列削峰、接口限流（这是下一层的题，能说出边界即可）。

---

# 扩展任务卡 T10：积分体系（2~3 天，可与 T9 串联成「会员营销」大亮点）

> 前端（h5 会员中心）已全部实现并留好契约：签到日历 + 积分明细 + 积分兑换货架；接口定义见 api-docs.md 模块 15（C 端 5 个）。
> 积分账本三来源：**消费积分**（已完成订单实付/100）+ **签到积分**（每日 +7）- **兑换消耗**（积分兑券，定价 max(20, 面额/10)，即 1 积分 ≈ 0.1 元面额）。

## 为什么

- 单做「签到送积分」只是小功能；把「积分 → 优惠券 → 下单核销」串起来，就和 T9 形成完整营销闭环（获客→留存→复购），面试叙事完整度远超课程项目。
- 技术点多而不深：唯一键防重、乐观锁扣分、事务边界，都是入职后真实会碰的场景，性价比高。

## 改什么

### 1. 两张表（放在 T9 之后补执行）

```sql
-- 签到记录：(user_id, checkin_date) 唯一键是防重复签到的最后防线
CREATE TABLE checkin_record (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT     NOT NULL,
  checkin_date DATE       NOT NULL,
  points       INT        NOT NULL DEFAULT 7 COMMENT '签到奖励积分',
  create_time  DATETIME   NOT NULL,
  UNIQUE KEY uk_user_date (user_id, checkin_date)
) COMMENT '签到记录';

-- 积分流水（type：1 签到 / 2 消费 / 3 兑换；points 正负号区分获得/消耗）
CREATE TABLE point_record (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT     NOT NULL,
  type       TINYINT    NOT NULL,
  title      VARCHAR(64) NOT NULL,
  sub        VARCHAR(128) NOT NULL DEFAULT '',
  points     INT        NOT NULL COMMENT '变动积分，正负号',
  ref_id     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联单号/对象',
  create_time DATETIME  NOT NULL,
  INDEX idx_pr_user (user_id, create_time)
) COMMENT '积分流水';
```

> 积分余额不建单独表也可以（SUM 聚合流水），但高并发场景建议在 member 表加 points 冗余列 + 流水表做审计，两者用事务保持一致——面试可对比讲。

### 2. 五个接口（对应 api-docs 模块 15）

- `GET /user/point/summary`：余额 = SUM(points)；连签 streak 从今天（或昨天）往回逐日查 checkin_record 计数；响应带 streakBonusTotal（累计连签奖励）与 nextBonusIn（距下次奖励天数）。
- `POST /user/point/checkin`：业务层先查当日记录，`INSERT` 捕获 `DuplicateKeyException` 兑底返回「已签到」。**连签奖励（v1.5）**：签到成功后若 streak 恰为 7 的整数倍，同一事务内额外写一条 +20 奖励流水，并在返回概要中带 `justBonus: 20`（未触发为 0），前端据此播放庆祝动效；奖励发放可用 `(user_id, checkin_date, type)` 唯一键防重复。
- `GET /user/point/records`：分页流水倒序；消费类流水建议在订单完成事件中异步入账（本地消息表/事务消息），取消订单写 -对冲流水。
- `GET /user/point/exchanges`：复用 T9 的 coupon 表（status=1 且 remain_count>0），按定价公式生成 cost 字段。
- `POST /user/point/exchange/{templateId}`：**同一事务**三步：①乐观锁扣积分 `UPDATE member SET points = points - ? WHERE user_id = ? AND points >= ?`（影响 0 行 = 余额不足，抛异常回滚）；②写兑换流水；③复用 T9 领券的乐观锁扣券库存并发券。积分兑换不占「限领 1 张」名额（用户已付出对价）。

### 3. 会员概要口径对齐

T9 的 `GET /user/member/summary` 的 points 字段改为取积分账本余额（与 15.1 同源），h5 会员卡数字才会联动。

## ✍️ 动手清单

- [ ] 建 checkin_record / point_record 两表（注意唯一键）
- [ ] PointController + PointService 5 接口（模块 15）
- [ ] 订单完成事件写入消费积分流水（先同步写也可，注释留升级点）
- [ ] 兑换接口三步事务（扣分 → 流水 → 发券），并发注释写清楚
- [ ] member/summary 的 points 改同源
- [ ] 联调：签到（重复签报错）→ 明细出现 +7 → 兑换（余额不足报错）→ 我的券多一张 → 订单完成后余额增加

## ✅ 验收标准（前端联调）

- h5 会员中心签到后日历格高亮、连签天数/余额联动，重复签到 toast 提示已签；连签进度条按 streak % 7 点亮，签到后 streak 恰为 7 时弹出「连签达成」庆祝动效且余额额外 +20；
- 积分明细出现三类记录且倒序；下单并推进到「已完成」后消费积分到账；
- 兑换后：余额减少、库存 -1、我的优惠券多一张可用券，可正常下单核销；
- 余额不足时兑换按钮置灰并提示「差 N 分」，后端并发重复请求不会把积分扣成负数。

## 💡 面试讲点

- 防重复签到：唯一键兜底 vs Redis SETNX，各自的过期语义差异；
- 积分这种「数字资产」为什么用乐观锁而不是先查后改（丢失更新）；
- 流水表 vs 余额表：审计/对账/性能三个维度对比；
- 消费积分为什么建议「完成后入账」而不是下单即入（取消/退款对冲成本）；
- 连签奖励的可配置化：奖励规则（7 天 +20）抽成规则表/配置项 vs 硬编码，运营改规则要不要发版；

---

# 扩展任务卡 T11：评价体系（2~3 天，C 端 + 管理端两端联动，苍穹没有的亮点）

> 为什么做：外卖课程项目只有订单，没有「服务闭环」——顾客喝完就走，商家听不到声音。评价体系把 C 端（写评价/看口碑）与管理端（回评价/管内容）串成闭环，同时给门店头增加「门店评分」这个所有真实 App 都有的信任背书位。
> 数据模型（一张表搞定）：`order_review(id, order_id UNIQUE, order_number, pick_up_code, user_id, score, tags, content, reply, visible, create_time)`。
> tags 存固定枚举（口味赞/出餐快/服务贴心/性价比高/包装精美/环境好）的 JSON 数组，解析与校验在业务层；score 为 1~5 整数；visible 1 展示 / 0 隐藏。

## 实现要点

- **一单一评**：`order_id` 加唯一键兑底 + 业务层先查后插（DuplicateKeyException 捕获后返回「已评价过」）；
- **状态机校验**：只有 `status=4（已完成）` 可评，防止评价未完成/已取消订单（面试讲点：评价写入前要校验订单归属 + 状态，防横向越权）；
- **统计口径**：平均分/好评率/星级分布/标签热度一律排除 `visible=0`；好评率 = 4/5 星占比；「待回复」= score ≤ 3 且 reply 为空（管理端仪表盘同口径）；
- **隐藏 ≠ 删除**：管理端隐藏用 visible 软删留痕，C 端评价墙与统计读同一条件；面试可延伸到「先审后显」与敏感词 DFA 过滤；
- **评分统计加缓存**（建议）：写入/回复/隐藏/恢复时失效 `cache:review:stats`，Redis 不可用降级直查。

## ✍️ 动手清单

- [ ] DDL：order_review 建表（order_id 唯一键 + user_id 普通索引 + create_time 索引）
- [ ] C 端 4 接口（POST /user/review、GET /user/review/order/{orderId}、GET /user/review/stats、GET /user/review/mine）
- [ ] 管理端 4 接口（GET /admin/review/page、GET /admin/review/stats、PUT /admin/review/reply、PUT /admin/review/visible/{visible}）
- [ ] 提交评价事务：校验订单状态 → 插入评价（捕获唯一键冲突）；文字评价过敏感词过滤（可选加分项）
- [ ] 联调：完成订单 → 评价（重复评报错）→ 管理端回复 → C 端我的评价看到回复 → 管理端隐藏 → C 端门店评分变化

## ✅ 验收标准（前端联调）

- h5 查询页：已完成订单出现「评价」按钮 → 星级+标签+文字提交成功 → 变「已评价 ★ 5.0」徽标，再次查询不再出现评价入口；
- h5 首页门店头出现「★ 4.8」评分徽标；门店信息页评价总览/标签热度/评价墙与统计接口一致；会员中心「我的评价」能看到商家回复；
- admin 评价管理页：搜索/星级/展示状态筛选正确；回复后「待回复」数 -1；隐藏后总数/平均分/好评率联动变化（隐藏一条 5 星会拉低平均分）。

## 💡 面试讲点

- 一单一评的唯一键兑底 vs 分布式锁，各自的适用场景；
- 评分统计的缓存与失效策略：写失效 + 回源重建 vs 定时聚合，隐藏/恢复对统计口径的影响；
- 评价对经营的价值闭环：低分待回复提醒（工作台待办）→ 商家回复 → 口碑提升，如何用数据验证；
- 为什么 tags 用 JSON 数组而不是三张关联表（标签是运营位、查询模式是整包读写 vs 多维分析）。

---

# 扩展任务卡 T12：月售动态统计（1~2 天，v2.0 新增，可与 T9~T11 并行）

> 小程序菜单每个商品卡上有「月售 xxx」徽标。v2.0 起 `monthSales` 是**服务端动态统计**字段（DrinkVO.monthSales，骨架已预留），不再是手工维护的数字——数据能随下单"活"起来，验收时肉眼可见增长。

## 为什么

- 真实点单 App 的"月售"是信任背书位（大众点评/美团同款），也是"哪些该补货/下架"的运营信号；
- 这是全项目**第一个统计需求**：一次 JOIN + SUM + GROUP BY，是阶段 8 报表与面试"聚合怎么优化"的最小练手场；
- 工作量极小（一条聚合 SQL + 组装 VO），性价比拉满。

## 改什么

### 1. 两种方案对比（先想清楚再动手）

| 方案 | 做法 | 优点 | 缺点 | 适用 |
|---|---|---|---|---|
| A 实时 SUM（**推荐**） | 查询时 JOIN order_detail + orders 聚合 | 零冗余、永远准确、无一致性维护成本 | 每次列表查询多一次聚合（可加缓存） | 订单量中小、课程项目 |
| B 冗余字段 | drink 表加 month_sales 列，订单完成时 +number | 查询零成本 | 防并发丢更新、需定时校准、刷单/清库会失真 | 订单量极大、列表 QPS 敏感 |

面试标准答法：先用 A 验证业务，QPS 上来再叠加"短 TTL 缓存 + 定时校准"把读压力挡在 Redis；B 的冗余本质是**用一致性换读性能**，与 T10 积分"余额表 vs 流水表"是同一个权衡。

### 2. 参考口径与 SQL

月售 = 已完成订单的明细数量之和。先和产品定口径（全历史 vs 近 30 天），再写 SQL：

```sql
-- 全历史口径：status=5 在骨架 6 态口径中表示「已完成」
-- （若沿用本指引 5 态口径，已完成是 4——写 SQL 前先定口径，团队内统一）
SELECT od.drink_id, IFNULL(SUM(od.number), 0) AS month_sales
FROM order_detail od
JOIN orders o ON od.order_id = o.id AND o.status = 5
GROUP BY od.drink_id;

-- 近 30 天口径（真实产品语义，推荐）：在上面的 JOIN 条件后加
--   WHERE o.order_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
```

- 实现：`OrderDetailMapper.sumMonthSales()` 返回 `List<DrinkSalesVO>`（骨架已有该 VO），Service 组装成 `Map<drinkId, sales>` 塞进阶段 5 的饮品列表 VO（没有订单的菜品补 0）；
- **缓存策略（可选）**：统计结果存 Redis `key=shanyue:monthSales, TTL=60s`——"榜单类"数据容忍分钟级延迟，短 TTL 缓存即可，不需要订阅式失效；管理端改价/停售不影响该值（统计的是订单事实）；缓存挂了降级直查（try-catch，附录 B 坑 10）。

## ✍️ 动手清单

- [ ] OrderDetailMapper 加聚合 SQL（推荐近 30 天口径，注释写清业务约定与 status 口径）
- [ ] DrinkVO.monthSales 组装进 /user/drink/list（无订单的菜品补 0）
- [ ] （可选）Redis 60s 缓存 + 降级直查
- [ ] （加分）写 10 行压测脚本对比加缓存前后的列表接口耗时

## ✅ 验收标准（前端联调）

- 小程序菜单每个商品卡显示「月售 N」；下一单推进到已完成（或把测试订单 status 手工改成已完成）后，刷新菜单 N 增长；
- 没有订单的新品显示「月售 0」而不是空/NaN；管理端停售不改变月售数字；
- 开缓存时 60 秒内重复刷新命中缓存（后端日志可证），不出现统计错数。

## 💡 面试讲点

- 实时聚合 vs 冗余字段：一致性 / 复杂度 / 读放大三个维度怎么权衡；
- 短 TTL 缓存为什么适配"榜单类"统计（容忍分钟级延迟 + 防穿透 + 降级兜底）；
- 大表聚合优化路径：where 走索引（order_time + status）→ 覆盖索引 → 预聚合表/数仓，能说出第一层就够。

---

# 附录 A：前端联调总核对清单（v2.0 · 全部 81 个接口 × 对应页面）

> 用法：每联调通一个，就在前面打勾。标注「扩展」的为阶段 8 / T9 / T10 / T11 / T12 可选。页面名以前端工程实际页面为准（C 端以小程序 shanyue-mp 为准，h5 为 v1.6 口径可对照）。
> 计数口径：管理端 42 + C 端 25 + C 端扩展 14 = **81**，与 `docs/api-docs.md` v2.0 接口清单一一对应（v1.6 的 67 个中，C 端 11 个按微信登录契约扩为 25 个：新增登录/资料/地址簿 9 个与订单详情/历史/两步支付/再来一单等）；接口定义以 api-docs.md 为准。

## 管理端（42 个）

| # | 接口 | 页面/位置 | 阶段 |
|---|---|---|---|
| ☐ | POST /admin/employee/login | 登录页 | 2 |
| ☐ | POST /admin/employee/logout | 顶栏头像菜单 | 2 |
| ☐ | POST /admin/employee | 员工管理-新增弹窗 | 2 |
| ☐ | GET /admin/employee/page | 员工管理-列表 | 2 |
| ☐ | GET /admin/employee/{id} | 员工管理-编辑回显 | 2 |
| ☐ | PUT /admin/employee | 员工管理-编辑保存 | 2 |
| ☐ | POST /admin/employee/status/{status} | 员工管理-启用/禁用 | 2 |
| ☐ | POST /admin/category | 分类管理-新增 | 2 |
| ☐ | GET /admin/category/page | 分类管理-列表 | 2 |
| ☐ | DELETE /admin/category | 分类管理-删除 | 2 |
| ☐ | PUT /admin/category | 分类管理-编辑 | 2 |
| ☐ | POST /admin/category/status/{status} | 分类管理-启用/禁用 | 2 |
| ☐ | GET /admin/drink/page | 饮品管理-列表 | 3 |
| ☐ | POST /admin/drink | 饮品管理-新增（含规格树） | 3 |
| ☐ | GET /admin/drink/{id} | 饮品编辑页回显 | 3 |
| ☐ | PUT /admin/drink | 饮品编辑保存 | 3 |
| ☐ | DELETE /admin/drink?ids= | 饮品管理-批量删除 | 3 |
| ☐ | POST /admin/drink/status/{status} | 饮品管理-起售/停售 | 3 |
| ☐ | POST /admin/common/upload | 饮品新增/编辑-传图 | 3 |
| ☐ | GET /admin/order/conditionSearch | 订单管理-列表+筛选 | 4 |
| ☐ | GET /admin/order/statistics | 订单管理-顶部 tab | 4 |
| ☐ | GET /admin/order/details/{id} | 订单详情弹窗 | 4 |
| ☐ | PUT /admin/order/confirm | 订单-接单按钮 | 4 |
| ☐ | PUT /admin/order/ready | 订单-出餐叫号按钮 | 4 |
| ☐ | PUT /admin/order/cancel | 订单-取消（带原因） | 4 |
| ☐ | PUT /admin/order/complete | 订单-完成按钮 | 4 |
| ☐ | GET /admin/report/todayData | 工作台-今日数据 | 8 |
| ☐ | GET /admin/report/turnoverStatistics | 报表-营业额趋势（扩展） | 8 |
| ☐ | GET /admin/report/top10 | 报表-销量TOP10（扩展） | 8 |
| ☐ | GET /admin/report/ordersStatistics | 报表-订单统计（扩展） | 8 |
| ☐ | GET /admin/shop/status | 顶栏店铺徽标/店铺页 | 2 |
| ☐ | PUT /admin/shop/status/{status} | 店铺设置-营业开关 | 2 |
| ☐ | PUT /admin/shop/notice | 店铺设置-公告 | 2 |
| ☐ | GET /admin/coupon/page | 优惠券管理-列表+筛选（扩展） | T9 |
| ☐ | POST /admin/coupon | 优惠券管理-新增弹窗（扩展） | T9 |
| ☐ | PUT /admin/coupon | 优惠券管理-编辑保存（扩展） | T9 |
| ☐ | POST /admin/coupon/status/{status} | 优惠券管理-上/下架开关（扩展） | T9 |
| ☐ | DELETE /admin/coupon | 优惠券管理-删除（已领取不可删）（扩展） | T9 |
| ☐ | GET /admin/review/page | 评价管理-列表+筛选（扩展） | T11 |
| ☐ | GET /admin/review/stats | 评价管理-总览卡+仪表盘口碑卡（扩展） | T11 |
| ☐ | PUT /admin/review/reply | 评价管理-回复弹窗（扩展） | T11 |
| ☐ | PUT /admin/review/visible/{visible} | 评价管理-隐藏/恢复（扩展） | T11 |

## C 端（25 个，v2.0 微信登录版）

| # | 接口 | 页面/位置 | 阶段 |
|---|---|---|---|
| ☐ | POST /user/user/login | 小程序登录页（wx.login code 换 token） | 6.5 |
| ☐ | GET /user/user/profile | 我的-用户卡（昵称/头像/积分缓存回写） | 6.5 |
| ☐ | GET /user/shop/status | 首页门店条（打烊遮罩） | 2 |
| ☐ | GET /user/category/list | 菜单分类侧栏 | 2 |
| ☐ | GET /user/drink/list | 菜单商品列表（含规格树 + monthSales） | 5 |
| ☐ | GET /user/setmeal/list | 套餐列表（预留） | 5 |
| ☐ | POST /user/shoppingCart/add | 规格弹窗-加入购物车 | 5 |
| ☐ | GET /user/shoppingCart/list | 购物车浮层 | 5 |
| ☐ | POST /user/shoppingCart/sub | 购物车 -1 | 5 |
| ☐ | DELETE /user/shoppingCart/clean | 购物车清空 | 5 |
| ☐ | POST /user/order/submit | 结算-提交订单 | 6 |
| ☐ | PUT /user/order/payment | 结算-支付第一步 | 6 |
| ☐ | PUT /user/order/paySuccess | 支付第二步（状态推进+取餐号） | 6 |
| ☐ | GET /user/order/historyOrders | 订单列表（状态 tab + 分页） | 6 |
| ☐ | GET /user/order/orderDetail/{id} | 订单详情 | 6 |
| ☐ | PUT /user/order/cancel/{id} | 订单详情-取消 | 6 |
| ☐ | PUT /user/order/reminder/{id} | 订单详情-催单 | 6 |
| ☐ | POST /user/order/repetition/{id} | 订单详情-再来一单 | 6 |
| ☐ | GET /user/addressBook/list | 地址列表页 | 6.5 |
| ☐ | GET /user/addressBook/{id} | 地址编辑回显 | 6.5 |
| ☐ | POST /user/addressBook | 新增地址 | 6.5 |
| ☐ | PUT /user/addressBook | 编辑地址 | 6.5 |
| ☐ | DELETE /user/addressBook?id= | 删除地址（query 传 id） | 6.5 |
| ☐ | GET /user/addressBook/default | 结算页默认地址 | 6.5 |
| ☐ | PUT /user/addressBook/default | 设为默认地址（body 传 id） | 6.5 |

> v1.6 历史口径：原 C 端 11 个（`GET /user/drink/list`、`GET /user/order/byPickUpCode/{code}` 等）为 h5 匿名版接口，v2.0 起以小程序契约为准；h5 未迁移前仍可对照联调（接口定义见 api-docs.md v2.0）。

## C 端扩展（14 个：模块 13 优惠券与会员 + 模块 15 积分 + 模块 16 评价）

| # | 接口 | 页面/位置 | 阶段 |
|---|---|---|---|
| ☐ | GET /user/coupon/templates | 会员中心-领券广场 | T9 |
| ☐ | POST /user/coupon/receive/{templateId} | 会员中心-领取按钮 | T9 |
| ☐ | GET /user/coupon/list | 会员中心-我的优惠券 | T9 |
| ☐ | GET /user/coupon/usable?amount= | 结算页-选券弹层 | T9 |
| ☐ | GET /user/member/summary | 会员中心-会员卡统计 | T9 |
| ☐ | GET /user/point/summary | 会员中心-签到卡/余额 | T10 |
| ☐ | POST /user/point/checkin | 会员中心-签到按钮 | T10 |
| ☐ | GET /user/point/records | 会员中心-积分明细 | T10 |
| ☐ | GET /user/point/exchanges | 会员中心-兑换货架 | T10 |
| ☐ | POST /user/point/exchange/{templateId} | 会员中心-兑换按钮 | T10 |
| ☐ | POST /user/review | 查询页-评价弹层 | T11 |
| ☐ | GET /user/review/order/{orderId} | 查询页-已评价徽标/评价按钮 | T11 |
| ☐ | GET /user/review/stats | h5 首页评分徽标+门店信息页评价总览 | T11 |
| ☐ | GET /user/review/mine | 会员中心-我的评价 | T11 |

另外 2 个非 HTTP 联调点：WebSocket 连接（admin 首页建立）与三种消息（NEW_ORDER/REMINDER/READY_CALL）——阶段 7 验收。

---

# 附录 B：常见联调坑（踩坑前先看一遍）

1. **CORS 不用管**。开发期两个前端都用 vite proxy 把 `/admin`、`/user`、`/ws`、`/upload` 等前缀转发到 8080（同源转发，浏览器根本不知道后端存在）；生产期 nginx 同域反代。**不要在后端加 `@CrossOrigin` 或 CorsFilter**，加了反而画蛇添足。
2. **401 三连查**：① `VITE_USE_MOCK` 是否被设成了 `true`（mock 模式根本不发请求或 token 是假的；v1.5.2 起默认 false）；② 重新登录过没有（改了 JwtProperties 密钥/过期时间旧 token 失效）；③ 后端 `admin-token-name` 必须是 `token`（接口文档约定），且 `/admin/employee/login` 在拦截器 excludePathPatterns 里。
3. **mock 开关/数据残留**：设过 `VITE_USE_MOCK=true` 的要删掉并重启 vite；localStorage 里 mock 数据和后端数据是两套，「重置演示数据」按钮仅 mock 模式可见，联调以 Network 面板有没有真实请求为准。
4. **金额分/元不一致**：后端全链路 int 分（1590），前端展示除以 100。如果你手滑把某处写成 BigDecimal 或在 SQL 里 `price/100`，前端就会差 100 倍。排查口诀：所有 amount/price/delta 字段，库里存的必须是分。
5. **时间格式缺秒**：苍穹 `JacksonObjectMapper` 默认 `yyyy-MM-dd HH:mm`，务必改成 `yyyy-MM-dd HH:mm:ss`（阶段 0）。否则订单时间筛选/展示都对不齐。
6. **Result 结构**：任何接口失败都必须 `code=0` + 中文 msg（前端 toast 直接展示 msg）。自定义异常要继承 `BaseException` 才会被 GlobalExceptionHandler 接住；别忘了 `SQLIntegrityConstraintViolationException` 的 Duplicate entry 处理（用户名重复）。
7. **图片 404**：mock 相对路径 `/menu/xxx.png` 由**前端**托管（public 目录）；后端上传图是 `http://localhost:8080/upload/...` 由**后端**托管。列表里混着两种图都正常，但如果你手写 SQL 把 mock 相对路径存进库、又用 Postman 直接开后端 URL 看图，就会 404——图跟域名走。
8. **PageHelper 陷阱**：`PageHelper.startPage(...)` 之后必须紧跟第一条查询语句，中间插别的查询会分页错乱；返回的 records 字段名是 `records` 不是 `list`。
9. **specKey 格式漂移**：specKey 只能由服务端生成（阶段 5），如果你在 postman 里手拼一个格式不同的 key 去 sub，匹配不到行不是 bug，是 key 不合法。
10. **Redis 没启动**：启动报 `Unable to connect to Redis` 或用店铺状态/缓存/取餐号时报错——先 `redis-server`/服务里启动 Redis；同时把读缓存包进 try-catch 降级（阶段 7）。
11. **WebSocket 连不上**：确认 `WebSocketConfiguration` 注册了 `ServerEndpointExporter`；浏览器 ws 地址是 `ws://localhost:8080/ws/shanyue`；vite proxy 的 ws 转发要带 `ws: true`（前端工程已配好）。
12. **改了 DDL 忘了改 XML**：drink 表字段和 DrinkMapper.xml 的 insert 列名对不上时报 SQL 异常——改表、改实体、改 XML 三件套永远一起动。

---

# 附录 C：nginx 部署与前后端联调（v2.0 新增）

> 目标：让 admin 管理端、小程序、后端按"真实部署形态"跑通——前端不再依赖 vite dev server，而是 nginx 托管静态页面 + 反向代理接口。

## C.1 为什么用 nginx（与课程部署模式一致）

- **静态托管**：`npm run build` 产出的 dist 是纯静态文件，nginx 托管即可，不再需要 node 进程；
- **反向代理**：admin 前端所有请求统一 `/api/**`，nginx 把 `/api` 重写为 `/admin` 转发 8080——与开发期 vite proxy 完全同款规则，前端代码零改动（这也是框架精讲 3.6 节说的"拦截器配 /admin/**、前端写 /api 的翻译层"）；
- **同源消灭跨域**：页面与接口同域，浏览器根本不发预检（附录 B 坑 1 在部署期的正确答案）；
- **扩展位**：gzip、静态资源缓存、负载均衡都留在 nginx 层，简历讲部署有得说。

## C.2 Windows 手把手（10 分钟）

交付材料已备好 Windows 版 nginx（`deliverable/_assets/nginx-1.20.2.zip`），解压即用：

1. 解压到一个**路径不含中文与空格**的目录，如 `D:\\nginx-1.20.2`；
2. 编辑 `conf/nginx.conf`，把 `http {}` 里的默认 `server {}` 换成下面样例（改完 `nginx -t` 验语法）：

```nginx
server {
    listen       80;                # 浏览器访问 http://localhost
    server_name  localhost;

    # ① 管理端静态页面：npm run build 的 dist/* 拷到 html/admin
    location / {
        root   html/admin;
        index  index.html;
        try_files $uri $uri/ /index.html;   # Vue history 路由刷新不 404
    }

    # ② 接口反代：/api/xxx → /admin/xxx（与 vite proxy 同款映射）
    location /api/ {
        proxy_pass http://localhost:8080/admin/;
    }

    # ③ C 端接口与上传图片（h5 部署时用；小程序开发期直连 8080，不经这里）
    location /user/ {
        proxy_pass http://localhost:8080;
    }
    location /upload/ {
        proxy_pass http://localhost:8080;
    }

    # ④ WebSocket：升级协议头必须带，否则 ws 连不上
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

3. 把 admin 前端 `dist/*` 拷到 `nginx-1.20.2/html/admin/`；
4. cmd 进入 nginx 目录执行 `start nginx`，浏览器访问 `http://localhost` → 出现管理端登录页（后端 8080 与 MySQL/Redis 照常先启动）；
5. 常用命令：`nginx -t`（语法检查）、`nginx -s reload`（改配置重载）、`nginx -s stop`（停止）。

## C.3 小程序接入：baseUrl 配置与「不校验合法域名」

1. 开发期直连后端：`deliverable/shanyue-mp/utils/config.js` 里 `BASE_URL: 'http://localhost:8080'`、`USE_MOCK: false`；
2. 真机预览：`BASE_URL` 改成电脑局域网 IP（Windows `ipconfig` 查 IPv4），手机与电脑同一 WiFi，后端防火墙放行 8080；
3. 微信开发者工具 → 详情 → 本地设置 → 勾选**「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**（否则 localhost 与 http 请求直接被拦）；
4. 登录走 `wx.login()` 取 code → `POST /user/user/login`（无真实 appid 时后端 mock 模式照跑，见阶段 6.5）。

## C.4 联调排错 10 条

| # | 症状 | 原因 | 解决 |
|---|---|---|---|
| 1 | 401 循环跳登录 | token 没带上 / 登录接口没放行 / 换密钥后旧 token 失效 | F12 Network 看请求头 token；对照拦截器 excludePathPatterns；重新登录（附录 B 坑 2） |
| 2 | CORS 报错 | 绕过 nginx 直访 8080，或 proxy_pass 没生效 | 走 nginx 同域反代；别在后端加 @CrossOrigin（附录 B 坑 1） |
| 3 | `/api/xxx` 全部 404 | proxy_pass 结尾斜杠规则没吃透 | `location /api/` + `proxy_pass http://localhost:8080/admin/;`——两边都带斜杠 = 替换前缀；都不带 = 原样拼接（会变成 /api/admin/...） |
| 4 | 图片 404 | 上传图由 8080 的 /upload/** 静态映射托管 | 核对 WebMvcConfiguration 的 addResourceHandlers 与磁盘路径一致；nginx 加 /upload/ 透传（附录 B 坑 7） |
| 5 | ws 连不上 | nginx 没带协议升级头 | location /ws/ 加 proxy_http_version 1.1 + Upgrade/Connection 头（见 C.2 ④） |
| 6 | 用一会儿突然 401 | token 过期（admin 2h / user 3 天） | 重新登录；联调期可临时调大 yml 的 ttl |
| 7 | 80 端口被占用 | IIS/其它进程占了 80 | `netstat -ano | findstr :80` 找 PID 结束；或 listen 8081 换端口（前端访问地址同步改） |
| 8 | 时间差 8 小时 | JDBC serverTimezone 缺失 | url 带 serverTimezone=Asia/Shanghai（附录 B 坑 5） |
| 9 | 店铺状态/取餐号报错 | Redis 没启动 | `redis-cli ping` 先通（附录 B 坑 10） |
| 10 | 415 Unsupported Media Type | JSON 接口没带 application/json，或文件上传没用表单 | JSON 用 wx.request/axios 默认头；上传必须 multipart/form-data（小程序用 wx.uploadFile） |

---

# 附录 D：简历项目描述模板（两段式）

> 按你实际完成的内容增删，不要写自己讲不出来的技术点；每条亮点都要有"问题→方案→结果"。（v2.0 注：原附录 C 顺延为附录 D，新增附录 C 为 nginx 部署与前后端联调；内容未动。）

**第一段（业务 + 技术概览）：**

> v2.0 注：C 端已改为微信登录模式，见阶段 6.5——第一段建议把「C 端匿名下单」更新为「JWT 双端（管理端员工 / C 端微信用户）鉴权」，接口数按 api-docs v2.0 口径写 81 个。

> **山月茶咖 —— 茶饮自取点单系统**（个人全栈项目）
> 基于苍穹外卖工程架构改造的茶饮自取点单系统，包含商家管理后台（Vue3 + Element Plus）与顾客 H5 点单端（Vue3 + Vant）。后端采用 Spring Boot + MyBatis + MySQL + Redis + WebSocket，JWT 管理端鉴权、C 端匿名下单。我独立完成了从业务建模（12 张表：饮品-规格组-规格选项三层数据结构、订单-明细快照、券-积分-评价营销与口碑账本）到接口开发（67 个）、前端联调与部署的全流程。

**第二段（亮点，挑 3~5 条，量化）：**

> - 设计**结构化规格体系**（spec_group/spec_option 两表 + 树形 DTO），支持杯型/温度/糖度/浓度任意组合与加价，订单金额一律服务端按选项重算，杜绝前端篡改；规格修改采用「先删后插 + 事务整体覆盖」，规避多表 diff 的一致性风险。
> - 实现按天自增的**取餐号系统**，对比 MySQL max+1、序列表与 Redis INCR 三种方案，选用 Redis 原子自增（key 含日期自然按天重置 + 25h 过期防堆积），同一计数器同源生成订单号，保证两号一致。
> - 引入 **Redis 缓存** C 端菜单（读多写少场景），管理端增删改/起售停售统一清理缓存保障一致性，并做缓存异常降级与空值防穿透设计。
> - 基于 **WebSocket** 实现来单提醒、顾客催单、出餐叫号三类服务端主动推送；订单超时取消从 Spring Task 轮询升级为 **RabbitMQ TTL + 死信队列** 延迟消息，消费端二次校验订单状态避免误取消。
> - 设计**优惠券营销链路**（券模板/用户券两表 + 乐观锁防超发 + 下单同事务核销），支持无门槛/满减两类型与按天过期惰性修正，实付金额一律服务端重算。
> - 设计**积分体系**（签到/消费/兑换三源账本 + 流水表审计），每日签到用 (user_id, date) 唯一键防重，积分兑券与发券同事务并用乐观锁防并发透支，与优惠券构成「获客-留存-复购」完整营销闭环。
> - 设计**订单评价体系**（order_review 一表 + order_id 唯一约束实现一单一评），订单状态机校验防越权评价，隐藏软删留痕 + 统计口径一致性保障，低分评价自动进入商家待办形成口碑运营闭环。
> - 完成前后端分离部署（nginx 静态托管 + 接口反代 + WebSocket 升级），编写部署文档与联调核对清单（67 个接口全通过）。

---

## 结语

8 个阶段走完，你手里的不再是一个"课程项目"，而是一个**有明确业务动机、有技术取舍、有量化结果**的完整作品。每一处"为什么这么设计"，本文档都给了你思考的起点——把答案变成自己的话，才是这份指引真正的交付物。

山月入杯，茶咖相宜。开工吧。
