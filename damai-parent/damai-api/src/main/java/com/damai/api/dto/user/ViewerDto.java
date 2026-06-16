package com.damai.api.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 观演人传输对象 —— 对应 DDL {@code t_viewer}。
 *
 * <p>用途：下单时需要校验/锁定观演人信息（PRD §7.1.5），
 * order 服务通过 Feign 调 user 服务获取观演人列表。
 *
 * <p>敏感字段：{@code idCardNo} 在跨服务传输时为加密值，展示时脱敏。
 */
@Data
public class ViewerDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 观演人 ID */
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 真实姓名 */
    private String realName;

    /** 证件类型：1身份证 2护照 3港澳通行证 4台胞证 */
    private Integer idCardType;

    /** 证件号（加密存储，脱敏展示） */
    private String idCardNo;

    /** 联系电话（可选） */
    private String mobile;
}
