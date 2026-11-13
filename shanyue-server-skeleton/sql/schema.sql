-- =====================================================================
-- 山月茶咖 · 后端基础框架 数据库建表脚本（schema.sql）
-- 使用方法（README 有详细步骤）：
--   1. mysql -uroot -p 登录后执行：CREATE DATABASE shanyue CHARACTER SET utf8mb4;
--   2. USE shanyue; 再执行本文件：source schema.sql  （或用 Navicat/DataGrip 直接运行）
--   3. 接着执行 data.sql 初始化种子数据
-- 字符集统一 utf8mb4：支持 emoji 与生僻字，MySQL 5.7 / 8.0 均可用
-- 说明：表名基本沿用课程同款命名（employee/setmeal/orders...），便于你逐行对照课程代码；
--       v1.2 全链改名：dish/dish_flavor/setmeal_dish 三张表、dish_id/dishId/dishFlavor 字段、
--       Dish* 类名、/dish 接口路径已全部统一为 drink 系（详见教程开头 v1.2 更新说明）；
--       drink_flavor（课程名 dish_flavor）为课程对照保留的"标本"表，山月业务实际走 spec_group / spec_option 两张规格表。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 员工表（管理端登录账号）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS employee;
CREATE TABLE employee (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)  NOT NULL                COMMENT '姓名',
    username    VARCHAR(32)  NOT NULL                COMMENT '用户名',
    password    VARCHAR(64)  NOT NULL                COMMENT '密码（MD5 加密后存储，对应 EmployeeServiceImpl 的 DigestUtils.md5DigestAsHex）',
    phone       VARCHAR(100)          DEFAULT NULL   COMMENT '手机号',
    sex         VARCHAR(2)            DEFAULT NULL   COMMENT '性别 0 女 1 男',
    id_number   VARCHAR(18)           DEFAULT NULL   COMMENT '身份证号',
    status      INT          NOT NULL DEFAULT 1      COMMENT '状态 0禁用 1启用（禁用后无法登录，见 MessageConstant.ACCOUNT_LOCKED）',
    create_time DATETIME              DEFAULT NULL   COMMENT '创建时间',
    update_time DATETIME              DEFAULT NULL   COMMENT '最后修改时间',
    create_user BIGINT       UNSIGNED DEFAULT NULL   COMMENT '创建人 id（由 AutoFillAspect 自动填充）',
    update_user BIGINT       UNSIGNED DEFAULT NULL   COMMENT '最后修改人 id（由 AutoFillAspect 自动填充）',
    PRIMARY KEY (id),
    UNIQUE KEY idx_username (username)  -- 唯一索引：重复用户名插入会触发全局异常处理器里的 Duplicate entry 分支
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '员工表';

-- ---------------------------------------------------------------------
-- 2. 分类表（type=1 饮品分类 / type=2 套餐分类；课程里叫"菜品分类"，山月文案叫"饮品"）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS category;
CREATE TABLE category (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    type        INT                  DEFAULT NULL   COMMENT '类型 1 饮品分类 2 套餐分类',
    name        VARCHAR(32) NOT NULL                COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT 0      COMMENT '排序（管理端按 sort 倒序展示）',
    status      INT         NOT NULL DEFAULT 1      COMMENT '状态 0 禁用 1 启用（C 端只展示启用的分类）',
    create_time DATETIME             DEFAULT NULL   COMMENT '创建时间',
    update_time DATETIME             DEFAULT NULL   COMMENT '最后修改时间',
    create_user BIGINT UNSIGNED      DEFAULT NULL   COMMENT '创建人 id',
    update_user BIGINT UNSIGNED      DEFAULT NULL   COMMENT '最后修改人 id',
    PRIMARY KEY (id),
    UNIQUE KEY idx_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分类表';

-- ---------------------------------------------------------------------
-- 3. 饮品表（课程里叫"菜品"表 dish；山月 v1.2 起全链改名为 drink 更贴切茶饮业务，类名同步为 Drink，文案叫"饮品"）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS drink;
CREATE TABLE drink (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)   NOT NULL                COMMENT '饮品名称',
    category_id BIGINT        NOT NULL                COMMENT '分类 id（逻辑外键 → category.id）',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '基础价格（若有规格，最终售价 = 基础价 + 所选规格加价之和）',
    image       VARCHAR(200)           DEFAULT NULL   COMMENT '图片路径（/admin/common/upload 上传后返回的 URL）',
    description VARCHAR(400)           DEFAULT NULL   COMMENT '描述信息',
    status      INT           NOT NULL DEFAULT 1      COMMENT '售卖状态 0 停售 1 起售',
    create_time DATETIME               DEFAULT NULL   COMMENT '创建时间',
    update_time DATETIME               DEFAULT NULL   COMMENT '最后修改时间',
    create_user BIGINT UNSIGNED        DEFAULT NULL   COMMENT '创建人 id',
    update_user BIGINT UNSIGNED        DEFAULT NULL   COMMENT '最后修改人 id',
    PRIMARY KEY (id),
    KEY idx_drink_category_id (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '饮品表';

-- ---------------------------------------------------------------------
-- 4. 规格组表（山月核心 v2.0：替代课程的 dish_flavor 口味表）
--    一个饮品有多个规格组（杯型/温度/糖度），每个规格组下有多个规格选项
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS spec_group;
CREATE TABLE spec_group (
    id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    drink_id BIGINT      NOT NULL                COMMENT '所属饮品 id（逻辑外键 → drink.id）',
    name    VARCHAR(32) NOT NULL                COMMENT '规格组名称，如：杯型 / 温度 / 糖度',
    sort    INT         NOT NULL DEFAULT 0      COMMENT '排序（弹窗内从上到下的展示顺序）',
    PRIMARY KEY (id),
    KEY idx_spec_group_drink_id (drink_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '规格组表（山月 v2.0）';

-- ---------------------------------------------------------------------
-- 5. 规格选项表（规格组下的可选项，如"杯型"组下的 中杯/大杯/超大杯）
--    price_delta 是加价：最终售价 = drink.price + Σ(所选项的 price_delta)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS spec_option;
CREATE TABLE spec_option (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    group_id    BIGINT        NOT NULL                COMMENT '所属规格组 id（逻辑外键 → spec_group.id）',
    name        VARCHAR(32)   NOT NULL                COMMENT '选项名称，如：大杯 / 少糖',
    price_delta DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '加价金额（0 表示不加价；服务端下单时会按此重算金额）',
    sort        INT           NOT NULL DEFAULT 0      COMMENT '排序',
    PRIMARY KEY (id),
    KEY idx_spec_option_group_id (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '规格选项表（山月 v2.0）';

-- ---------------------------------------------------------------------
-- 6. 口味表（课程对照保留，山月业务请使用 spec_group/spec_option）
--    课程原版 DishDTO 里是 List<DishFlavor> flavors（本工程 v1.2 改名 DrinkDTO/DrinkFlavor），山月改为 List<SpecGroupDTO> specGroups，
--    DrinkVO 里两个都保留，便于你逐行对照课程代码过渡。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS drink_flavor;
CREATE TABLE drink_flavor (
    id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    drink_id BIGINT      NOT NULL                COMMENT '饮品 id',
    name    VARCHAR(32)          DEFAULT NULL   COMMENT '口味名称，如：辣度',
    value   VARCHAR(255)         DEFAULT NULL   COMMENT '口味可选值 JSON 数组，如：["不辣","微辣","中辣"]',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '口味表（课程对照保留）';

-- ---------------------------------------------------------------------
-- 7. 套餐表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS setmeal;
CREATE TABLE setmeal (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT        NOT NULL                COMMENT '分类 id',
    name        VARCHAR(64)   NOT NULL                COMMENT '套餐名称',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '套餐价格',
    status      INT           NOT NULL DEFAULT 1      COMMENT '售卖状态 0 停售 1 起售',
    description VARCHAR(400)           DEFAULT NULL   COMMENT '描述信息',
    image       VARCHAR(200)           DEFAULT NULL   COMMENT '图片路径',
    create_time DATETIME               DEFAULT NULL   COMMENT '创建时间',
    update_time DATETIME               DEFAULT NULL   COMMENT '最后修改时间',
    create_user BIGINT UNSIGNED        DEFAULT NULL   COMMENT '创建人 id',
    update_user BIGINT UNSIGNED        DEFAULT NULL   COMMENT '最后修改人 id',
    PRIMARY KEY (id),
    UNIQUE KEY idx_setmeal_name (name),
    KEY idx_setmeal_category_id (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '套餐表';

-- ---------------------------------------------------------------------
-- 8. 套餐-饮品关联表（多对多中间表）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS setmeal_drink;
CREATE TABLE setmeal_drink (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    setmeal_id BIGINT        NOT NULL                COMMENT '套餐 id',
    drink_id    BIGINT                 DEFAULT NULL   COMMENT '饮品 id',
    name       VARCHAR(32)            DEFAULT NULL   COMMENT '饮品名称（冗余字段，避免联表）',
    price      DECIMAL(10,2)          DEFAULT NULL   COMMENT '饮品原价（冗余字段）',
    copies     INT           NOT NULL DEFAULT 1      COMMENT '份数',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '套餐-饮品关联表';

-- ---------------------------------------------------------------------
-- 9. C 端用户表（微信小程序用户）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS user;
CREATE TABLE user (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid      VARCHAR(64) NOT NULL                COMMENT '微信用户唯一标识（登录时后端用 code 调 jscode2session 换取）',
    nick_name   VARCHAR(64)          DEFAULT NULL   COMMENT '昵称（山月 v2.0：UserLoginVO.nickName 展示用；列名 nick_name 配合驼峰映射对应实体 User.nickName）',
    name        VARCHAR(32)          DEFAULT NULL   COMMENT '姓名（课程同款字段保留）',
    phone       VARCHAR(100)         DEFAULT NULL   COMMENT '手机号',
    sex         VARCHAR(2)           DEFAULT NULL   COMMENT '性别 0 女 1 男',
    id_number   VARCHAR(18)          DEFAULT NULL   COMMENT '身份证号',
    avatar      VARCHAR(500)         DEFAULT NULL   COMMENT '头像 URL',
    create_time DATETIME             DEFAULT NULL   COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'C端用户表';

-- ---------------------------------------------------------------------
-- 10. 地址簿表（外送订单从这里快照收货信息）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS address_book;
CREATE TABLE address_book (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL                COMMENT '所属用户 id',
    consignee     VARCHAR(50)           DEFAULT NULL   COMMENT '收货人',
    phone         VARCHAR(100)          DEFAULT NULL   COMMENT '手机号',
    sex           VARCHAR(2)            DEFAULT NULL   COMMENT '性别 0 女 1 男',
    province_code VARCHAR(12)             DEFAULT NULL   COMMENT '省级区划编号',
    province_name VARCHAR(32)           DEFAULT NULL   COMMENT '省级名称',
    city_code     VARCHAR(12)           DEFAULT NULL   COMMENT '市级区划编号',
    city_name     VARCHAR(32)           DEFAULT NULL   COMMENT '市级名称',
    district_code VARCHAR(12)           DEFAULT NULL   COMMENT '区级区划编号',
    district_name VARCHAR(32)           DEFAULT NULL   COMMENT '区级名称',
    detail        VARCHAR(200)          DEFAULT NULL   COMMENT '详细地址',
    label         VARCHAR(100)          DEFAULT NULL   COMMENT '标签，如：家 / 公司',
    is_default    TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否默认地址 0 否 1 是',
    PRIMARY KEY (id),
    KEY idx_address_book_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '地址簿表';

-- ---------------------------------------------------------------------
-- 11. 购物车表（每个用户多行；同规格合并数量靠 spec_selected 快照比对）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS shopping_cart;
CREATE TABLE shopping_cart (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(64)            DEFAULT NULL   COMMENT '商品名称（冗余，下单时直接复制进订单明细）',
    user_id       BIGINT        NOT NULL                COMMENT '所属用户 id',
    drink_id       BIGINT                 DEFAULT NULL   COMMENT '饮品 id（与 setmeal_id 二选一）',
    setmeal_id    BIGINT                 DEFAULT NULL   COMMENT '套餐 id',
    drink_flavor   VARCHAR(200)           DEFAULT NULL   COMMENT '口味快照（课程 dish_flavor 同款字段，v1.2 改名）',
    spec_selected VARCHAR(500)           DEFAULT NULL   COMMENT '规格快照 JSON 数组（山月 v2.0），如：[{"optionId":2,"optionName":"大杯","priceDelta":3,...}]',
    number        INT           NOT NULL DEFAULT 1      COMMENT '数量',
    amount        DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '单价（含规格加价；服务端按 spec_option 表重算，不信任前端）',
    image         VARCHAR(200)           DEFAULT NULL   COMMENT '图片',
    create_time   DATETIME               DEFAULT NULL   COMMENT '加入时间',
    PRIMARY KEY (id),
    KEY idx_shopping_cart_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车表';

-- ---------------------------------------------------------------------
-- 12. 订单表（苍穹 6 状态不变：1待付款 2待接单 3已接单 4派送中·待取餐 5已完成 6已取消）
--     v2.0 新增：delivery_mode 双模式 / pickup_number 自选取餐号 / delivery_fee 外送费
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    number                  VARCHAR(50)            DEFAULT NULL   COMMENT '订单号（下单时间戳生成，见 OrderServiceImpl.submit）',
    status                  INT         NOT NULL DEFAULT 1        COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中(外送)·待取餐(自取) 5已完成 6已取消',
    user_id                 BIGINT      NOT NULL                  COMMENT '下单用户 id',
    address_book_id         BIGINT                 DEFAULT NULL   COMMENT '地址簿 id（外送必填；自取可为空）',
    order_time              DATETIME               DEFAULT NULL   COMMENT '下单时间',
    checkout_time           DATETIME               DEFAULT NULL   COMMENT '结账（支付成功）时间',
    pay_method              INT                    DEFAULT NULL   COMMENT '支付方式 1 微信 2 支付宝',
    pay_status              INT         NOT NULL DEFAULT 0        COMMENT '支付状态 0 未支付 1 已支付 2 退款',
    amount                  DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '实收金额（含外送费）',
    remark                  VARCHAR(100)           DEFAULT NULL   COMMENT '订单备注',
    user_name               VARCHAR(32)            DEFAULT NULL   COMMENT '下单用户名称',
    phone                   VARCHAR(100)           DEFAULT NULL   COMMENT '收货手机号（自取=联系手机）',
    address                 VARCHAR(255)           DEFAULT NULL   COMMENT '收货地址快照（外送时写入，自取为门店地址）',
    consignee               VARCHAR(32)            DEFAULT NULL   COMMENT '收货人快照',
    cancel_reason           VARCHAR(255)           DEFAULT NULL   COMMENT '订单取消原因',
    rejection_reason        VARCHAR(255)           DEFAULT NULL   COMMENT '订单拒绝原因（管理端拒单）',
    cancel_time             DATETIME               DEFAULT NULL   COMMENT '订单取消时间',
    estimated_delivery_time DATETIME               DEFAULT NULL   COMMENT '预计送达时间',
    delivery_status         INT                    DEFAULT NULL   COMMENT '配送状态 1 立即送出 0 选择具体时间',
    delivery_time           DATETIME               DEFAULT NULL   COMMENT '送达时间',
    pack_amount             INT                    DEFAULT NULL   COMMENT '打包费',
    tableware_number        INT                    DEFAULT NULL   COMMENT '餐具数量',
    tableware_status        INT                    DEFAULT NULL   COMMENT '餐具数量状态 1 按餐量提供 0 选择具体数量',
    delivery_mode           INT         NOT NULL DEFAULT 1        COMMENT '下单模式（山月 v2.0）1 门店自取 2 外送',
    pickup_number           VARCHAR(8)             DEFAULT NULL   COMMENT '取餐号（山月 v2.0，自取单生成，如 A01；Redis 按天自增）',
    delivery_fee            DECIMAL(10,2)          DEFAULT 0.00   COMMENT '外送费（山月 v2.0，外送固定 5 元，自取 0）',
    PRIMARY KEY (id),
    KEY idx_orders_number (number),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_order_time (order_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单表';

-- ---------------------------------------------------------------------
-- 13. 订单明细表（一单多商品；monthSales 月售统计就是 SUM 本表的 number）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS order_detail;
CREATE TABLE order_detail (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(64)            DEFAULT NULL   COMMENT '商品名称',
    order_id      BIGINT        NOT NULL                COMMENT '所属订单 id',
    drink_id       BIGINT                 DEFAULT NULL   COMMENT '饮品 id（月售统计 GROUP BY 的字段）',
    setmeal_id    BIGINT                 DEFAULT NULL   COMMENT '套餐 id',
    drink_flavor   VARCHAR(200)           DEFAULT NULL   COMMENT '口味快照（课程 dish_flavor 同款字段，v1.2 改名）',
    spec_selected VARCHAR(500)           DEFAULT NULL   COMMENT '规格快照 JSON 数组（山月 v2.0，下单瞬间的规格与加价留档）',
    number        INT           NOT NULL DEFAULT 1      COMMENT '数量',
    amount        DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '单价（含规格加价）',
    image         VARCHAR(200)           DEFAULT NULL   COMMENT '图片',
    PRIMARY KEY (id),
    KEY idx_order_detail_order_id (order_id),
    KEY idx_order_detail_drink_id (drink_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单明细表';
