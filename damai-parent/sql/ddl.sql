-- =============================================================================
-- 大麦票务订购与演出服务系统 —— 数据库 DDL 脚本
-- =============================================================================
-- 文档对应：
--   PRD §7 功能需求详述（各业务域表结构依据）
--   技术文档 §7.1 MySQL + ShardingSphere 分库分表
--
-- 分片策略（技术文档 §7.1.2）：
--   分片表：t_order / t_order_detail / t_pay
--     分片键：user_id（订单按 user_id 查询最高频，"我的订单"精准命中单分片）
--     分片算法：4 库 × 8 表 = 32 分片，取模路由
--     order_id：雪花算法，低位嵌入 user_id，确保与 user_id 同分片
--   非分片表：t_user / t_viewer / t_program / t_show / t_ticket_type / 基础数据表
--
-- 状态码与 Java 枚举严格对齐：
--   t_order.status          ← OrderStatusEnum（1待支付 2已支付 3已取消(手动) 4已取消(超时) 5已退款）
--   t_pay.status            ← PayStatusEnum  （1待支付 2成功 3失败 4已关闭 5已退款）
--   t_program.status        ← ProgramStatusEnum（0草稿 1在售 2已下架 3已结束）
--   t_user.status           ← UserStatusEnum （1正常 0禁用 -1已注销）
--
-- 通用字段约定（所有表）：
--   id            BIGINT       主键（雪花算法 / 雪花嵌入 user_id，由应用层生成）
--   create_time   DATETIME     创建时间（MyBatis-Plus 自动填充）
--   update_time   DATETIME     更新时间（MyBatis-Plus 自动填充）
--   is_deleted    TINYINT      逻辑删除标记（0未删除 1已删除，MyBatis-Plus 全局逻辑删除）
-- =============================================================================

-- 库字符集统一为 utf8mb4，支持 emoji 与生僻字（艺人名、场馆名等）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =============================================================================
-- 一、用户域（damai-user，非分片，单库）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 用户表 t_user
-- PRD §7.1.1 注册 / §7.1.2 登录 / §7.1.4 个人信息维护
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`              BIGINT       NOT NULL                COMMENT '用户ID（雪花算法）',
    `mobile`          VARCHAR(20)  NOT NULL                COMMENT '手机号（注册账号，全局唯一）',
    `password`        VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 加盐哈希）',
    `nickname`        VARCHAR(50)           DEFAULT NULL   COMMENT '昵称',
    `avatar`          VARCHAR(255)          DEFAULT NULL   COMMENT '头像 URL',
    `email`           VARCHAR(100)          DEFAULT NULL   COMMENT '邮箱',
    `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1正常 0禁用 -1已注销（UserStatusEnum）',
    `last_login_time` DATETIME              DEFAULT NULL   COMMENT '最近登录时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mobile` (`mobile`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';


-- -----------------------------------------------------------------------------
-- 2. 观演人表 t_viewer
-- PRD §7.1.5 实名信息维护（观演人：姓名 + 证件号，用于实名购票校验）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_viewer`;
CREATE TABLE `t_viewer` (
    `id`             BIGINT       NOT NULL                COMMENT '观演人ID（雪花算法）',
    `user_id`        BIGINT       NOT NULL                COMMENT '所属用户ID',
    `real_name`      VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    `id_card_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '证件类型：1身份证 2护照 3港澳通行证 4台胞证',
    `id_card_no`     VARCHAR(64)  NOT NULL                COMMENT '证件号（加密存储，脱敏展示）',
    `mobile`         VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话（可选）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_idcard` (`user_id`, `id_card_type`, `id_card_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '观演人表';


-- =============================================================================
-- 二、节目域（damai-program，非分片，单库）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 3. 节目表 t_program
-- PRD §7.2.1 首页 / §7.2.2 分类浏览 / §7.2.3 搜索 / §7.2.4 详情 / §7.2.6 发布上下架
-- 同步至 ES 索引 damai_program（见 es/program-mapping.json）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_program`;
CREATE TABLE `t_program` (
    `id`            BIGINT        NOT NULL                COMMENT '节目ID',
    `title`         VARCHAR(200)  NOT NULL                COMMENT '节目名称（例：周杰伦 嘉年华世界巡演 - 北京站）',
    `artist`        VARCHAR(200)           DEFAULT NULL   COMMENT '艺人/表演团体',
    `type_code`     VARCHAR(50)   NOT NULL                COMMENT '节目类型编码（关联 t_program_type，如 concert/play）',
    `city_code`     VARCHAR(20)   NOT NULL                COMMENT '城市编码（关联 t_region，如 beijing）',
    `city_name`     VARCHAR(50)            DEFAULT NULL   COMMENT '城市名称（冗余，便于展示）',
    `venue`         VARCHAR(200)           DEFAULT NULL   COMMENT '场馆名称',
    `venue_address` VARCHAR(500)           DEFAULT NULL   COMMENT '场馆地址',
    `description`   TEXT                   DEFAULT NULL   COMMENT '节目介绍',
    `poster_url`    VARCHAR(255)           DEFAULT NULL   COMMENT '海报图 URL',
    `price_min`     DECIMAL(10,2)          DEFAULT NULL   COMMENT '最低票价（冗余，便于筛选/排序，同步ES）',
    `price_max`     DECIMAL(10,2)          DEFAULT NULL   COMMENT '最高票价（冗余，同步ES）',
    `show_start`    DATETIME               DEFAULT NULL   COMMENT '演出开始时间（首场）',
    `show_end`      DATETIME               DEFAULT NULL   COMMENT '演出结束时间（末场）',
    `sale_start`    DATETIME               DEFAULT NULL   COMMENT '开售时间（抢票起跑点）',
    `status`        TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0草稿 1在售 2已下架 3已结束（ProgramStatusEnum）',
    `heat`          INT          NOT NULL DEFAULT 0       COMMENT '热度值（同步ES，用于排序）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_type_city` (`type_code`, `city_code`),
    KEY `idx_sale_start` (`sale_start`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '节目表';


-- -----------------------------------------------------------------------------
-- 4. 场次表 t_show
-- PRD §4 术语：节目下的具体时间场次（如 2026-07-01 19:30 场）
-- 一个节目可有多场次
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_show`;
CREATE TABLE `t_show` (
    `id`            BIGINT       NOT NULL                COMMENT '场次ID',
    `program_id`    BIGINT       NOT NULL                COMMENT '所属节目ID',
    `show_time`     DATETIME     NOT NULL                COMMENT '演出时间（如 2026-07-01 19:30:00）',
    `sale_start`    DATETIME              DEFAULT NULL   COMMENT '本场开售时间',
    `sale_end`      DATETIME              DEFAULT NULL   COMMENT '本场停售时间',
    `seat_mode`     TINYINT      NOT NULL DEFAULT 1      COMMENT '售卖模式：1票档级(先到先得) 2座位级(自选)（PRD §7.2.5）',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '场次状态：0未开售 1在售 2已停售 3已结束',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_program_id` (`program_id`),
    KEY `idx_show_time` (`show_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '场次表';


-- -----------------------------------------------------------------------------
-- 5. 票档表 t_ticket_type
-- PRD §4 术语：场次下的票价档位（如 VIP 1880 / 980 / 580 / 380）
-- 库存预扣 Key：damai:stock:{showId}:{ticketTypeId}（RedisKeyConstant.STOCK）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_ticket_type`;
CREATE TABLE `t_ticket_type` (
    `id`            BIGINT        NOT NULL                COMMENT '票档ID',
    `show_id`       BIGINT        NOT NULL                COMMENT '所属场次ID',
    `program_id`    BIGINT        NOT NULL                COMMENT '所属节目ID（冗余，便于查询）',
    `name`          VARCHAR(50)   NOT NULL                COMMENT '票档名称（如 VIP / 980 / 580）',
    `price`         DECIMAL(10,2) NOT NULL                COMMENT '票价（元）',
    `total_stock`   INT           NOT NULL DEFAULT 0      COMMENT '总库存（DB 权威值，预热时写 Redis）',
    `sale_stock`    INT           NOT NULL DEFAULT 0      COMMENT '可售库存（DB 兜底，L3 条件更新：stock - ? >= 0）',
    `locked_stock`  INT           NOT NULL DEFAULT 0      COMMENT '预扣锁定库存（下单未支付占用）',
    `sort_order`    INT           NOT NULL DEFAULT 0      COMMENT '排序值（越小越靠前）',
    `status`        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停售',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`    TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_show_id` (`show_id`),
    KEY `idx_program_id` (`program_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '票档表';


-- =============================================================================
-- 三、订单域（damai-order，分片表）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 6. 订单表 t_order（分片表）
-- 技术文档 §7.1.2：分片键 user_id，4库×8表=32分片，取模路由
-- 注意：order_id 为雪花算法，低位嵌入 user_id，保证与 user_id 同分片
--       （避免 t_order 与 t_order_detail join 跨库）
-- 状态码 ← OrderStatusEnum（1待支付 2已支付 3已取消(手动) 4已取消(超时) 5已退款）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id`              BIGINT        NOT NULL                COMMENT '订单ID（雪花算法，低位嵌入 user_id）',
    `user_id`         BIGINT        NOT NULL                COMMENT '用户ID（分片键，按此取模路由）',
    `program_id`      BIGINT        NOT NULL                COMMENT '节目ID',
    `show_id`         BIGINT        NOT NULL                COMMENT '场次ID',
    `ticket_type_id`  BIGINT        NOT NULL                COMMENT '票档ID',
    `quantity`        INT           NOT NULL                COMMENT '购买数量',
    `total_amount`    DECIMAL(12,2) NOT NULL                COMMENT '订单总金额（元）',
    `status`          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1待支付 2已支付 3已取消(手动) 4已取消(超时) 5已退款（OrderStatusEnum）',
    `viewer_info`     VARCHAR(1000)          DEFAULT NULL   COMMENT '观演人信息快照（JSON，下单时锁定）',
    `pay_deadline`    DATETIME               DEFAULT NULL   COMMENT '支付截止时间（下单时间 + 15分钟）',
    `pay_time`        DATETIME               DEFAULT NULL   COMMENT '实际支付时间',
    `cancel_reason`   VARCHAR(200)           DEFAULT NULL   COMMENT '取消原因（手动取消/超时）',
    `cancel_time`     DATETIME               DEFAULT NULL   COMMENT '取消时间',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单表（分片，分片键 user_id）';


-- -----------------------------------------------------------------------------
-- 7. 订单明细表 t_order_detail（分片表，与 t_order 同分片组）
-- 技术文档 §7.1.2：与主表绑定，同分片组
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order_detail`;
CREATE TABLE `t_order_detail` (
    `id`              BIGINT        NOT NULL                COMMENT '明细ID（雪花算法）',
    `order_id`        BIGINT        NOT NULL                COMMENT '订单ID（与 t_order 同分片组）',
    `user_id`         BIGINT        NOT NULL                COMMENT '用户ID（冗余分片键，保证同分片路由）',
    `program_id`      BIGINT        NOT NULL                COMMENT '节目ID',
    `program_title`   VARCHAR(200)  NOT NULL                COMMENT '节目名称（快照）',
    `show_id`         BIGINT        NOT NULL                COMMENT '场次ID',
    `show_time`       DATETIME      NOT NULL                COMMENT '演出时间（快照）',
    `ticket_type_id`  BIGINT        NOT NULL                COMMENT '票档ID',
    `ticket_type_name` VARCHAR(50)  NOT NULL                COMMENT '票档名称（快照）',
    `venue`           VARCHAR(200)           DEFAULT NULL   COMMENT '场馆名称（快照）',
    `unit_price`      DECIMAL(10,2) NOT NULL                COMMENT '单价（快照）',
    `quantity`        INT           NOT NULL                COMMENT '数量',
    `sub_amount`      DECIMAL(12,2) NOT NULL                COMMENT '小计金额',
    `viewer_name`     VARCHAR(50)            DEFAULT NULL   COMMENT '观演人姓名（每张票一人，可分行存储）',
    `viewer_id_card`  VARCHAR(64)            DEFAULT NULL   COMMENT '观演人证件号（加密）',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单明细表（分片，与 t_order 同分片组）';


-- =============================================================================
-- 四、支付域（damai-pay，分片表，与 t_order 同分片组）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 8. 支付单表 t_pay（分片表）
-- 技术文档 §7.1.2：分片键 order_id，与 t_order 同分片组
-- 支付为模拟实现（PRD §2.3 Non-Goals：不接真实支付通道）
-- 状态码 ← PayStatusEnum（1待支付 2成功 3失败 4已关闭 5已退款）
-- 幂等：回调以 pay_no 为幂等键（RedisKeyConstant.PAY_CALLBACK_IDEMPOTENT）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_pay`;
CREATE TABLE `t_pay` (
    `id`             BIGINT        NOT NULL                COMMENT '支付单ID（雪花算法）',
    `pay_no`         VARCHAR(64)   NOT NULL                COMMENT '支付单号（业务唯一，回调幂等键）',
    `order_id`       BIGINT        NOT NULL                COMMENT '订单ID（分片键，与 t_order 同分片组）',
    `user_id`        BIGINT        NOT NULL                COMMENT '用户ID（冗余分片键，保证同分片路由）',
    `pay_amount`     DECIMAL(12,2) NOT NULL                COMMENT '支付金额（元）',
    `pay_method`     TINYINT       NOT NULL DEFAULT 1      COMMENT '支付方式：1微信(模拟) 2支付宝(模拟) 3余额(模拟)',
    `status`         TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1待支付 2成功 3失败 4已关闭 5已退款（PayStatusEnum）',
    `pay_url`        VARCHAR(500)           DEFAULT NULL   COMMENT '模拟支付链接/参数（JSON）',
    `callback_time`  DATETIME               DEFAULT NULL   COMMENT '回调到达时间',
    `pay_time`       DATETIME               DEFAULT NULL   COMMENT '支付成功时间',
    `expire_time`    DATETIME               DEFAULT NULL   COMMENT '支付单过期时间（= 订单剩余有效时间）',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_callback` (`status`, `callback_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付单表（分片，与 t_order 同分片组）';


-- =============================================================================
-- 五、基础数据域（damai-base，非分片，单库）
-- PRD §7.5：数据量小、读多写少，全量入 Redis 缓存
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 9. 节目类型表 t_program_type
-- PRD §7.5 / UC-B-01：演唱会/话剧/体育/亲子等
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_program_type`;
CREATE TABLE `t_program_type` (
    `id`           BIGINT       NOT NULL                COMMENT '主键',
    `code`         VARCHAR(50)  NOT NULL                COMMENT '类型编码（如 concert/play/sports，业务唯一）',
    `name`         VARCHAR(50)  NOT NULL                COMMENT '类型名称（如 演唱会/话剧/体育赛事）',
    `icon_url`     VARCHAR(255)          DEFAULT NULL   COMMENT '图标 URL',
    `sort_order`   INT          NOT NULL DEFAULT 0      COMMENT '排序值',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '节目类型表';


-- -----------------------------------------------------------------------------
-- 10. 地区表 t_region
-- PRD §7.5 / UC-B-02：城市与场馆地区
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_region`;
CREATE TABLE `t_region` (
    `id`           BIGINT       NOT NULL                COMMENT '主键',
    `code`         VARCHAR(20)  NOT NULL                COMMENT '地区编码（如 beijing/shanghai，业务唯一）',
    `name`         VARCHAR(50)  NOT NULL                COMMENT '地区名称（如 北京/上海）',
    `parent_code`  VARCHAR(20)           DEFAULT NULL   COMMENT '父级编码（支持省市两级）',
    `level`        TINYINT      NOT NULL DEFAULT 1      COMMENT '层级：1省/直辖市 2市/区',
    `sort_order`   INT          NOT NULL DEFAULT 0      COMMENT '排序值',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_parent_code` (`parent_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '地区表';


-- -----------------------------------------------------------------------------
-- 11. 轮播图表 t_banner
-- PRD §7.2.1 首页推荐 / UC-B-03：首页 Banner
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_banner`;
CREATE TABLE `t_banner` (
    `id`           BIGINT       NOT NULL                COMMENT '主键',
    `title`        VARCHAR(100) NOT NULL                COMMENT '标题',
    `image_url`    VARCHAR(255) NOT NULL                COMMENT '图片 URL',
    `link_url`     VARCHAR(500)          DEFAULT NULL   COMMENT '点击跳转链接',
    `channel`      VARCHAR(50)  NOT NULL DEFAULT 'home' COMMENT '所属频道（home/concert/play 等）',
    `city_code`    VARCHAR(20)           DEFAULT NULL   COMMENT '适用城市（NULL 表示全国）',
    `sort_order`   INT          NOT NULL DEFAULT 0      COMMENT '排序值（越小越靠前）',
    `start_time`   DATETIME              DEFAULT NULL   COMMENT '展示开始时间',
    `end_time`     DATETIME              DEFAULT NULL   COMMENT '展示结束时间',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_channel_city` (`channel`, `city_code`),
    KEY `idx_sort_status` (`status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '轮播图表';


-- -----------------------------------------------------------------------------
-- 12. 字典表 t_dict
-- PRD §7.5 / UC-B-04：通用枚举/配置字典
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_dict`;
CREATE TABLE `t_dict` (
    `id`           BIGINT       NOT NULL                COMMENT '主键',
    `dict_code`    VARCHAR(50)  NOT NULL                COMMENT '字典编码（如 id_card_type / pay_method）',
    `item_value`   VARCHAR(50)  NOT NULL                COMMENT '字典项值（如 1/2/3）',
    `item_label`   VARCHAR(100) NOT NULL                COMMENT '字典项标签（如 身份证/护照）',
    `sort_order`   INT          NOT NULL DEFAULT 0      COMMENT '排序值',
    `remark`       VARCHAR(200)          DEFAULT NULL   COMMENT '备注',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item` (`dict_code`, `item_value`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典表';


-- =============================================================================
-- 六、基础设施表（非分片，单库）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 13. 本地消息表 t_local_message
-- 技术文档 §8.3.3：本地消息表 + 定时扫描投递，保证"业务成功 → 消息必达"
-- 使用场景：下单时与订单同一本地事务写入消息记录，定时任务扫描投递 Kafka
-- 消息状态机：0待发送 1已发送 2已确认 3发送失败
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_local_message`;
CREATE TABLE `t_local_message` (
    `id`             BIGINT       NOT NULL                COMMENT '主键',
    `message_id`     VARCHAR(64)  NOT NULL                COMMENT '消息业务唯一ID（幂等键，UUID）',
    `topic`          VARCHAR(100) NOT NULL                COMMENT '目标 Kafka Topic',
    `biz_key`        VARCHAR(64)           DEFAULT NULL   COMMENT '业务键（如 orderId，便于查询）',
    `message_body`   TEXT         NOT NULL                COMMENT '消息体（JSON）',
    `retry_count`    INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0待发送 1已发送 2已确认 3失败',
    `next_retry_time` DATETIME             DEFAULT NULL   COMMENT '下次重试时间（退避策略）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP    COMMENT '更新时间',
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_status_retry` (`status`, `next_retry_time`),
    KEY `idx_biz_key` (`biz_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '本地消息表（事务消息，保证最终一致）';


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- DDL 结束
-- =============================================================================
-- 备注：
--   1. 分片表（t_order / t_order_detail / t_pay）的库表路由规则在 ShardingSphere
--      配置（ShardingSphereConfig.java）中定义，此处仅建表结构。
--      生产环境按"4 库 × 8 表"分别在 4 个库各建 8 张物理表。
--   2. 字段类型与 Java 实体（待生成）的映射：
--        BIGINT          ↔ Long
--        VARCHAR         ↔ String
--        DECIMAL(12,2)   ↔ BigDecimal
--        DATETIME        ↔ LocalDateTime
--        TINYINT/INT     ↔ Integer
--   3. 通用字段 create_time / update_time 由 service-starter 的 MyBatis-Plus
--      自动填充（MetaObjectHandler）维护，is_deleted 为全局逻辑删除字段。
-- =============================================================================
