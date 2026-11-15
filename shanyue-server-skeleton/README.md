# 山月茶咖 · 后端基础框架（shanyue-server-skeleton）

> 苍穹外卖（sky-take-out）改造版「山月茶咖」的 Java 后端。
> **代码形态分两类**：① 主链路（员工/分类/饮品/套餐/订单/报表…）为「苍穹对照完整实现」，
> 饮品类命名全链统一为 Drink 系（表名 drink、URL /drink、类名 Drink*/DrinkDTO/DrinkVO 等），
> 方便按「苍穹菜品 ↔ 山月饮品」的映射逐行对照课程，表名与接口路径统一为 drink 口径，可直接编译启动；
> ② v2 功能包（F1 横幅 / F2 会员 / F4 券包 / F5 签到 / F6 评价 / F7 AI 点单）为「接口桩」——
> 返回与种子一致的演示数据，类内 TODO 注释含替换为真实现的步骤与黑马章节映射（练手区）。
> 前端工程（shanyue-vue3 管理端 + h5）与接口文档见交付包，联调只需把 baseUrl 指向本服务。

---

## 一、环境准备

| 软件 | 版本要求 | 说明 |
|---|---|---|
| JDK | **17** | 父 pom 已锁 `java.version=17`，装 JDK 8 会编译失败 |
| Maven | **3.8+** | IDEA 自带的 Bundled Maven 也可以 |
| MySQL | **8.x** | 5.7 也能跑（utf8mb4 兼容），但建议 8.0 |
| Redis | 5.x / 6.x / 7.x | 店铺营业状态、菜单缓存、取餐号自增都依赖它，**必须先启动** |
| IDEA | 2021.3+ | **务必安装 Lombok 插件**（新版 IDEA 内置），并开启 `Settings → Build → Compiler → Annotation Processors → Enable annotation processing` |

## 二、导入聚合工程

1. IDEA → `File → Open` → 选中 **shanyue-server-skeleton 根目录**（有父 pom.xml 的那层），OK。
2. 首次打开弹窗问 **Trust Project？→ Trust**（Maven 需要执行脚本权限）。
3. 右下角等待 Maven 同步完成；没有自动同步就点右侧 Maven 面板的 🔄 **Reload All Maven Projects**。
   - 同步成功的标志：左侧出现 `shanyue / shanyue-common / shanyue-pojo / shanyue-server` 四个模块，且外部库里有 spring-boot 2.7.6。
4. 首次同步会下载大量依赖，耐心等（国内网络建议配置阿里云镜像：`~/.m2/settings.xml` 加 mirror `https://maven.aliyun.com/repository/public`）。

## 三、建库

```sql
CREATE DATABASE shanyue CHARACTER SET utf8mb4;
```

用命令行（`mysql -uroot -p`）或 Navicat/DataGrip 都可以。库名必须是 `shanyue`（与 `application-dev.yml` 的 url 一致），字符集 utf8mb4 支持 emoji 与生僻字。

## 四、执行 SQL（顺序不能反）

1. 先执行 `sql/schema.sql` —— 建 13 张表（员工/分类/饮品/规格组/规格选项/口味/套餐/套餐饮品/用户/地址簿/购物车/订单/订单明细）；
2. 再执行 `sql/data.sql` —— 写入种子数据：管理员 **admin / 123456**、4 个分类、8 款饮品、11 组规格、2 个套餐、演示用户 1 名（占位 openid，仅防空表；真实登录由「扫码 → code 换 openid → 自动注册」产生新行）；
3. 可选执行 `sql/v2_features.sql` —— v2 功能包建表与种子（banner / coupon / user_coupon / review / checkin_log 五张新表 + user 表加 growth/points 字段；
   每张表头标注对应功能包编号，与控制器桩的 TODO 三方对齐）。不执行也能跑主链路，但 v2 功能联调前必须执行。

命令行方式：

```bash
mysql -uroot -p
USE shanyue;
SOURCE /你的路径/schema.sql;
SOURCE /你的路径/data.sql;
```

## 五、改配置（application-dev.yml）

打开 `shanyue-server/src/main/resources/application-dev.yml`，必改 1 处：

```yaml
spring:
  datasource:
    password: root   # ⭐ 改成你自己的 MySQL 密码
```

按需修改：
- `shanyue.wechat.appid/secret`：填成你自己的小程序凭据（公众平台 → 开发管理 → 开发设置；AppSecret 只在生成时显示一次）。必须与开发者工具里项目登录的 AppID 一致（三方一致），不一致微信返回 40013 invalid appid；本机公网 IP 要加进后台「IP 白名单」，否则 40164。注册实操见教程第 15 章；
- `shanyue.local-storage.path`：图片上传目录，Windows 默认 `D:/shanyue/upload`，Mac/Linux 改成如 `/Users/你的用户名/shanyue/upload`；
- `shanyue.jwt.*-secret-key`：默认给了占位长串，可自行更换（不要提交到公开仓库）。

### 预置菜品图片（不复制不影响功能，只影响菜单展示效果）

`data.sql` 预置的 8 个饮品 + 2 个套餐引用了交付包根目录 `../upload-assets/` 里的 10 张图片，
把它们**全部复制**到上面 `local-storage.path` 指向的目录（如 `D:/shanyue/upload`）即可：

- 数据库存的是相对路径 `/upload/latte.png`，后端把 `/upload/**` 映射到该目录，小程序拼上后端地址加载；
- 没复制时菜单显示绿色首字占位（图片加载失败自动兜底），不影响任何功能；
- 详细说明见 `../upload-assets/README.md`。

## 六、启动

运行 `shanyue-server` 模块下的 `com.shanyue.ShanyueApplication` 的 main 方法，看到：

```
山月茶咖后端启动成功：http://localhost:8080
接口文档：http://localhost:8080/swagger-ui/index.html
```

> 接口契约离线文件：根目录 `openapi.json`（OpenAPI 3.0.3，含全部 96 个接口、请求/响应字段级 schema），
> 可直接导入 Apifox（项目设置 → 数据导入 → OpenAPI/Swagger → 选该文件）；
> 服务启动后也可用在线地址 `http://localhost:8080/v3/api-docs` 导入，两者内容一致。

> 说明：本工程已含双端 JWT 拦截器与全量主链路业务代码，可直接编译启动（无需先写任何类）。
> 若你正跟着《后端手把手教程》逐章自写：可对照本工程逐行校对；v2 功能包（带 TODO 注释的桩）
> 是留给你替换为真实现的练手区，桩阶段返回种子数据，前后端可先行联调跑通全流程。
> **把桩换成真实现的手把手教程见 `docs/新增功能教学文档.md`**（七步法 + 6 个功能包
> 逐个展开的代码骨架/验收 curl/常见坑 + 改名对照表）。

## 七、验证（写完登录模块后自测）

**前置条件**：无需前置 —— 主链路业务代码已含苍穹对照完整实现，可直接启动自测。

```bash
# 1. 管理端登录（种子账号 admin/123456，密码错误会返回 code=0）
curl -X POST http://localhost:8080/admin/employee/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
# 期望：{"code":1,"msg":null,"data":{"id":1,"userName":"admin","name":"山月店长","token":"eyJ..."}}

# 2. C 端登录（真实链路：code 必须是小程序 wx.login() 拿到的临时凭证，
#    不能凭空编造——Apifox 直接调会返回登录失败，这是预期行为；
#    完整「注册小程序 → 配 appid/secret → 开发者工具扫码实测」见教程第 15 章）
curl -X POST http://localhost:8080/user/user/login \
  -H "Content-Type: application/json" \
  -d '{"code":"wx.login()拿到的真实code"}'
# 期望：{"code":1,"data":{"id":新用户id,"openid":"oXXXX...","token":"eyJ...",...}}
# 首次登录的用户会自动写入 user 表（SELECT * FROM user 查看新行）
```

登录成功后，后续管理端请求都带请求头 `token: <登录返回的令牌>`（双端请求头名都配置为 `token`，密钥不同、令牌不通用）。

## 八、工程结构说明

| 模块 | 包 | 职责 | 状态 |
|---|---|---|---|
| **shanyue**（父） | — | 聚合 + 版本管理（Spring Boot 2.7.6 / Java 17 / jjwt 0.9.1 / springdoc 1.7.0） | ✅ |
| **shanyue-common** | `constant` | 提示语/状态/JWT claims 等常量（MessageConstant 含 SHOP_CLOSE、USER_NOT_LOGIN、ADDRESS_BOOK_IS_NULL） | ✅ |
| | `context` | BaseContext：ThreadLocal 保存当前登录人 id | ✅ |
| | `enumeration` | OperationType（INSERT/UPDATE，配 @AutoFill）、EntityType（预留） | ✅ |
| | `exception` | BaseException 及 13 个业务异常 + **GlobalExceptionHandler**（唯一键冲突/未登录 401/业务兜底/NFE/未知异常） | ✅ |
| | `properties` | JwtProperties / WeChatProperties / LocalStorageProperties（@ConfigurationProperties） | ✅ |
| | `result` | Result<T> / PageResult 统一返回 | ✅ |
| | `utils` | JwtUtil（jjwt 0.9.1 写法，JDK17 需 jaxb-api） | ✅ |
| **shanyue-pojo** | `entity` | 13 张表的实体（与 schema.sql 列一一对应，金额 BigDecimal、时间 LocalDateTime） | ✅ 勿随意改 |
| | `dto` | 请求参数模型（DrinkDTO 含 specGroups、OrdersSubmitDTO 含 deliveryMode…） | ✅ |
| | `vo` | 返回视图（DrinkVO 含 specGroups/monthSales、OrderSubmitVO 含 pickupNumber/deliveryFee…） | ✅ |
| **shanyue-server** | `config` | WebMvcConfiguration（拦截器接线+本地资源映射）/ Jackson 日期格式 / RedisTemplate 序列化 / WebSocket / RestTemplate | ✅ |
| | `annotation` + `aspect` | @AutoFill + AutoFillAspect 公共字段自动填充 | ✅ |
| | `websocket` | WebSocketServer（/ws/{sid}，来单/催单推送 TODO） | ✅ |
| | `interceptor` | 双端 JWT 拦截器（WebMvcConfiguration 已接线，登录态校验） | ✅ |
| | `controller/admin` | 管理端 11 组（员工/分类/饮品/套餐/订单/报表/工作台/店铺/公共 + v2 桩：券模板/评价管理） | ✅（v2 桩见 TODO） |
| | `controller/user` | C 端 14 组（登录/店铺/菜单/购物车/地址簿/订单/套餐 + v2 桩：横幅/签到/券包/会员/评价墙/AI 点单） | ✅（v2 桩见 TODO） |
| | `service` `service/impl` | 主链路业务逻辑（登录/饮品规格树/购物车/订单六步法…苍穹对照完整实现） | ✅ |
| | `mapper`（java） | Mapper 接口（注解 SQL + XML 混用） | ✅ |
| | `resources/mapper` | MyBatis XML（含动态 SQL 样板） | ✅ |
| | `resources/application*.yml` | 主配置 + dev 环境配置（每项带中文注释） | ✅ |
| **sql** | schema.sql / data.sql | 建表 + 种子数据 | ✅ |
| **openapi.json** | 根目录 | 全量接口契约（OpenAPI 3.0.3，可导入 Apifox） | ✅ |

## 九、常见报错速查（6 条）

1. **启动报 `BeanNotFoundError` / `Field xxxMapper required a bean`：Mapper 没被扫描到**
   Mapper 接口必须放 `com.shanyue.mapper` 包（启动类默认扫描 `com.shanyue` 及子包），接口上加 `@Mapper` 注解；
   或在启动类加 `@MapperScan("com.shanyue.mapper")`。XML 文件必须放 `resources/mapper/`（对应 `mapper-locations: classpath:mapper/*.xml`），namespace 写接口全限定名。

2. **数据库连接失败 / 时间差 8 小时：时区问题**
   报 `The server time zone value 'xxx' is unrecognized...`：url 里保留 `serverTimezone=Asia/Shanghai` 别删；
   报 `Access denied for user 'root'@'localhost'`：密码不对，回第五步改 `application-dev.yml`；
   报 `Unknown database 'shanyue'`：回第三步建库。

3. **启动卡住或店铺状态接口报错：Redis 没启动**
   先启动 Redis（Windows 建议用 `redis-server.exe` 或 WSL；Mac `brew services start redis`）。
   报 `Unable to connect to Redis`：检查 `spring.redis.host/port/password` 与本机 Redis 是否一致。

4. **所有实体类 getter/setter 报红 / 编译大量 cannot find method：Lombok 没生效**
   ① IDEA 装 Lombok 插件；② 开启 Annotation Processing（见第一步）；
   ③ Maven 面板确认 lombok 依赖没有飘红；④ 还不行就 `Build → Rebuild Project`。

5. **启动报 `Web server failed to start. Port 8080 was already in use`：端口占用**
   Windows：`netstat -ano | findstr 8080` 找到 PID → `taskkill /PID xxx /F`；
   Mac/Linux：`lsof -i :8080` → `kill -9 pid`；
   或者改 `application.yml` 的 `server.port`（改了记得同步前端 vite proxy）。

6. **登录时报 `java.lang.NoClassDefFoundError: javax/xml/bind/DatatypeConverter`：jaxb 缺失**
   JDK 11+ 移除了 JAXB，而 jjwt 0.9.1 需要它。shanyue-common 的 pom 里已经引入了 `javax.xml.bind:jaxb-api`，
   出现此错说明依赖没同步成功：Maven 面板 Reload，或 `mvn clean install -DskipTests` 重装一遍。
   （将来升级到 jjwt 0.12.x 就不再需要 jaxb-api。）

---

## 附：与课程工程的对应关系

- 包名规则：`com.sky.* → com.shanyue.*`；饮品类名已全链统一 Drink 系（完整对照表见 `docs/新增功能教学文档.md` 附录 A），
  对照课程时按「苍穹菜品 ↔ 山月饮品」映射即可，其余类名保持苍穹原名，方便逐行对照课程视频/讲义；
- 表名基本沿用课程（employee/setmeal/orders...），唯一改名：课程菜品表 → drink（茶饮项目更贴切）；
  接口路径自 v2 起统一为 /admin/drink、/user/drink（与三端前端、api-docs v1.6 契约一致）；山月新增 `spec_group / spec_option` 两张规格表替代 drink_flavor 的玩法（drink_flavor 保留用于对照）；
- 订单 v2.0 双模式：`orders.delivery_mode`（1 自取 / 2 外送）+ `pickup_number`（Redis 按天自增 A01）+ `delivery_fee`；
- 阿里云 OSS 改为本地磁盘存储（`shanyue.local-storage.path` + `/upload/**` 静态映射），省 AK/SK 配置；
- 接口文档：springdoc（swagger-ui），启动后访问 `/swagger-ui/index.html`，右上角可切换"管理端接口 / 用户端接口"分组。

**下一步**：按 `shanyue-vue3/docs/dev-guide.md` 阶段任务卡动手 —— 阶段一工程认知 → 阶段二登录认证（拦截器+JwtUtil）→ 阶段三分类/饮品管理……每阶段都有 ✍️ 动手清单与前端联调验收标准。
