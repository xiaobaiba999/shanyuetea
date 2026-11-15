-- =====================================================================
-- 山月茶咖 · 功能升级包 v2 建表与种子脚本（v2_features.sql）
-- 覆盖功能包：F1 轮播横幅 / F2 会员等级 / F4 券包 / F5 每日签到 / F6 评价墙
--（F7 AI 点单无表；F8 取餐推送走 WebSocket；F9 纯前端 —— 见 docs/v2-features.md）
--
-- 使用方法（README 流程之后追加执行）：
--   1. 先执行 schema.sql、data.sql（基础 13 张表 + v1 种子）；
--   2. USE shanyue; 再执行本文件：source v2_features.sql；
--   3. 重启后端即可联调（桩数据与种子数据一致，逐个替换 TODO 互不影响）。
--
-- 说明：
--   * 本文件对既有表零侵入：5 张新表 + user 表 ALTER 加两个字段，不改任何既有列；
--   * 每张表头标注对应功能包编号（F1/F2/F4/F5/F6），与 docs/v2-features.md 的
--     总览表、Java 控制器（controller/user 下 *Controller.java 的 TODO）三方对齐；
--   * 种子数据与前端 utils/mock-v2.js 一一对应（横幅 3 条、券 4 张、用户券 2 张、
--     他人评价 10 条、签到 5 天），桩换成真实现后前端无感切换；
--   * ⚠ 饮品 id 口径提醒：mock-v2.js 的演示菜单用 101~108 号段（小程序侧演示库），
--     而 data.sql 的 drink 表用 1~8 号段。review 种子的 drink_id 按 mock 原样写 101~107，
--     评价墙"按饮品筛选"（GET /user/review/wall?drinkId=）在联调时传的是 drink 表真实
--     id，不会命中这些氛围种子 —— 需要命中就把种子 drink_id 手工改成 data.sql 的真实
--     id（按 drink_name 对应关系改），不改也不影响默认评价墙展示（drinkName 为冗余字段）。
-- =====================================================================

USE shanyue;

-- ---------------------------------------------------------------------
-- 1. 轮播横幅表【F1】（对应 controller/user/BannerController.java 的 TODO）
--    C 端查询口径：status=1 且按 sort 升序（对照 mock 的 filter+sort）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS banner;
CREATE TABLE banner (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(64)  NOT NULL                COMMENT '主标题，如：桂花冰酿 · 秋日上新',
    subtitle    VARCHAR(64)           DEFAULT NULL   COMMENT '副标题，如：第二杯半价 · 限时一周',
    theme       VARCHAR(32)  NOT NULL                COMMENT '主题色（前端渐变卡配色）：pine 松绿 | gold 鎏金 | terracotta 陶土',
    image       VARCHAR(200)           DEFAULT NULL   COMMENT '图片 URL（空串时前端按 theme 渲染新中式渐变卡；管理端上传后回填）',
    link_type   VARCHAR(16)  NOT NULL DEFAULT 'page' COMMENT '跳转类型 page 小程序页面（预留 url 外链）',
    link_url    VARCHAR(200)           DEFAULT NULL   COMMENT '跳转地址，如 /pages/coupons/coupons',
    sort        INT          NOT NULL DEFAULT 0      COMMENT '排序（C 端按 sort 升序展示）',
    status      INT          NOT NULL DEFAULT 1      COMMENT '状态 0 下架 1 上架（C 端只展示上架）',
    create_time DATETIME               DEFAULT NULL   COMMENT '创建时间',
    update_time DATETIME               DEFAULT NULL   COMMENT '最后修改时间',
    create_user BIGINT UNSIGNED        DEFAULT NULL   COMMENT '创建人 id（AutoFillAspect 自动填充）',
    update_user BIGINT UNSIGNED        DEFAULT NULL   COMMENT '最后修改人 id（AutoFillAspect 自动填充）',
    PRIMARY KEY (id),
    KEY idx_banner_sort (sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '轮播横幅表（F1）';

-- 横幅种子 3 条（与 mock-v2.js SEED_BANNERS 一致；image 空串 → 前端按 theme 渐变卡渲染）
INSERT INTO banner (id, title, subtitle, theme, image, link_type, link_url, sort, status, create_time, update_time, create_user, update_user) VALUES
(1, '桂花冰酿 · 秋日上新', '第二杯半价 · 限时一周',     'pine',       '', 'page', '/pages/menu/menu',       1, 1, NOW(), NOW(), 1, 1),
(2, '每日签到 · 茶香不断', '连签 7 天额外得 50 积分',    'gold',       '', 'page', '/pages/checkin/checkin', 2, 1, NOW(), NOW(), 1, 1),
(3, '新人见面礼',          '满 30 减 8 券包待领取',      'terracotta', '', 'page', '/pages/coupons/coupons', 3, 1, NOW(), NOW(), 1, 1);

-- ---------------------------------------------------------------------
-- 2. 优惠券模板表【F4】（对应 controller/user/CouponController.java 的 TODO）
--    type：1 满减 | 2 折扣 | 3 限时秒杀满减；秒杀场次用 seckill 位标记（演示口径）
--    秒杀库存以 Redis 为准（key=seckill:stock:coupon:{id}），remain 列仅作预热前的初始值
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS coupon;
CREATE TABLE coupon (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(64)   NOT NULL                COMMENT '券名称，如：新人礼·满30减8',
    type             INT           NOT NULL DEFAULT 1      COMMENT '类型 1 满减 2 折扣 3 限时秒杀满减（本演示秒杀券 type=1 + seckill=1）',
    threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '使用门槛（满 X 元可用；0 表示无门槛，外送费不计入门槛基准）',
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '满减金额（type=1 时生效）',
    discount_rate    INT           NOT NULL DEFAULT 100    COMMENT '折扣率（type=2 时生效，88 表示 88 折即打 8.8 折）',
    total_days       INT           NOT NULL DEFAULT 30     COMMENT '领取后有效天数（领取时算 expire_date）',
    seckill          TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否秒杀券 0 否 1 是（秒杀券限量，领取走 Lua 原子扣减）',
    stock            INT           NOT NULL DEFAULT 0      COMMENT '秒杀总量（非秒杀券不限量，存 0）',
    remain           INT           NOT NULL DEFAULT 0      COMMENT '秒杀剩余库存（预热进 Redis 后以 Redis 为准，本列对账用）',
    status           INT           NOT NULL DEFAULT 1      COMMENT '状态 0 下架 1 上架（下架后 list 不展示、receive 报"券不存在或已下架"）',
    create_time      DATETIME               DEFAULT NULL   COMMENT '创建时间',
    update_time      DATETIME               DEFAULT NULL   COMMENT '最后修改时间',
    create_user      BIGINT UNSIGNED        DEFAULT NULL   COMMENT '创建人 id',
    update_user      BIGINT UNSIGNED        DEFAULT NULL   COMMENT '最后修改人 id',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券模板表（F4）';

-- 券模板种子 4 张（与 mock-v2.js SEED_COUPONS 一致）
INSERT INTO coupon (id, name, type, threshold_amount, discount_amount, discount_rate, total_days, seckill, stock, remain, status, create_time, update_time, create_user, update_user) VALUES
(1, '新人礼·满30减8',    1, 30.00,  8.00, 100, 30, 0,   0,   0, 1, NOW(), NOW(), 1, 1),
(2, '周五秒杀·满20减10', 1, 20.00, 10.00, 100,  7, 1, 100,  37, 1, NOW(), NOW(), 1, 1),
(3, '会员日·全场88折',    2,  0.00,  0.00,  88, 15, 0,   0,   0, 1, NOW(), NOW(), 1, 1),
(4, '老友回归·满50减15',  1, 50.00, 15.00, 100, 30, 0,   0,   0, 1, NOW(), NOW(), 1, 1);

-- ---------------------------------------------------------------------
-- 3. 用户优惠券表【F4】（一人一单的凭证表）
--    uk_user_coupon(user_id, coupon_id) 唯一索引是"一人一单"的最后一道防线：
--    Redis SETNX/Lua 挡住 99.9% 的并发，漏网的由唯一索引抛 Duplicate entry 兜底
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS user_coupon;
CREATE TABLE user_coupon (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键（前端契约里的 userCouponId，下单用券时传它）',
    user_id      BIGINT      NOT NULL                COMMENT '领取用户 id',
    coupon_id    BIGINT      NOT NULL                COMMENT '券模板 id（逻辑外键 → coupon.id）',
    status       INT         NOT NULL DEFAULT 1      COMMENT '状态 1 可用 2 已使用 3 已过期（懒加载比对 expire_date 置 3）',
    receive_time DATETIME             DEFAULT NULL   COMMENT '领取时间',
    used_time    DATETIME             DEFAULT NULL   COMMENT '使用时间（OrderServiceImpl.submit 用券时回填）',
    expire_date  DATE                 DEFAULT NULL   COMMENT '过期日期（领取时间 + coupon.total_days）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    KEY idx_user_coupon_coupon_id (coupon_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户优惠券表（F4）';

-- 用户券种子 2 张（演示用户 user_id=1，与 mock-v2.js ensureV2 一致）：
--   id 91：新人礼已领、可用（status=1）、27 天后过期；
--   id 92：老友回归已领、已使用（status=2）、9 天前用掉、18 天后过期。
INSERT INTO user_coupon (id, user_id, coupon_id, status, receive_time, used_time, expire_date) VALUES
(91, 1, 1, 1, DATE_ADD(NOW(), INTERVAL -3 DAY),  NULL,                           DATE_ADD(CURDATE(), INTERVAL 27 DAY)),
(92, 1, 4, 2, DATE_ADD(NOW(), INTERVAL -12 DAY), DATE_ADD(NOW(), INTERVAL -9 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY));

-- ---------------------------------------------------------------------
-- 4. 订单评价表【F6】（对应 controller/user/ReviewController.java 与
--    controller/admin/ReviewController.java 的 TODO）
--    一单一评双保险：Service 业务校验 + uk_review_order_id 唯一索引兜底
--    （order_id 允许 NULL：他人氛围种子不挂订单；MySQL 唯一索引对多个 NULL 不冲突）
--    reply / visible 两列是 B 端管理页的状态位（回复、隐藏/恢复）；隐藏后 C 端
--    评价墙与统计都当它不存在（软删除留痕，可恢复）
--    reply / visible 两列是 B 端管理页的状态位（回复、隐藏/恢复）；隐藏后 C 端
--    评价墙与统计都当它不存在（软删除留痕，可恢复）
--    tags 存法采用方案 1（JSON 字符串，毕设推荐）；方案 2 拆 review_tag 关联表见
--    docs/v2-features.md 的 F6 章节（MyBatis collection 一对多映射练习）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS review;
CREATE TABLE review (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL                COMMENT '评价人 id（氛围种子的 21~30 为虚拟用户，不在 user 表）',
    nick_name   VARCHAR(64)           DEFAULT NULL   COMMENT '评价人昵称（冗余快照：评价墙展示不联 user 表）',
    avatar_url  VARCHAR(500)          DEFAULT NULL   COMMENT '评价人头像 URL（冗余快照）',
    order_id    BIGINT                DEFAULT NULL   COMMENT '关联订单 id（本人评价必填且唯一，一单一评）',
    drink_id    BIGINT                DEFAULT NULL   COMMENT '饮品 id（订单明细第一行快照；号段口径见文件头 ⚠ 提醒）',
    drink_name  VARCHAR(64)           DEFAULT NULL   COMMENT '饮品名称（冗余快照）',
    rating      INT          NOT NULL DEFAULT 5      COMMENT '星级 1~5（服务端夹取）',
    content     VARCHAR(255) NOT NULL                COMMENT '评价内容（服务端截断 120 字）',
    tags        VARCHAR(500)          DEFAULT NULL   COMMENT '评价标签 JSON 数组字符串，如：["椰香浓","出餐快"]（最多 6 个）',
    images      VARCHAR(1000)         DEFAULT NULL   COMMENT '评价图 URL JSON 数组字符串（本演示恒为空数组）',
    like_count  INT          NOT NULL DEFAULT 0      COMMENT '点赞数',
    reply       VARCHAR(200)          DEFAULT NULL   COMMENT '商家回复（B 端一评一回复，可修改，≤50 字；C 端评价墙展示在评价下方）',
    visible     TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否展示 1 展示 0 已隐藏（B 端隐藏不当言论；隐藏后不参与 C 端展示与统计）',
    create_time DATETIME               DEFAULT NULL   COMMENT '评价时间（评价墙按它倒序）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_order_id (order_id),
    KEY idx_review_drink_id (drink_id),
    KEY idx_review_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单评价表（F6）';

-- 他人评价种子 10 条（前 8 条内容/标签/点赞数与 mock-v2.js SEED_REVIEWS 一字不差；
-- create_time 按执行日相对生成，保证评价墙永远"新鲜"）；后 2 条是 B 端管理演示行：
-- 109 = 3 星未回复 → 管理页「待回复」样例；110 = 2 星已隐藏 visible=0 → C 端真实现 WHERE visible=1 时不展示）
INSERT INTO review (id, user_id, nick_name, avatar_url, order_id, drink_id, drink_name, rating, content, tags, images, like_count, reply, visible, create_time) VALUES
(101, 21, '月下独酌', '', NULL, 101, '生椰拿铁',     5, '椰香很正，不齁甜，下午的续命全靠它。',        '["椰香浓","出餐快"]',           '[]', 23, '谢谢喜爱～欢迎常来 🌼', 1, DATE_ADD(NOW(), INTERVAL -1 DAY)),
(102, 22, '山间客',   '', NULL, 106, '黑糖波波奶茶', 5, '波波弹牙，黑糖挂壁看着就治愈，大杯满足。',    '["分量足","会回购"]',           '[]', 18, '', 1, DATE_ADD(NOW(), INTERVAL -2 DAY)),
(103, 23, '白鹭未眠', '', NULL, 102, '桂花冰酿拿铁', 4, '桂花香是真实桂花的那种香，就是杯子小了点。',  '["香气足"]',                     '[]',  9, '', 1, DATE_ADD(NOW(), INTERVAL -3 DAY)),
(104, 24, '南山南',   '', NULL, 104, '美式咖啡',     5, '双份浓缩够劲，加班救星，性价比无敌。',        '["提神醒脑","性价比"]',         '[]', 31, '', 1, DATE_ADD(NOW(), INTERVAL -4 DAY)),
(105, 25, '青梅煮酒', '', NULL, 107, '茉莉奶绿',     4, '茶底清清爽爽，饭后喝刚好，回甘明显。',        '["回甘明显","清爽解腻"]',       '[]', 12, '', 1, DATE_ADD(NOW(), INTERVAL -6 DAY)),
(106, 26, '拾月',     '', NULL, 103, '焦糖玛奇朵',   5, '层次做得很好，甜度选半糖刚刚好，颜值也在线。', '["颜值高","甜度刚好"]',         '[]', 15, '哈哈欢迎常来打卡 ✨', 1, DATE_ADD(NOW(), INTERVAL -8 DAY)),
(107, 27, '迟迟',     '', NULL, 105, '澳白',         5, '奶泡绵密入口很顺，热饮出品稳定。',            '["口感顺滑"]',                   '[]',  7, '', 1, DATE_ADD(NOW(), INTERVAL -10 DAY)),
(108, 28, '雾岛',     '', NULL, 101, '生椰拿铁',     4, '冰的更好喝，建议选去冰再少一档糖。',          '["椰香浓"]',                     '[]',  5, '', 1, DATE_ADD(NOW(), INTERVAL -12 DAY)),
(109, 29, '南风知我意', '', NULL, 104, '美式咖啡',   3, '工作日下午人也这么多，等了小二十分钟才出餐，建议错峰。', '["等太久"]', '[]', 3, '', 1, DATE_ADD(NOW(), INTERVAL -5 DAY)),
(110, 30, '匿名茶客', '', NULL, 106, '黑糖波波奶茶', 2, '餐车脏了都没擦……失望。',                    '["包装瑕疵"]', '[]', 0, '', 0, DATE_ADD(NOW(), INTERVAL -7 DAY));

-- ---------------------------------------------------------------------
-- 5. 签到流水表【F5】（兜底对账用；生产主存储是 Redis BITMAP，见下）
--    checkin:{userId}:{yyyyMM} 一月一个 key，SETBIT offset=dayOfMonth-1；
--    本表只作审计/对账（可双写可不写），跨月累计天数也可用 BITCOUNT 累加实现
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS checkin_log;
CREATE TABLE checkin_log (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT      NOT NULL                COMMENT '用户 id',
    checkin_date  DATE        NOT NULL                COMMENT '签到日期（yyyy-MM-dd）',
    points_gained INT         NOT NULL DEFAULT 5      COMMENT '本次签到积分（Lv2 青绿以上权益翻倍为 10）',
    create_time   DATETIME             DEFAULT NULL   COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkin_user_date (user_id, checkin_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '签到流水表（F5，Redis BITMAP 的对账兜底）';

-- 签到种子 5 天（演示用户 user_id=1：昨天起连签 3 天 + 更早零散 2 天，与 mock 口径一致）
INSERT INTO checkin_log (id, user_id, checkin_date, points_gained, create_time) VALUES
(1, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 5, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 9 HOUR)),
(2, 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 5, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 2 DAY), INTERVAL 9 HOUR)),
(3, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 5, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 DAY), INTERVAL 9 HOUR)),
(4, 1, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 5, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 DAY), INTERVAL 9 HOUR)),
(5, 1, DATE_SUB(CURDATE(), INTERVAL 9 DAY), 5, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 9 DAY), INTERVAL 9 HOUR));

-- ---------------------------------------------------------------------
-- 6. user 表加字段【F2 会员成长值 + F5/F6 积分账本】（对既有表唯一的一处增量改动）
--
--    方案 A（本脚本采用，毕设推荐）：冗余字段，实时 UPDATE。
--      计算点只挂三处（与成长值规则一一对应）：
--        ① OrderServiceImpl.paySuccess    → growth += 订单金额 * 2（下单 1 元 = 2）
--        ② CheckinService.sign            → growth += 5
--        ③ ReviewService.submit           → growth += 10
--      优点：查询 O(1)；缺点：算错一次永久错（冗余的代价，答辩讲清取舍即加分）。
--
--    方案 B（备选，无冗余）：user 表不加 growth，查询时聚合三表（视图写法如下，
--      想体验方案 B 就注释掉上面的 ALTER，取消下面 CREATE VIEW 的注释）：
--        -- CREATE VIEW v_user_growth AS
--        -- SELECT u.id AS user_id,
--        --        (SELECT IFNULL(SUM(o.amount), 0) FROM orders o
--        --          WHERE o.user_id = u.id AND o.pay_status = 1 AND o.status <> 6) * 2
--        --      + (SELECT COUNT(*) FROM checkin_log c WHERE c.user_id = u.id) * 5
--        --      + (SELECT COUNT(*) FROM review r WHERE r.user_id = u.id) * 10 AS growth
--        -- FROM user u;
--      优点：数据永远自洽；缺点：订单量大后聚合慢，SQL 复杂。
-- ---------------------------------------------------------------------
ALTER TABLE user
    ADD COLUMN growth INT NOT NULL DEFAULT 0 COMMENT '会员成长值（方案 A 冗余字段：下单1元+2/签到+5/评价+10；等级阈值 0/200/600/1200/2000 → Lv1~Lv5）' AFTER avatar,
    ADD COLUMN points INT NOT NULL DEFAULT 0 COMMENT '山月积分（签到+5/评价+10；下单 1 元得 1 积分为演示口径）' AFTER growth;

-- 演示用户对齐 mock 口径：积分 120、成长值 308（→ Lv2 青绿·抽枝，距 Lv3 松风差 292）
UPDATE user SET points = 120, growth = 308 WHERE id = 1;

-- ---------------------------------------------------------------------
-- 7. Redis 预热说明（可选执行，非 SQL：给 F4 秒杀券灌初始库存）
--    redis-cli 执行：SET seckill:stock:coupon:2 100
--    （coupon.remain=37 是"已被抢 63 张"的演示快照，预热时想还原现场就 SET 37）
--    签到 BITMAP（F5）无需预热：当月 key 不存在即"一天都没签"，SETBIT 自动建 key。
-- ---------------------------------------------------------------------
