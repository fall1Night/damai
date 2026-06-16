package com.damai.api.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息传输对象 —— 对应 DDL {@code t_user}，用于服务间用户信息查询。
 *
 * <p>注意：不含 {@code password} 字段（敏感信息不跨服务传递）。
 * 字段与 {@code t_user} 表对齐，去除了内部字段（is_deleted 等）。
 *
 * @see com.damai.api.client.user.UserFeignClient
 */
@Data
public class UserDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 手机号（注册账号） */
    private String mobile;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 状态：1正常 0禁用 -1已注销（UserStatusEnum） */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;
}
