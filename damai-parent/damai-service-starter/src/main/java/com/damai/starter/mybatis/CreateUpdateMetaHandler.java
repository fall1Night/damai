package com.damai.starter.mybatis;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器 —— 填充 create_time / update_time。
 *
 * <p>对应 DDL 统一通用字段约定：
 * <ul>
 *   <li>{@code create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP}</li>
 *   <li>{@code update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP}</li>
 * </ul>
 *
 * <p>插入时自动设置 create_time 和 update_time；
 * 更新时自动刷新 update_time。
 * 实体字段需标注 {@code @TableField(fill = FieldFill.INSERT)} /
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)}。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅处理值为 null 的字段，已有值不覆盖（支持业务手动设置特定时间）。</li>
 *   <li>使用 {@link LocalDateTime}（Jakarta EE / Java 17+ 标准时间类型）。</li>
 * </ul>
 *
 * @see MybatisPlusAutoConfiguration
 */
public class CreateUpdateMetaHandler implements org.apache.ibatis.reflection.MetaObjectHandler {

    /** 实体中 createTime 字段名（与 DDL / 实体类对齐） */
    private static final String CREATE_TIME = "createTime";

    /** 实体中 updateTime 字段名 */
    private static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, CREATE_TIME, LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime::now, LocalDateTime.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime::now, LocalDateTime.class);
    }
}
