package com.damai.user.service;

import com.damai.user.entity.Viewer;

import java.util.List;
import java.util.Map;

/**
 * 用户服务接口
 *
 * @author damai
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param mobile   手机号
     * @param password 密码
     * @param code     短信验证码
     * @return 包含 accessToken、refreshToken、用户信息的 Map
     */
    Map<String, Object> register(String mobile, String password, String code);

    /**
     * 密码登录
     *
     * @param mobile   手机号
     * @param password 密码
     * @return 包含 accessToken、refreshToken、用户信息的 Map
     */
    Map<String, Object> login(String mobile, String password);

    /**
     * 验证码登录
     *
     * @param mobile 手机号
     * @param code   短信验证码
     * @return 包含 accessToken、refreshToken、用户信息的 Map
     */
    Map<String, Object> loginByCode(String mobile, String code);

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新令牌
     * @return 包含新 accessToken 的 Map
     */
    Map<String, Object> refreshToken(String refreshToken);

    /**
     * 用户登出
     *
     * @param userId 用户ID
     */
    void logout(Long userId);

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息（脱敏）
     */
    Map<String, Object> getUserInfo(Long userId);

    /**
     * 更新个人信息
     *
     * @param userId   用户ID
     * @param nickname 昵称
     * @param avatar   头像
     * @param gender   性别
     */
    void updateProfile(Long userId, String nickname, String avatar, Integer gender);

    /**
     * 获取观演人列表
     *
     * @param userId 用户ID
     * @return 观演人列表
     */
    List<Viewer> getViewerList(Long userId);

    /**
     * 新增观演人
     *
     * @param userId   用户ID
     * @param viewer   观演人信息
     */
    void addViewer(Long userId, Viewer viewer);

    /**
     * 更新观演人
     *
     * @param userId   用户ID
     * @param viewerId 观演人ID
     * @param viewer   观演人信息
     */
    void updateViewer(Long userId, Long viewerId, Viewer viewer);

    /**
     * 删除观演人
     *
     * @param userId   用户ID
     * @param viewerId 观演人ID
     */
    void deleteViewer(Long userId, Long viewerId);
}
