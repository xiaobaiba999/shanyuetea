# 山月茶咖 · 后端基础框架精讲手册

> 版本：v2.0 ｜ 配套工程：`deliverable/shanyue-server-skeleton/`（Maven 三模块，Spring Boot 2.7.6 + Java 17）
> 读者：第一次独立写 Spring Boot 项目、基础还比较薄弱的你。不需要你"已经会了"，只需要你边看边打开骨架工程对照。
> 用法：本文是**精讲**——每个文件先给"它是干嘛的"，再给代码节选，再逐段讲"为什么这么写"。看不懂某一段时先跳过去，等写到对应功能再回来读第二遍，是正常节奏。
> 姊妹文档：《dev-guide.md》（阶段任务卡，告诉你"什么时候做什么"）、《api-docs.md》（接口契约，告诉你"每个接口长什么样"）。本文回答的是"**这套框架本身是怎么转起来的**"。

---

## 目录

- [第 1 章 开篇：什么是"基本框架"，为什么先搭它](#第-1-章)
- [第 2 章 骨架工程总览：三个模块每个文件一句话](#第-2-章)
- [第 3 章 逐文件精讲（代码节选 + 逐段讲解）](#第-3-章)
  - 3.1 pom.xml ×3 ｜ 3.2 application.yml ×2 ｜ 3.3 Result / PageResult ｜ 3.4 GlobalExceptionHandler
  - 3.5 JwtProperties / JwtUtil ｜ 3.6 双 JWT 拦截器 + WebMvcConfiguration ｜ 3.7 BaseContext（ThreadLocal）
  - 3.8 @AutoFill + AutoFillAspect ｜ 3.9 MyBatis 四件套 ｜ 3.10 RedisTemplate ｜ 3.11 Jackson 时间 ｜ 3.12 WebSocket ｜ 3.13 启动类
- [第 4 章 两条完整链路时序（管理端登录 / 小程序微信登录）](#第-4-章)
- [第 5 章 新手常踩 12 大坑（现象 / 原因 / 解决）](#第-5-章)
- [第 6 章 自检清单 10 条 + 骨架文件对照表](#第-6-章)

---

<a id="第-1-章"></a>
# 第 1 章 开篇：什么是"基本框架"，为什么先搭它

## 1.1 "基本框架"到底指什么

把一个后端项目拆开看，其实只有两类东西：

- **业务代码**：登录、下单、加购物车……每个项目都不一样，是要你亲手写的部分；
- **基础设施**：统一返回结构、全局异常处理、登录校验、公共字段填充、数据库连接、缓存配置……这些**每个项目都长一样**，跟"卖奶茶还是卖外卖"无关。

所谓"先搭框架"，就是把基础设施一次性搭好。骨架工程 `shanyue-server-skeleton` 就是这批基础设施的成品：你拿到手就能跑，然后按 dev-guide 的任务卡，往里面填业务。

一个类比：基础设施是毛坯房的水电管线，业务代码是家具。家具可以慢慢添，水电必须在搬家（写业务）之前弄好——否则每加一件家具都要砸一次墙。

## 1.2 为什么必须先搭框架（而不是直接写业务）

1. **约定先行，联调不返工**。前端永远拿到 `{code, msg, data}`，永远用 `token` 请求头，永远 `yyyy-MM-dd HH:mm:ss`——这些约定定晚了，前端每个接口都要改一遍解析代码。
2. **横切逻辑只写一次**。登录校验、异常处理、公共字段填充，如果散落在每个 Controller/Service 里手写，改一次规则就要改几十个文件。框架把它们收敛到拦截器/全局处理器/切面各一个类里。
3. **报错有统一的出口**。新手项目最常见的崩法是"接口报一堆英文堆栈给用户看"。全局异常处理器保证任何异常最终都变成前端能展示的一句中文。
4. **调试有统一的入口**。时间格式错了、SQL 打印没开、分页页码错位……这些问题的解法都沉淀在配置文件里，先配好，后面少熬很多夜。

## 1.3 整体架构图（先混个眼熟，后面每章都会回来对照）

```
        ┌──────────────┐        ┌──────────────────┐
        │ 浏览器管理端   │        │   微信小程序 C 端  │          ← 两个前端
        │ (Vue3, /api) │        │  (wx.request)    │
        └──────┬───────┘        └────────┬─────────┘
               │ http://localhost        │ http://localhost:8080（开发期直连）
               ▼                         ▼
   ┌───────────────────────────────────────────────┐
   │              nginx（部署期，可选）                │   ← 静态资源 + 反向代理
   │   /            → 前端静态页面                    │
   │   /api/**      → 重写为 /admin/** 转发给后端      │
   └──────────────────────┬────────────────────────┘
                          │ http://localhost:8080
                          ▼
┌─────────────────────────────────────────────────────────────┐
│               Spring Boot（shanyue-server，端口 8080）          │
│                                                             │
│   请求先过「安检门」：JwtTokenAdminInterceptor / JwtTokenUserInterceptor
│        │  解析 token → 把当前登录人 id 放进 BaseContext(ThreadLocal)
│        ▼
│   Controller（/admin/**、/user/**）     ← 收请求、发请求，不写业务
│        ▼
│   Service / ServiceImpl                ← 业务规则、事务、异常
│        ▼
│   Mapper 接口 → MyBatis（XML / 注解 SQL） ← 只管SQL，一条方法一条SQL
│         （AutoFillAspect 在 Mapper 前 自动填 create_time/update_user…）
│
│   旁挂三件套：
│   · Redis     —— 店铺状态 / 菜单缓存 / 取餐号按天自增
│   · ThreadLocal —— 当前登录人 id 的"请求内传送带"
│   · WebSocket  —— /ws/{sid} 来单/催单推送给管理端
└──────────┬──────────────────────────────┬───────────────────┘
           ▼                              ▼
     ┌──────────┐                  ┌──────────┐
     │  MySQL   │                  │  Redis   │
     │ 13 张业务表 │                 │ 0 号库    │
     └──────────┘                  └──────────┘
```

## 1.4 一次请求的完整旅程（背下这 7 步，后面所有章节都是在展开它）

1. 前端发请求（管理端带请求头 `token: <jwt>`；C 端同样带 `token`，登录接口除外）；
2. 若经 nginx：`/api/xxx` 被重写为 `/admin/xxx` 再转发给 8080；
3. Spring MVC 按路径找到 Controller 方法；请求先进入该路径挂的拦截器 `preHandle()`；
4. 拦截器解析 JWT：成功 → 把用户 id 放进 `BaseContext`（ThreadLocal），放行；失败 → 抛 `UserNotLoginException` → 全局异常处理器回 HTTP 401；
5. Controller 调 Service；Service 调 Mapper；AutoFill 切面在 SQL 执行前填公共字段；
6. MyBatis 执行 SQL，MySQL 返回数据，Service 组装 VO，Controller 包成 `Result.success(data)`；
7. 若第 4~6 步任何一步抛异常：`GlobalExceptionHandler` 统一兜底，回 `Result.error(中文提示)`。

---

<a id="第-2-章"></a>
# 第 2 章 骨架工程总览：三个模块每个文件一句话

## 2.1 模块划分与依赖方向

```
shanyue（父工程，packaging=pom，只管版本与聚合）
├── shanyue-common   ← 最底层：谁都能用，不依赖任何兄弟模块
├── shanyue-pojo     ← 数据模型：被 server 依赖
└── shanyue-server   ← 最顶层：业务 + 启动类，依赖 common 和 pojo
```

为什么分三个模块？——**职责隔离**。实体/DTO/VO（pojo）和工具/异常（common）是"定义"，server 是"使用"。定义与使用分离后，想复用 common 给别的项目、或者检查"server 有没有偷懒把业务写进实体类"，都一目了然。Maven 依赖方向只能是上层依赖下层，形成一棵树、不能成环。

## 2.2 shanyue-common（31 个文件：常量 / 上下文 / 异常 / 配置属性 / 统一返回 / JWT 工具）

| 包 / 类 | 一句话职责 |
|---|---|
| `constant/MessageConstant` | 全部中文提示语常量（"账号不存在""未登录""门店已打烊"…），抛异常只用它，禁止散写字符串 |
| `constant/StatusConstant` | 启用/禁用通用状态（1 启用 0 禁用），员工/分类/饮品共用 |
| `constant/ShopStatusConstant` | 店铺营业状态常量（1 营业中 0 打烊） |
| `constant/PasswordConstant` | 新员工默认密码 `123456` |
| `constant/JwtClaimsConstant` | JWT 载荷自定义字段名：`empId` / `userId` |
| `constant/AutoFillConstant` | 公共字段 setter 的方法名字符串（`setCreateTime` 等），给切面反射用 |
| `context/BaseContext` | ThreadLocal 工具：存/取/清"当前登录人 id"（3.7 节精讲） |
| `enumeration/OperationType` | INSERT / UPDATE 枚举，`@AutoFill` 的参数 |
| `enumeration/EntityType` | 实体类型枚举（预留，暂未使用） |
| `exception/BaseException` | 所有业务异常的父类，全局处理器按它兜底 |
| `exception/*Exception`（13 个） | 具体业务异常：账号不存在/锁定、密码错误、重复、删除受限、订单/购物车/地址簿业务错、未登录、登录失败、文件上传失败、起售停售受限、密码修改失败 |
| `exception/GlobalExceptionHandler` | 全局异常处理器：把异常翻译成 `Result`（3.4 节精讲） |
| `result/Result` | 统一响应 `{code, msg, data}`，code=1 成功（3.3 节） |
| `result/PageResult` | 分页响应 `{total, records}`（3.3 节） |
| `properties/JwtProperties` | 绑定 yml 的 `shanyue.jwt.*`：双端密钥/TTL/请求头名 |
| `properties/WeChatProperties` | 绑定 `shanyue.wechat.*`：小程序 appid/secret |
| `properties/LocalStorageProperties` | 绑定 `shanyue.localStorage.path`：本地图片目录（替代 OSS） |
| `utils/JwtUtil` | JWT 生成/解析两个静态方法（3.5 节） |

## 2.3 shanyue-pojo（56 个文件：entity 13 + dto 23 + vo 20）

| 包 | 一句话职责 | 记忆法 |
|---|---|---|
| `entity`（13 个） | 和数据库表一一对应：Employee/Category/Drink/DrinkFlavor/SpecGroup/SpecOption/Setmeal/SetmealDrink/User/AddressBook/ShoppingCart/Orders/OrderDetail | 一表一实体，字段=列的驼峰版 |
| `dto`（23 个） | **接收前端参数**的模型：EmployeeLoginDTO、DrinkDTO（含规格树）、OrdersSubmitDTO（含 deliveryMode）… | D=Down，数据从上往下进后端 |
| `vo`（20 个） | **返回给前端**的视图：EmployeeLoginVO（含 token）、DrinkVO（含 specGroups/monthSales）、OrderSubmitVO（含 pickupNumber）… | V=View，给前端看的 |

三者怎么选？一句话口诀：**进用 DTO、出用 VO、存表用 entity**。同一个"饮品"，新增时收 `DrinkDTO`（带规格树），落库拆成 `Drink`+`SpecGroup`+`SpecOption` 三个 entity，返回时组装 `DrinkVO`。绝不拿 entity 直接收/发前端——多字段泄露（如密码）与少字段报错都源于此。

## 2.4 shanyue-server（业务 + 启动，62 个文件）

| 包 / 类 | 一句话职责 |
|---|---|
| `ShanyueApplication` | 启动类：开事务、开缓存，组件扫描从 `com.shanyue` 开始 |
| `config/WebMvcConfiguration` | 注册双拦截器（含放行清单）+ `/upload/**` 静态映射 + swagger 分组 |
| `config/JacksonObjectMapperConfig` | 全局时间格式 `yyyy-MM-dd HH:mm:ss`（3.11 节） |
| `config/RedisTemplateConfiguration` | RedisTemplate 的 key 用字符串序列化（3.10 节） |
| `config/WebSocketConfiguration` | 注册 WS 端点扫描器 |
| `config/RestTemplateConfiguration` | HTTP 客户端 Bean（微信 jscode2session 用） |
| `annotation/AutoFill` | 方法级自定义注解，贴在 Mapper 方法上当"标签" |
| `aspect/AutoFillAspect` | 切面：拦截带标签的 Mapper 方法，自动填 4 个公共字段 |
| `interceptor/JwtTokenAdminInterceptor` | 管理端安检门：验 token → BaseContext（3.6 节） |
| `interceptor/JwtTokenUserInterceptor` | C 端安检门：同构，密钥/字段不同（3.6 节） |
| `controller/admin`（9 个） | 管理端接口：员工/分类/饮品/套餐/订单/统计/店铺/工作台/公共上传 |
| `controller/user`（8 个） | C 端接口：用户(登录/资料)/分类/菜品/套餐/购物车/订单/地址簿/店铺 |
| `service`（11 个接口）+ `impl`（11 个实现） | 业务逻辑层；接口定契约，impl 写实现 |
| `mapper`（12 个接口） | 数据访问层：一个 Mapper 对一张表，注解 SQL 与 XML 混用 |
| `websocket/WebSocketServer` | WS 端点 `/ws/{sid}`：群发消息给在线管理端页面 |
| `resources/application.yml` + `application-dev.yml` | 全部配置（3.2 节逐项讲） |
| `resources/mapper/CategoryMapper.xml` | 动态 SQL 样板（3.9 节） |
| `sql/schema.sql` + `sql/data.sql` | 13 张建表语句 + 种子数据（含 mock_openid_demo 测试用户） |

> 状态标注：✅ = 已完成可直接用；✍️ = 动手区（Controller/Service/Mapper 的业务实现按 dev-guide 阶段任务写）。common 全部 ✅，这正是"骨架"的含义。

---

<a id="第-3-章"></a>
# 第 3 章 逐文件精讲（代码节选 + 逐段讲解）

> 本章每节的结构：**文件在哪 → 代码节选 → 逐段讲解**。节选有删减，完整版以工程内文件为准（骨架里每个文件头部都有大段中文注释，是本文的"扩展阅读"）。

## 3.1 pom.xml ×3：Maven 聚合工程的"户口本"

### 父 pom（`shanyue-server-skeleton/pom.xml`）

```xml
<parent>
    <artifactId>spring-boot-starter-parent</artifactId>
    <groupId>org.springframework.boot</groupId>
    <version>2.7.6</version>          <!-- 全家桶版本总开关 -->
</parent>
<packaging>pom</packaging>            <!-- 自己不产 jar，只聚合+管版本 -->
<modules>
    <module>shanyue-common</module>
    <module>shanyue-pojo</module>
    <module>shanyue-server</module>
</modules>
<properties>
    <java.version>17</java.version>   <!-- 与课程教学版一致 -->
    <jjwt>0.9.1</jjwt>
    <jaxb>2.3.1</jaxb>
    ...
</properties>
<dependencyManagement> ... </dependencyManagement>
```

逐段讲：

- **`<parent>` 指向 spring-boot-starter-parent**：Spring Boot 全家桶（web/mybatis/redis…）的版本号都由它统一裁定，子模块引依赖不用写 version。这也解释了"为什么 starter 之间版本不会打架"——有人替你测过组合。
- **`<modules>` 聚合**：在父工程目录执行 `mvn package`，三个子模块按依赖顺序一起构建。
- **`<properties>` 集中版本**：改版本只改一处。
- **`<dependencyManagement>` 只声明版本、不真正引入**：子模块写 `<dependency>` 不带 version 时由这里决定（详见 5.9 坑）。

### 每个依赖是干嘛的（父 pom 声明版本，common/server 实际引入）

| 依赖 | 引入位置 | 干什么 |
|---|---|---|
| `spring-boot-starter-web` | common + server | Spring MVC + 内嵌 Tomcat。common 也引是因为 Result/全局异常处理器用到 spring-web 的注解，server 依赖 common 后自动传递获得 |
| `mybatis-spring-boot-starter` | server | MyBatis 整合包：`@Mapper` 接口自动代理、数据源自动配置 |
| `pagehelper-spring-boot-starter` | server | 物理分页插件（3.9.4 节） |
| `mysql-connector-java` | server（runtime） | MySQL 驱动。runtime=编译期用不到、运行期才加载，防止误用驱动类写业务 |
| `spring-boot-starter-data-redis` | server | RedisTemplate / StringRedisTemplate |
| `spring-boot-starter-websocket` | server | WebSocket 端点支持 |
| `spring-boot-starter-aop` | server | 切面底层 aspectjweaver，AutoFillAspect 必需 |
| `springdoc-openapi-ui` | pojo + server | 在线接口文档（Boot 2.7 配 1.7.0）；注解包名与新版一致，将来升级零成本 |
| `fastjson` | server | 规格快照 specSelected 的 JSON 序列化/解析 |
| `lombok` | common/pojo/server | `@Data`/`@Slf4j`/`@Builder` 编译期生成样板代码 |
| `spring-boot-configuration-processor` | common（optional） | 让 IDEA 编辑 yml 时对 `shanyue.*` 配置项有自动补全 |
| `jjwt` 0.9.1 | common | JWT 生成/解析（3.5 节） |
| `jaxb-api` 2.3.1 | common | **jjwt 0.9.1 的"救命搭子"**，见下 |

### 专项：jjwt 0.9.1 为什么必须配 jaxb-api（高频报错）

jjwt 0.9.1 内部用 `javax.xml.bind.DatatypeConverter` 做 Base64 编解码。JAXB 从 JDK 11 起**被从 JDK 里移除**了，所以 JDK 17 上跑 jjwt 0.9.1 必须手动补 `jaxb-api`，否则启动或登录时抛：

```
java.lang.NoClassDefFoundError: javax/xml/bind/DatatypeConverter
```

一句话记住：**JDK 8 时代的老库 + JDK 17 = 常见"类找不到"，缺什么补什么（这里就是 jaxb）**。

## 3.2 application.yml / application-dev.yml：每项含义

### 主配置 `application.yml`（放"所有环境都不变"的）

```yaml
server:
  port: 8080          # 后端端口；前端 proxy / 小程序 baseUrl / swagger 都按它找后端
spring:
  application:
    name: shanyue-server   # 应用名（日志/监控里标识用）
  profiles:
    active: dev       # 再加载 application-dev.yml，两份合并后生效
```

**为什么拆两个文件**：主配置放不变量，dev 放因环境而异的（数据库地址、密码、密钥）。将来上线加 `application-prod.yml`，把 active 改成 prod 即可——**代码一行不动**。

### 开发环境 `application-dev.yml`（逐项）

| 配置项 | 值 | 为什么要这么配 |
|---|---|---|
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | MySQL 8 驱动包名带 `.cj`，写成老包名直接 ClassNotFoundException |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/shanyue?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true` | 四个参数逐个讲：`serverTimezone` 不写会时区异常/差 8 小时（5.10 坑）；`characterEncoding` 中文不乱码；`useSSL=false` 本地开发省去 SSL 告警；`allowPublicKeyRetrieval=true` 配合 MySQL8 新认证方式，缺了报 "Public Key Retrieval is not allowed" |
| `spring.datasource.username/password` | root / root | **⭐ 90% 的"连不上数据库"是密码没改成自己的** |
| `spring.redis.host/port/password/database` | localhost / 6379 / '' / 0 | 店铺状态、菜单缓存、取餐号自增都用它；本地没密码就留空 `''` |
| `spring.servlet.multipart.max-file-size` | 10MB | 不配默认 1MB，传大图直接 MaxUploadSizeExceededException |
| `mybatis.mapper-locations` | `classpath:mapper/*.xml` | 告诉 MyBatis 去哪找 XML；写错路径 = "Invalid bound statement" |
| `mybatis.type-aliases-package` | `com.shanyue.entity` | XML 里 resultType 直接写 `Category`，不用写全限定名 |
| `mybatis.configuration.map-underscore-to-camel-case` | true | 驼峰映射总开关（3.9.1 节） |
| `mybatis.configuration.log-impl` | StdOutImpl | 控制台打印 SQL+参数，排错神器；**上生产要注释掉** |
| `logging.level.com.shanyue` | debug | 拦截器/切面里 log.info 才能看见 |
| `shanyue.jwt.admin-secret-key/admin-ttl/admin-token-name` | 密钥串 / 7200000(2h) / token | 管理端 JWT 三要素：签发用什么签、多久过期、请求头叫什么 |
| `shanyue.jwt.user-secret-key/user-ttl/user-token-name` | 另一串 / 259200000(3 天) / token | C 端三要素；**密钥与管理端不同**=令牌不能混用（密钥隔离） |
| `shanyue.wechat.appid/secret` | 占位 | 真实小程序才需要；骨架 mock 登录用不到 |
| `shanyue.localStorage.path` | `D:/shanyue/upload` | 本地图片目录；`/upload/**` 静态映射指向这里 |

yml 的铁律只有一条：**层级靠缩进，同级左对齐**。`serverTimezone=Asia/Shanghai` 这种值里没有特殊字符不用加引号；`''` 表示"空字符串"而不是 null。

## 3.3 Result / PageResult：前端拿到手的"信封"

### Result（`common/result/Result.java`）

```java
@Data
public class Result<T> implements Serializable {
    private Integer code;   // 1 成功；0（及其它）失败
    private String msg;     // 错误信息
    private T data;         // 业务数据

    public static <T> Result<T> success() { ... }          // code=1
    public static <T> Result<T> success(T object) { ... }  // code=1 + data
    public static <T> Result error(String msg) { ... }     // code=0 + msg
}
```

**为什么 code=1 表示成功（而不是 0）？** 这是一份**前后端契约**：骨架、admin 前端、小程序的 request 工具都按"code===1 算成功"写死了解包逻辑。0/1 各有阵营，本工程选了 1——你只需记住"判断成功永远看 `code === 1`"，别用 `!code`（0 是 falsy 会误判，虽然恰好不错，但语义混乱）。

**为什么不用 HTTP 状态码表达业务失败？** 业务失败（密码错误）的请求其实"送达且被处理了"，HTTP 仍是 200；真正的协议层失败（未登录 401）才动 HTTP 状态码。这套"**双通道**"设计在 3.4 节展开。

### PageResult（`common/result/PageResult.java`）

```java
@Data @AllArgsConstructor @NoArgsConstructor
public class PageResult implements Serializable {
    private long total;    // 总记录数
    private List records;  // 当前页数据
}
```

PageHelper 查回来的 `Page` 对象既是 List 又藏着 total/页码等一堆字段，直接序列化给前端会多出十几个用不上的字段。PageResult 就是把前端分页组件**真正需要的两个字段**抽出来。Service 层标准三连：

```java
PageHelper.startPage(page, pageSize);            // ① 发分页指令（ThreadLocal）
Page<Employee> page = employeeMapper.page(dto);  // ② 紧跟第一条查询（被拦截拼 limit）
return new PageResult(page.getTotal(), page.getResult()); // ③ 抽两个字段
```

## 3.4 GlobalExceptionHandler：全项目的"异常翻译官"（`common/exception/`）

### 它解决什么问题

没有它，每个 Controller 都要写 try-catch；有了它，**Service 只管 throw，这里统一接住**，任何异常最终都变成 `Result{code=0, msg=中文}` 返回。

### @RestControllerAdvice 原理（面试高频）

```java
@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result handleSQL...(SQLIntegrityConstraintViolationException ex) {
        // Duplicate entry 'zhangsan' for key 'employee.idx_username'
        String[] split = ex.getMessage().split(" ");
        return Result.error(split[2] + " 已存在");
    }
    ...
}
```

- `@ControllerAdvice`：给"所有 Controller"加的一个**公共切面容器**——凡 Controller 里抛出、未在方法内 catch 的异常，都流到这里；
- `@ExceptionHandler(某异常.class)`：声明"我负责翻译这一类异常"；
- `@RestControllerAdvice` 自带 `@ResponseBody`，所以返回的 `Result` 会自动序列化成 JSON。

**匹配规则**：多个 handler 同时命中时，Spring 按**异常继承树就近匹配**——抛 `UserNotLoginException` 时同时能命中"UserNotLoginException 处理器"和"BaseException 兜底处理器"，但取层次更近的前者。所以**方法书写顺序无所谓**，401 永远不会被兜底 handler 抢走。

### 五个 handler 一览

| Handler | 触发场景 | 返回 |
|---|---|---|
| `SQLIntegrityConstraintViolationException` | 撞了唯一索引（重复用户名/分类名） | 切词取冲突值 → `"zhangsan 已存在"` |
| `UserNotLoginException` | 拦截器验 token 失败 | **HTTP 401** + `{code:0, msg:"未登录"}` |
| `BaseException` | 所有业务异常（13 个子类的爸爸） | `code=0 + e.getMessage()`（所以异常 message 必须用 MessageConstant 常量） |
| `NumberFormatException` | 字符串转数字失败 | "数字格式不正确，请检查请求参数" |
| `Exception` | 其它一切未知异常 | **固定返回"未知错误"，绝不把 e.getMessage() 吐给前端**（可能带 SQL/表名/路径，是安全隐患）；完整堆栈进服务端日志 |

### 专项：HTTP 401 与 Result code 的"双通道"设计（本骨架最重要的设计决策之一）

```
通道 1（业务失败）：HTTP 200 + {code:0, msg:"密码错误"}
        → 前端 toast 展示 msg，用户留在当前页
通道 2（协议层失败）：HTTP 401 + {code:0, msg:"未登录"}
        → 前端 request 工具统一拦截 401：清 token → 跳登录页
```

为什么 `UserNotLoginException` 要单独一个 handler 加 `@ResponseStatus(HttpStatus.UNAUTHORIZED)`？因为如果走 BaseException 兜底，响应是 200+code=0，**前端无法区分"密码错了"和"token 过期了"**，也就没法自动跳登录页。一个注解，两个通道各走各的。

## 3.5 JwtProperties / JwtUtil：签发与验证"通行证"

### JwtProperties（`common/properties/`）

```java
@Component
@ConfigurationProperties(prefix = "shanyue.jwt")   // 绑定 yml 的 shanyue.jwt.*
@Data
public class JwtProperties {
    private String adminSecretKey;  // ← admin-secret-key（松散绑定：中划线↔驼峰自动映射）
    private long adminTtl;          // ← admin-ttl（毫秒）
    private String adminTokenName;  // ← admin-token-name
    private String userSecretKey;   // C 端一套，密钥与管理端不同
    private long userTtl;
    private String userTokenName;
}
```

三个要点：① `prefix` + 字段名 = yml 里的 key，松散绑定自动驼峰转换；② `@Component` 让它成为 Bean，拦截器/Controller 直接 `@Autowired`；③ **双端各一套**，员工令牌拿到 C 端解析会直接失败——安全性靠"密钥隔离"。

### JWT 三段结构图解（背下来，面试必考）

```
eyJhbGciOiJIUzI1NiJ9  .  eyJlbXBJZCI6MSwiZXhwIjoxNzA...  .  qp8Xj2mL...
────────────────────     ─────────────────────────────     ──────────
      Header                     Payload                  Signature
      （头部）                    （载荷）                  （签名）
   算法是啥：HS256            业务字段：empId=1           防篡改：对前两段
                          标准字段：exp 过期时间          用密钥算的摘要
```

- 三段都是 Base64 编码——**编码 ≠ 加密**，Payload 用 base64 解码人人可读，所以**绝不放密码等敏感信息**，只放 id；
- 签名 = HMAC(header.payload, 密钥)。客户端改了 payload 里任何一个字符（比如把 empId 改成 1 号管理员），服务端重算签名对不上 → 解析直接抛异常。**签名防篡改，密钥防伪造**——密钥泄露等于任何人可签发合法 token，所以密钥只存服务端 yml；
- 服务端**无状态**：不存 session、不查库，解析 token 就知道"是谁、过没过期"。这也是"退出登录=前端删 token"的原因（想服务端强制失效要黑名单机制，进阶）。

### JwtUtil（`common/utils/`，jjwt 0.9.1 API）

```java
// 生成：载荷(claims) + HS256签名(密钥) + 过期时间
public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
    long expMillis = System.currentTimeMillis() + ttlMillis;
    return Jwts.builder()
            .setClaims(claims)                                        // empId / userId
            .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8))
            .setExpiration(new Date(expMillis))
            .compact();
}

// 解析：验签 + 查过期，任何一步失败都抛异常（由拦截器 catch）
public static Claims parseJWT(String secretKey, String token) {
    return Jwts.parser()
            .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
            .parseClaimsJws(token)
            .getBody();
}
```

## 3.6 双 JWT 拦截器 + WebMvcConfiguration：请求的"安检门"

### 拦截器是什么、为什么用它做登录校验

`HandlerInterceptor` 是 Spring MVC 的"请求前置钩子"：请求到达 Controller 前先过 `preHandle()`，返回 true 放行、false（或抛异常）拦截。登录校验是"几乎每个接口都要做"的横切逻辑——放拦截器写**一次**，Controller 零侵入。

### JwtTokenAdminInterceptor 的 preHandle 逐行讲（`server/interceptor/`）

```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // ① 拦到的"目标"不是 Controller 方法（比如静态资源/swagger）→ 没有登录语义，放行
    if (!(handler instanceof HandlerMethod)) {
        return true;
    }
    // ② 从请求头取 token（请求头名读配置 shanyue.jwt.admin-token-name）
    String token = request.getHeader(jwtProperties.getAdminTokenName());
    try {
        // ③ 用管理端密钥解析：验签 + 过期检查，失败/过期/非法都会抛异常进 catch
        Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
        // ④ 解析成功：从载荷取出员工 id（claims.get 返回 Object，转 String 再转 Long）
        Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
        // ⑤ 存入 ThreadLocal——本请求后续任何代码都能拿到"当前是谁"
        BaseContext.setCurrentId(empId);
        return true;                      // ⑥ 放行进 Controller
    } catch (Exception ex) {
        // ⑦ 不放行的方式是"抛异常"：交全局处理器 → HTTP 401 + {code:0,msg:"未登录"}
        throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
    }
}

@Override
public void afterCompletion(...) {        // ⑧ 请求结束后【必须】清理 ThreadLocal
    BaseContext.removeCurrentId();        //    线程池复用，不清理会"串号"（3.7 节）
}
```

### JwtTokenUserInterceptor 与它的三个差异

两个拦截器逐行同构，差异只有三处：请求头名取 `getUserTokenName()`、解析密钥取 `getUserSecretKey()`、claims 里取 `USER_ID`（userId）。为什么要两个类？——**路径分开挂、密钥分开管**：`/admin/**` 与 `/user/**` 各拦各的，一端泄露伪造不出另一端的令牌。

### WebMvcConfiguration：把安检门装到哪条走廊

```java
registry.addInterceptor(jwtTokenAdminInterceptor)
        .addPathPatterns("/admin/**")                    // 拦 /admin/ 下所有
        .excludePathPatterns("/admin/employee/login");   // 放行登录（不登录哪来的 token？）

registry.addInterceptor(jwtTokenUserInterceptor)
        .addPathPatterns("/user/**")
        .excludePathPatterns("/user/user/login")         // 放行微信登录
        .excludePathPatterns("/user/shop/status");       // 放行营业状态（进首页就要看）
```

**放行 `/user/user/login` 的原因**：登录前用户根本没有 token，若不放行就是"先有鸡还是先有蛋"死循环——浏览器表现就是无限 401（见 5.1 坑）。同一路径写多个 `excludePathPatterns` 是**叠加**关系，都放行。

**与 nginx `/api/` → `/admin/` 映射的关系**：admin 前端所有请求统一以 `/api` 开头（baseURL='/api'，如 `POST /api/employee/login`），nginx/vite 把 `/api` 前缀**重写为 `/admin`** 再转发给 8080——所以后端拦截器配的是 `/admin/**`，跟前端写的 `/api` 永远对不上号也别慌，中间隔着一层翻译。开发期这层翻译由 vite proxy 干，部署期由 nginx 干，规则完全一致（见 dev-guide 附录 C）。

这个配置类还干两件事：`addResourceHandlers` 把 `/upload/**` 映射到本地磁盘目录（上传图片的公开访问入口，路径末尾必须补 `/`）；`GroupedOpenApi` 两个 Bean 把 swagger 文档分成"管理端接口/用户端接口"两组。

## 3.7 BaseContext 与 ThreadLocal：请求内的"传送带"（全骨架最值得吃透的一处）

### 解决什么问题

拦截器验完 token 拿到了 userId，可这个 id 要一路传给 Controller → Service → Mapper（中间隔着 N 层方法）。方案 A：给每个方法加一个 `Long userId` 参数——签名又丑又容易漏传。方案 B（本工程）：存进 ThreadLocal，**同一请求内任何代码随手可取**。

### 原理图解

```
                    BaseContext.threadLocal（全项目唯一一把"钥匙"）
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
  Tomcat 工作线程 #1         工作线程 #2                工作线程 #3
  ┌──────────────────┐   ┌──────────────────┐     ┌──────────────────┐
  │ 自己的 ThreadLocalMap│  │ 自己的 ThreadLocalMap│    │ map 已被 remove 清空 │
  │ key→value         │   │ key→value          │    │ （处理上个请求完    │
  │钥匙→ 1001 (张三)   │   │钥匙→ 1002 (李四)    │    │   成归还线程池）     │
  └──────────────────┘   └──────────────────┘     └──────────────────┘
```

- 每个线程内部有一张自己的 map（ThreadLocalMap），`set/get` 操作的都是**当前线程自己的那张**，天然线程隔离、无需加锁——这就是"本地"二字的含义（Thread-Local）；
- key 是 ThreadLocal 对象本身，value 是你存的 id；所以 100 号线程取到的是张三、101 号取到的是李四，互不干扰。

### 数据流三步（代码就三行）

```java
BaseContext.setCurrentId(userId);   // 拦截器 preHandle：验签成功后"放进"
BaseContext.getCurrentId();         // Service / AutoFillAspect："取出"（如填 create_user）
BaseContext.removeCurrentId();      // 拦截器 afterCompletion：请求结束"清掉"
```

### 两个必须记住的警告

1. **线程池串号**：Tomcat 用线程池复用线程。线程处理完张三的请求，若不 remove 就去处理李四的请求，李四的代码里 `getCurrentId()` 会取到**张三**——下单人变成别人，这是真事故。所以 `afterCompletion` 里的 remove 是**必做题不是选做题**（骨架已写好）。
2. **内存泄漏**：ThreadLocalMap 的 key 是弱引用、value 是强引用。线程长期存活（线程池正是如此）时，value 一直被 map 挂着回收不掉；`remove()` 显式断开引用才彻底释放。"用完即 remove"同时防串号防泄漏，一举两得。

### 为什么存 Long 而不是 Object

本工程只有一种"当前登录人 id"，泛型定为 Long 就够；若一个请求要带多种上下文（id + 角色名…），可以定义一个 Context 对象整体存取。

## 3.8 @AutoFill + AutoFillAspect：公共字段自动填充

### 要解决的问题

13 张表几乎都有 `create_time/update_time/create_user/update_user` 四个公共字段。每个 Service 方法手动 set 四行，几十个方法就是几百行重复代码，还容易漏。切面把这这件事"横切"出来，**在 SQL 执行前统一填**。

### @AutoFill 注解本身没有任何逻辑

```java
@Target(ElementType.METHOD)        // 只能贴在方法上
@Retention(RetentionPolicy.RUNTIME) // 保留到运行期（否则切面反射读不到）
public @interface AutoFill {
    OperationType value();          // INSERT 填 4 字段 / UPDATE 填 2 字段
}
```

它是一张"标签"：真正干活的是切面。贴标签示例：`@AutoFill(value = OperationType.UPDATE) void update(Employee employee);`

### 切点表达式逐词拆解（面试高频，务必逐词会说）

```java
@Pointcut("execution(* com.shanyue.mapper.*.*(..)) && @annotation(com.shanyue.annotation.AutoFill)")
```

| 片段 | 含义 |
|---|---|
| `execution(...)` | 按"方法签名"匹配 |
| 第一个 `*` | 任意返回类型 |
| `com.shanyue.mapper` | 只看 mapper 包（**改包名后忘了改这里 = 静默失效**，字段全 null 且不报错） |
| 中间的 `*` | 任意接口/类 |
| 最后的 `*` | 任意方法名 |
| `(..)` | 任意参数（不限个数不限类型） |
| `&&` | 两个条件同时满足 |
| `@annotation(...)` | 且方法上贴了 @AutoFill 标签 |

交集语义：**"mapper 包里、贴了 @AutoFill 标签的方法"**执行前，先跑切面的 `@Before` 通知。

### @Before 通知里干了什么

拿到方法签名 → 读注解判断 INSERT/UPDATE → 取第一个参数（约定实体永远是第一个参数）→ **反射**找 `setCreateTime/setCreateUser/setUpdateTime/setUpdateUser` → 填 `LocalDateTime.now()` 和 `BaseContext.getCurrentId()`。UPDATE 只填后两个——创建时间/创建人是历史事实，不能改。

为什么用反射？被拦的实体类型各不相同（Employee/Category/Drink…），编译期写不死，只能运行期按方法名找 setter。这也解释了为什么 setter 方法名要抽成 `AutoFillConstant` 常量——手滑拼错编译器不报错，运行期才炸。

价值一句话总结：**新增员工那行代码里没有一行在填公共字段，但库里四个字段都有值**——这行代码的作者可以专心写业务。

## 3.9 MyBatis 四件套：驼峰映射 / #{} vs ${} / 动态 SQL / PageHelper

### 3.9.1 驼峰映射：列名 ↔ 字段名的自动翻译

数据库列叫 `create_time`，Java 字段叫 `createTime`——约定俗成的两种命名风格。`map-underscore-to-camel-case: true` 打开后，MyBatis 自动把 `create_time` 列的值装进 `createTime` 字段（去掉下划线、下一位大写）。**关掉它**，查询结果里所有多单词字段全是 null，且不报任何错——属于"最难查的 bug"梯队。

### 3.9.2 #{} vs ${}：一个防注入，一个字符串拼接（必考）

```xml
where name like concat('%', #{name}, '%')     -- ✅ 推荐
where name like '%${name}%'                   -- ❌ 仅在理解风险时使用
```

| | `#{}` | `${}` |
|---|---|---|
| 原理 | 预编译占位符 `?`，值作为参数传入 | **字符串替换**，直接把值拼进 SQL 文本 |
| 注入风险 | 无（值永远不会被当代 SQL 执行） | 有：传入 `' or '1'='1` 直接改变 SQL 语义 |
| 适用 | 一切"传值"场景（99% 的情况） | 传"表名/列名/order by 字段名"等无法参数化的场景，且值必须来自白名单 |

`concat('%',#{name},'%')` 就是"想模糊匹配又想防注入"的标准写法。

### 3.9.3 动态 SQL：一条 SQL 适应多种条件组合（样板：resources/mapper/CategoryMapper.xml）

```xml
<mapper namespace="com.shanyue.mapper.CategoryMapper">  <!-- 必须与接口全限定名一致 -->

    <!-- 动态更新：<set> 自动去掉末尾多余逗号；只更新传了的字段 -->
    <update id="update" parameterType="com.shanyue.entity.Category">
        update category
        <set>
            <if test="name != null">name = #{name},</if>
            <if test="type != null">type = #{type},</if>
            ...
        </set>
        where id = #{id}
    </update>

    <!-- 动态查询：<where> 自动处理首个 and 与全空条件 -->
    <select id="page" resultType="com.shanyue.entity.Category">
        select * from category
        <where>
            <if test="name != null and name != ''">
                and name like concat('%',#{name},'%')
            </if>
            <if test="type != null">and type = #{type}</if>
        </where>
        order by sort desc
    </select>
</mapper>
```

- `<if test>`：条件为真才拼进 SQL——前端"名称可不填、类型可不选"的自由组合查询就这么实现；
- `<where>`：子条件全空时连 `where` 关键字都不生成，非空时自动吃掉第一个 `and`；
- `<set>`：同理处理逗号；
- 简单单表 SQL 用注解（`@Select/@Insert`）更省，带 `<if>` 的写 XML 更清晰——两者混用是常态，namespace 必须与 Mapper 接口全限定名一致，MyBatis 靠"namespace + 语句 id"对接方法。

### 3.9.4 PageHelper：页码从 1 起！（新手第一大误用）

```java
PageHelper.startPage(page, pageSize);   // 页码从 1 开始！前端传 0 会查出诡异结果
Page<Employee> page = employeeMapper.page(dto);  // 必须紧跟第一条查询
```

- **原理**：`startPage` 把分页参数存进 ThreadLocal；PageHelper 的 MyBatis 拦截器在**下一条 SQL** 执行前，先发一条 `select count(*)` 拿 total，再给你的 SQL 追加 `limit offset, size` 物理分页；
- **两条铁律**：① 页码从 1 起（第 1 页传 1）；② startPage 与查询之间不能插别的查询——ThreadLocal 里的分页指令会被别的查询"抢走"，分页错乱；
- 为什么叫物理分页：SQL 层面真的 limit 了，不像有些框架"全查回来内存里截取"（数据量一大就内存爆炸）。

## 3.10 RedisTemplateConfiguration：key 可读，value 能装

```java
@Bean
public RedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory);   // host/port 来自 yml
    redisTemplate.setKeySerializer(new StringRedisSerializer()); // key 用字符串序列化
    return redisTemplate;
}
```

- **为什么必须配**：Spring Boot 默认的 RedisTemplate 对 key 做 JDK 序列化，存进去是 `\xac\xed\x00\x05t\x00...` 乱码，redis-cli 里根本认不出 key，想 `keys *` 排查都难；
- 换成 `StringRedisSerializer` 后，key 是 `shanyue:pickup:2026-09-26`、`SHOP_STATUS` 这样的人类可读字符串；
- value 保留默认 JDK 序列化（能直接存取 Java 对象如 `List<DrinkVO>`），代价是 redis-cli 里 value 不可读——想可读可换 `GenericJackson2JsonRedisSerializer`（进阶）；
- 典型使用（OrderServiceImpl 内）：`redisTemplate.opsForValue().increment("shanyue:pickup:2026-09-26")` —— 取餐号 A01/A02 按天自增，INCR 原子操作并发不重号，key 带日期跨天自然重置。

## 3.11 JacksonObjectMapperConfig：全局时间格式统一

```java
public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

javaTimeModule.addSerializer(LocalDateTime.class,
        new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));
...
objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

@Override
public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setObjectMapper(buildObjectMapper());
    converters.add(0, converter);   // 插到第 0 位，最先匹配，优先生效
}
```

**为什么全局配**：LocalDateTime 默认被 Jackson 序列化成 `2026-09-26T10:15:30`（带 T），前端期望 `2026-09-26 10:15:30`。逐个字段加 `@JsonFormat` 要写几十处，全局消息转换器一处搞定、出入向都管（序列化+反序列化）。

原理两句话：Spring MVC 用 `HttpMessageConverter` 列表做对象↔JSON 互转，JSON 转换器默认内部持有一个 ObjectMapper；`extendMessageConverters` 把定制转换器**插到列表第 0 位**——按顺序匹配，排前面的优先。

边界提醒：这套只管 `@RequestBody`（JSON 请求体）与响应 JSON；GET 请求 URL 参数（`?beginTime=...`）走 `@DateTimeFormat` 注解，两套别搞混。**这个格式与接口文档的约定一致，改了它 = 联调时间筛选全错**（5.3 坑还有时区版的故事）。

## 3.12 WebSocketConfiguration + WebSocketServer：服务端"主动喊话"

HTTP 是"一问一答"——管理端想知道有没有新订单，只能轮询（定时反复请求），浪费且不实时。WebSocket 握手一次建立**全双工长连接**，服务端随时可推。

```java
@Configuration
public class WebSocketConfiguration {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();   // 扫描 @ServerEndpoint 并注册端点
    }
}

@Component
@ServerEndpoint("/ws/{sid}")                   // 端点路径，{sid} 客户端标识
public class WebSocketServer {
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen  public void onOpen(Session session, @PathParam("sid") String sid) { sessionMap.put(sid, session); }
    @OnClose public void onClose(@PathParam("sid") String sid) { sessionMap.remove(sid); }
    @OnError public void onError(Throwable error) { log.error("WebSocket 发生错误", error); }
    @OnMessage public void onMessage(String message, @PathParam("sid") String sid) { /* 心跳 TODO */ }

    public void sendMessageToAll(String message) {   // 群发：来单/催单推送入口
        for (Session session : sessionMap.values()) {
            session.getBasicRemote().sendText(message);
        }
    }
}
```

要点：① 静态 map 存所有在线会话，**并发读写必须 ConcurrentHashMap**（课程演示用 HashMap 是线程不安全的）；② 约定消息 JSON 带 `type`：1 来单提醒、2 催单提醒，管理端 onmessage 按 type 播不同提示；③ war 包部署到外部 Tomcat 时 ServerEndpointExporter 要删（骨架是内嵌 Tomcat jar 部署，保留）；④ 支付成功/催单处调用 `sendMessageToAll` 是阶段 7 的动手任务（TODO 已留）。

## 3.13 启动类 ShanyueApplication：为什么它必须"住最外层"

```java
@EnableCaching                 // 开 Spring Cache：@Cacheable 才生效
@SpringBootApplication         // = @Configuration + @EnableAutoConfiguration + @ComponentScan
@EnableTransactionManagement   // 开注解事务：Service 方法上 @Transactional 才生效
public class ShanyueApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShanyueApplication.class, args);
    }
}
```

`@SpringBootApplication` 默认扫描**启动类所在包及其子包**（com.shanyue.*）。它住在最外层包，所有 Bean 才会被扫到——**挪进子包 = 一半的 Bean 集体消失**（经典事故：Mapper 找不到、拦截器不生效）。

---

<a id="第-4-章"></a>
# 第 4 章 两条完整链路时序（把 1~3 章串起来）

## 4.1 链路一：管理端登录（页面 → token → 后续请求）

```
 管理端浏览器                        nginx/vite                       Spring Boot 8080
────────────────                 ─────────────────                ─────────────────────
1. 登录页提交表单
   POST /api/employee/login
   {username, password}
              │
              ├──── 2. /api 前缀重写为 /admin ───────────────►  POST /admin/employee/login
              │                                                  │
              │                                     3. 拦截器 JwtTokenAdminInterceptor：
              │                                        命中 excludePathPatterns("/admin/employee/login")
              │                                        → 直接放行（登录前没有 token）
              │                                                  │
              │                                     4. EmployeeController.login()
              │                                        → EmployeeServiceImpl.login() 三连校验：
              │                                          ① getByUsername 查库 → 查不到抛
              │                                            AccountNotFoundException
              │                                          ② 前端明文 MD5 后与库中密文比对
              │                                            → 不符抛 PasswordErrorException
              │                                          ③ status=DISABLE 抛 AccountLockedException
              │                                                  │
              │                                     5. 校验通过 → 发证：
              │                                        claims={empId} → JwtUtil.createJWT
              │                                        (adminSecretKey, adminTtl)
              │                                                  │
6. 收到 {code:1, data:{id,userName,name,token}} ◄──────────────┘
7. 前端把 token 存 localStorage/pinia
              │
8. 之后每个请求带请求头 token: <jwt>
              ├── 9. nginx 重写 ──────────────────────────►  拦截器 parseJWT：
              │                                              验签+过期 → 取 empId
              │                                              → BaseContext.setCurrentId(empId)
              │                                              → 放行 Controller
              │                                     10. Service/切面随时 getCurrentId()
              │                                         （如 AutoFill 填 create_user）
              │                                     11. afterCompletion → removeCurrentId()
```

编号步骤复述（面试可照此讲）：① 表单提交到 `/api/employee/login`；② nginx 把 `/api` 重写为 `/admin` 转发 8080；③ 登录接口在拦截器放行清单里，直接进 Controller；④ Service 三连校验（查人→验 MD5→查状态）；⑤ 通过则把 empId 装进 claims、用管理端密钥签发 JWT；⑥⑦ 前端拿 token 存起来；⑧⑨ 后续请求带头 token，拦截器验签后把 empId 放进 BaseContext；⑩ 业务层随时取"当前是谁"；⑪ 请求结束清理 ThreadLocal。

## 4.2 链路二：小程序微信登录（wx.login → openid → user token）

```
 微信小程序                        Spring Boot 8080                     微信官方 / MySQL
────────────────                ─────────────────────                ─────────────────
1. wx.login() → 拿到临时凭证 code
   （5 分钟有效、一次性）
2. POST /user/user/login {code}
        │
        ├──────────────► 3. 拦截器 JwtTokenUserInterceptor：
        │                   /user/user/login 在放行清单 → 放行
        │                          │
        │                  4. UserServiceImpl.login()：
        │                     ├─ mock 模式（shanyue.wechat.mock-openid=true，
        │                     │   无真实 appid 也能跑）：
        │                     │     openid = "mock_openid_" + code   ─────────────┐
        │                     ├─ 真实模式：RestTemplate GET               │（二选一）
        │                     │   jscode2session?appid&secret&js_code ──► 微信接口
        │                     │   ← {openid, session_key}                │
        │                     │                                          ▼
        │                     │                                    MySQL.user 表
        │                     ├─ 5. getByOpenId(openid) 查库
        │                     │     查不到 → builder 新 User → insert（自动注册）
        │                     ├─ 6. claims={userId} → JwtUtil.createJWT
        │                     │     （userSecretKey, userTtl=3 天）
        │                     ▼
7. 收到 {code:1, data:{id, openid, token, nickName, avatarUrl}} ◄────┘
8. wx.setStorageSync 存登录态（utils/auth.js）
9. 之后每个请求带 token 头；未登录就下单 → 401 → 自动拉起登录
        │
        ├──────────────► 10. JwtTokenUserInterceptor 验签 → BaseContext.setCurrentId(userId)
        │                  下单/购物车/地址簿的 userId 一律取 ThreadLocal（不信任前端传参，防越权）
```

编号步骤复述：① 小程序 `wx.login()` 拿一次性 code；② POST `/user/user/login`；③ 该路径被拦截器放行；④ Service 换 openid——**mock 模式**用 `mock_openid_ + code` 直接模拟（code 每次不同 → 天然模拟不同用户），**真实模式**用 RestTemplate 调微信 jscode2session；⑤ 按 openid 查库，新用户自动注册（openid 唯一索引兜底防并发重复注册）；⑥ 用 C 端密钥签发含 userId 的 JWT（TTL 3 天）；⑦ 返回 VO；⑧ 小程序存 storage；⑨⑩ 先登录后下单：后续请求带 token，拦截器把 userId 放进 BaseContext；服务端所有"当前用户"一律取 ThreadLocal——这正是 v2.0「微信登录」模式的核心契约（见 dev-guide 阶段 6.5）。

> 骨架调试通道：`sql/data.sql` 预置了 `openid = mock_openid_demo` 的种子用户，小程序登录时传 `code=demo` 即命中它；或 `curl -X POST http://localhost:8080/user/user/login -H "Content-Type: application/json" -d '{"code":"demo"}'`。

---

<a id="第-5-章"></a>
# 第 5 章 新手常踩 12 大坑（现象 / 原因 / 解决）

> 使用方式：出问题时先对号入座"现象"，再按"解决"一步步做。每条都附"原因"是为了让你下次能自己推理，而不是查表。

## 坑 1：401 循环跳登录（登录页一闪一闪）

- **现象**：打开管理端就无限跳登录页；或者登录成功后一进别的页面又被弹回来。
- **原因**：三类。① 拦截器忘了 `excludePathPatterns` 登录接口——登录请求本身被 401，前端自动跳登录页，死循环；② 前端没把 token 放进请求头（或请求头名不是 `token`，与 `admin-token-name` 配置不一致）；③ 改过 `admin-secret-key` 或重启换密钥后，浏览器里还是旧 token，验签失败。
- **解决**：F12 Network 逐条查：登录接口是不是 200？后续请求头里有没有 `token`？401 的是哪些接口？后端日志搜"jwt 校验"。逐项对上即可。

## 坑 2：跨域 CORS 报错（nginx 反代后"消失"）

- **现象**：浏览器 Console 报 `has been blocked by CORS policy`，接口全红。
- **原因**：浏览器同源策略——页面在 5173，接口在 8080，协议/域名/端口任一不同就是跨域。
- **解决**：**不要在后端加 `@CrossOrigin`**。开发期前端 vite proxy 把 `/api`、`/admin`、`/user`、`/upload`、`/ws` 转发到 8080（同源转发，浏览器根本不知道后端存在）；部署期由 nginx 同域反代——跨域在架构上"消失"，而不是被"解决"。这也是为什么骨架里一个 CORS 配置都没有：它属于前端代理层的事。

## 坑 3：Jackson 时间差 8 小时 / 带 T

- **现象**：库里 `2026-09-26 08:00:00`，前端显示 `2026-09-25T16:00:00` 或时间不对；或字段显示成 `2026-09-26T10:15:30`。
- **原因**：两个独立问题。带 T = 没走全局时间格式（Jackson 默认 ISO 格式）；差 8 小时 = 序列化时区不是东八区（JVM 默认时区或 Jackson 时区配置）。
- **解决**：① 骨架已配 `JacksonObjectMapperConfig`（`yyyy-MM-dd HH:mm:ss`），确认这个类没被删、`converters.add(0, converter)` 还在；② 数据库连接串带 `serverTimezone=Asia/Shanghai`（见坑 10）；③ 本机时区确认东八区。三个都对了还差 8 小时，查查是不是手滑在字段上加了 `@JsonFormat(timezone = "GMT")`。

## 坑 4：PageHelper 页码与"分页错乱"

- **现象**：前端传 page=0 查出重复数据；或列表页数据"每页都一样"；或 total 正确 records 为空。
- **原因**：① 页码从 **1** 起，传 0 时 offset 变负数，MySQL 行为诡异；② `startPage` 和查询之间插了别的查询——分页指令存在 ThreadLocal 里，会被"下一条 SQL"抢走，张冠李戴。
- **解决**：`PageHelper.startPage(page, pageSize)` 紧跟第一条查询，中间不许插任何 mapper 调用；页码判空给默认 1；前端分页组件确认从 1 计数。

## 坑 5：ThreadLocal 取出 null（或取到别人的 id）

- **现象**：`BaseContext.getCurrentId()` 返回 null；更吓人的：填进去的 create_user 是别的员工。
- **原因**：null = 取值发生在拦截器 set 之前（比如请求压根没走拦截器：路径没被 `addPathPatterns` 覆盖、或登录放行接口里取）；串号 = **拦截器没在 afterCompletion 里 remove**，线程池复用读到上个请求的值。
- **解决**：确认取值的接口确实在拦截器覆盖范围内；确认两个拦截器都有 `BaseContext.removeCurrentId()`；自己新写的定时任务/异步线程里不要用 BaseContext（子线程不是当前请求线程，取不到是正常的，需要值就当参数传）。

## 坑 6：拦截器 `/*` 与 `/**` 写错

- **现象**：拦截器"好像没生效"——该拦的没拦，或静态资源全被 401。
- **原因**：Ant 路径语法里 `/*` 只匹配**一层**（`/admin`、`/admin/login`），不匹配 `/admin/employee/page`；`/**` 才是任意层级。写 `/*` 大概率拦不到深层接口；反过来把 `/**` 配在过宽的前缀上会误伤静态资源。
- **解决**：一律用 `/**` 表达"整个前缀"：`/admin/**`、`/user/**`。放行清单（excludePathPatterns）精确到具体接口。骨架已配好，动它之前先理解。

## 坑 7：yml 缩进错误

- **现象**：启动报 `while scanning a simple key` / 配置项读不到（properties 字段是默认值）。
- **原因**：yml 用**缩进表达层级**，Tab 与空格混用、同级没对齐、冒号后少空格，都会解析错或把 key 挂错爹。
- **解决**：全部用空格（IDEA 显示空白字符检查）；同一层左对齐；`key: value` 冒号后一个空格；不确定层级时对照骨架原文件逐行抄。

## 坑 8：Lombok 不生效（getter/setter 找不到）

- **现象**：编译满屏 `找不到符号: 方法 getUsername()`；`@Data` 明明加了。
- **原因**：Lombok 是**编译期注解处理器**——IDEA 没装插件（新版内置）或没开注解处理，生成代码这一步根本没跑。
- **解决**：`Settings → Build → Compiler → Annotation Processors → Enable annotation processing` 打勾；Maven 引入 lombok（骨架已引）；还不行就 `mvn clean` 重新构建。

## 坑 9：Maven 依赖红线：dependencyManagement 不引入依赖

- **现象**：父 pom `dependencyManagement` 里明明写了 fastjson，子模块代码却 import 报红。
- **原因**：`dependencyManagement` 只**声明版本**，不真正引入；真正引入要子模块自己的 `<dependencies>` 里写一遍（此时可省 version）。这是多模块工程版本一致性的关键设计：版本集中在父工程，声明分散到使用者。
- **解决**：加依赖三问——版本在哪管（父 pom）？谁来真正引入（子模块）？要不要传递（optional/ scope）？对照骨架三个 pom 抄结构最稳。

## 坑 10：MySQL 8 时区 serverTimezone

- **现象**：启动直接报 `The server time zone value 'UTC' is unrecognized...`；或时间写入查出都偏 8 小时。
- **原因**：MySQL 8 驱动强制校验时区；服务器时区与驱动默认不一致时，时间字段在"写入→存储→读出"的某一环被平移。
- **解决**：JDBC URL 显式加 `serverTimezone=Asia/Shanghai`（骨架 dev 配置已带）；MySQL 服务端也可 `show variables like '%time_zone%'` 核对。改完**重启应用**并重验一条数据的写入与读出。

## 坑 11：Redis 连不上

- **现象**：启动报 `Unable to connect to Redis`；或一切正常但店铺状态/取餐号接口报错。
- **原因**：① 本机 Redis 没启动（最多）；② host/port/password 与 yml 不符；③ Windows 下 Redis 服务名没起或防火墙拦截。
- **解决**：`redis-cli ping` 回 PONG 才算活；核对 dev 配置四项；骨架对"读缓存"做了 try-catch 降级，但取餐号 INCR 这类写操作没得降——Redis 必须先启动再联调。

## 坑 12：接口返回 code=0 的通用排查法

- **现象**：接口通了，但 `{code:0, msg:"xxx"}`，前端 toast 一句中文。
- **原因**：code=0 = 后端**业务/系统异常被全局处理器接住了**——这本身是框架在正常工作，你要做的是顺藤摸瓜。
- **解决**：三步定位法——① 看 `msg`：业务异常直接把 MessageConstant 的中文返回了，一般一眼可判（"该分类下存在商品"）；② 后端控制台搜 ERROR/WARN：`GlobalExceptionHandler` 每个分支都打了日志，`Exception` 兜底分支有完整堆栈；③ 开启 `mybatis.log-impl=StdOutImpl` 看 SQL 与参数，核对是不是数据问题。**永远先看后端日志再改代码**。

---

<a id="第-6-章"></a>
# 第 6 章 自检清单 10 条 + 骨架文件对照表

## 6.1 自检清单（全部打勾才进入阶段 0 的业务开发）

- [ ] 1. 导入工程后 `mvn compile`（或 IDEA Build）通过；若报 JwtToken*Interceptor 找不到，属于动手区留白，按骨架 README 的"临时放行"注释先让它过。
- [ ] 2. 本机 MySQL 8 / Redis 已启动；`application-dev.yml` 密码已改成自己的；`serverTimezone=Asia/Shanghai` 在 URL 里。
- [ ] 3. `sql/schema.sql`、`sql/data.sql` 依序执行成功，`shanyue` 库 13 张表都在，`user` 表里有一条 `mock_openid_demo`。
- [ ] 4. `ShanyueApplication` 启动成功，控制台能看到"山月茶咖后端启动成功"，且 Redis 配置日志、拦截器注册日志都出现。
- [ ] 5. swagger 可访问：`http://localhost:8080/swagger-ui/index.html`，右上角能切"管理端接口/用户端接口"两组。
- [ ] 6. 管理端登录 curl 通：`POST /admin/employee/login {"username":"admin","password":"123456"}` 返回 `code=1` 带 token。
- [ ] 7. C 端登录 curl 通：`POST /user/user/login {"code":"demo"}` 返回 `code=1` 带 token（命中 mock_openid_demo）。
- [ ] 8. 拿第 7 步的 token 请求 `GET /user/user/profile`（请求头 `token`）能返回用户资料；**不带 token** 请求返回 HTTP 401（双通道验证）。
- [ ] 9. 上传一张图（或手工放一张图到 `shanyue.localStorage.path`），`http://localhost:8080/upload/图名` 浏览器能打开。
- [ ] 10. 能脱稿复述：一次请求的 7 步旅程（1.4 节）+ JWT 三段各自的作用（3.5 节）+ ThreadLocal 为什么必须 remove（3.7 节）。

## 6.2 骨架文件对照表（文件 ↔ 本文精讲章节 ↔ 对应开发阶段）

| 骨架文件 / 位置 | 精讲章节 | 你动手写业务的阶段（见 dev-guide） |
|---|---|---|
| pom.xml（父 + 3 子） | 3.1 | 阶段 0（只看不改） |
| application.yml / application-dev.yml | 3.2 | 阶段 0（改密码/密钥） |
| sql/schema.sql + data.sql | 3.2 / 6.1 | 阶段 1（业务表改造） |
| Result / PageResult | 3.3 | 已完成，直接用 |
| GlobalExceptionHandler + 15 个异常类 | 3.4 | 已完成；新增业务异常时照抄同构 |
| JwtProperties / JwtUtil | 3.5 | 已完成；阶段 6.5 换 user 密钥联调 |
| JwtTokenAdminInterceptor / JwtTokenUserInterceptor | 3.6 | 已完成（骨架自带范本）；动手任务里要会逐行讲 |
| WebMvcConfiguration | 3.6 | 阶段 2 联调确认放行清单 |
| BaseContext | 3.7 | 已完成；阶段 5/6 业务里随时取 |
| @AutoFill + AutoFillAspect | 3.8 | 已完成；新 Mapper 方法记得贴注解 |
| CategoryMapper.xml（动态 SQL 样板） | 3.9 | 阶段 2 起照抄结构写自己的 XML |
| RedisTemplateConfiguration | 3.10 | 阶段 6/7（取餐号/缓存） |
| JacksonObjectMapperConfig | 3.11 | 已完成，勿动格式 |
| WebSocketConfiguration + WebSocketServer | 3.12 | 阶段 7（来单/催单推送接线） |
| ShanyueApplication | 3.13 | 已完成 |
| controller/admin ×9、controller/user ×8、service ×11、mapper ×12 | 全章 | ✍️ 动手区，按各阶段任务卡填 |
| UserServiceImpl（微信登录 mock/真实双模式范本） | 4.2 | 阶段 6.5 联调确认 |
| OrderServiceImpl（下单六步法/双模式/取餐号范本） | 3.10 / 4.2 | 阶段 6 联调确认 |

---

## 结语

这份手册讲的所有东西，本质上是同一句话：**把"每个接口都要做的事"收拢到一处，让业务代码只写业务**。统一返回收拢在 Result，异常翻译收拢在 GlobalExceptionHandler，登录校验收拢在拦截器，公共字段收拢在切面，SQL 细节收拢在 Mapper。

下一步：回到 `dev-guide.md` 阶段 0 的任务卡，边写边翻回本文对照。框架先懂，业务才快——山月入杯，开工吧。
