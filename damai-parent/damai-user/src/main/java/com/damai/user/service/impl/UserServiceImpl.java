package com.damai.user.service.impl;

import com.damai.common.constants.RedisKeyConstant;
import com.damai.common.enums.ErrorCode;
import com.damai.common.exception.BizException;
import com.damai.user.entity.User;
import com.damai.user.entity.Viewer;
import com.damai.user.manager.UserManager;
import com.damai.user.manager.ViewerManager;
import com.damai.user.service.UserService;
import com.damai.user.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 *
 * @author damai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserManager userManager;
    private final ViewerManager viewerManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 短信验证码缓存前缀
     */
    private static final String SMS_CODE_PREFIX = "sms:code:";

    /**
     * RefreshToken 缓存前缀
     */
    private static final String REFRESH_TOKEN_PREFIX = "user:refresh:";

    /**
     * RefreshToken 缓存过期时间（秒）
     */
    private static final int REFRESH_TOKEN_EXPIRE_SECONDS = 604800; // 7 天

    /**
     * 观演人最大数量限制
     */
    private static final int MAX_VIEWER_COUNT = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(String mobile, String password, String code) {
        // 1. 验证码校验
        validateSmsCode(mobile, code);

        // 2. 布隆过滤器快速判断手机号是否已注册
        if (userManager.mightExistByMobile(mobile)) {
            // 布隆过滤器判断可能存在，再查 DB 确认
            User existingUser = userManager.getByMobile(mobile);
            if (existingUser != null) {
                throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
            }
        }

        // 3. 再次确认手机号唯一（防止布隆过滤器误判）
        User existingUser = userManager.getByMobile(mobile);
        if (existingUser != null) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 4. 密码加密
        String encodedPassword = passwordEncoder.encode(password);

        // 5. 创建用户
        User user = new User();
        user.setMobile(mobile);
        user.setPassword(encodedPassword);
        user.setNickname("用户" + mobile.substring(7)); // 默认昵称
        user.setGender(0);
        user.setStatus(0);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userManager.insert(user);

        // 6. 将手机号添加到布隆过滤器
        userManager.addMobileToBloomFilter(mobile);

        // 7. 清除验证码
        clearSmsCode(mobile);

        // 8. 签发 JWT
        String accessToken = jwtUtil.generateAccessToken(user.getId(), mobile);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), mobile);

        // 9. 存储 RefreshToken 到 Redis
        saveRefreshToken(user.getId(), refreshToken);

        // 10. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getId());
        result.put("mobile", mobile);
        result.put("nickname", user.getNickname());
        return result;
    }

    @Override
    public Map<String, Object> login(String mobile, String password) {
        // 1. 检查是否被锁定
        if (userManager.isLoginLocked(mobile)) {
            throw new BizException(ErrorCode.USER_LOGIN_LOCKED, "登录失败次数过多，账号已被锁定15分钟");
        }

        // 2. 查询用户
        User user = userManager.getByMobile(mobile);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 校验用户状态
        if (user.getStatus() != 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        // 4. 密码比对
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 密码错误，增加失败计数
            long failCount = userManager.incrLoginFailCount(mobile);
            if (failCount >= 5) {
                throw new BizException(ErrorCode.USER_LOGIN_LOCKED, "登录失败次数过多，账号已被锁定15分钟");
            }
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR, "密码错误，还剩" + (5 - failCount) + "次机会");
        }

        // 5. 登录成功，清除失败计数
        userManager.clearLoginFailCount(mobile);

        // 6. 签发 JWT
        String accessToken = jwtUtil.generateAccessToken(user.getId(), mobile);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), mobile);

        // 7. 存储 RefreshToken 到 Redis
        saveRefreshToken(user.getId(), refreshToken);

        // 8. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getId());
        result.put("mobile", mobile);
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        return result;
    }

    @Override
    public Map<String, Object> loginByCode(String mobile, String code) {
        // 1. 验证码校验
        validateSmsCode(mobile, code);

        // 2. 查询用户
        User user = userManager.getByMobile(mobile);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 校验用户状态
        if (user.getStatus() != 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        // 4. 清除验证码
        clearSmsCode(mobile);

        // 5. 签发 JWT
        String accessToken = jwtUtil.generateAccessToken(user.getId(), mobile);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), mobile);

        // 6. 存储 RefreshToken 到 Redis
        saveRefreshToken(user.getId(), refreshToken);

        // 7. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getId());
        result.put("mobile", mobile);
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        return result;
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        // 1. 解析 RefreshToken
        Claims claims = jwtUtil.parseToken(refreshToken);
        if (claims == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = claims.get("userId", Long.class);
        String mobile = claims.get("mobile", String.class);

        // 2. 校验 RefreshToken 是否在 Redis 中（未被吊销）
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(
                REFRESH_TOKEN_PREFIX + userId);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "RefreshToken 已失效，请重新登录");
        }

        // 3. 签发新的 AccessToken
        String newAccessToken = jwtUtil.generateAccessToken(userId, mobile);

        // 4. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", refreshToken); // RefreshToken 不变
        return result;
    }

    @Override
    public void logout(Long userId) {
        // 删除 Redis 中的 RefreshToken（吊销）
        stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
        log.info("用户登出，已吊销 RefreshToken，userId={}", userId);
    }

    @Override
    public Map<String, Object> getUserInfo(Long userId) {
        User user = userManager.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 脱敏处理
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("mobile", desensitizeMobile(user.getMobile()));
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("gender", user.getGender());
        result.put("createTime", user.getCreateTime());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, String nickname, String avatar, Integer gender) {
        User user = userManager.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (gender != null) {
            user.setGender(gender);
        }
        user.setUpdateTime(LocalDateTime.now());
        userManager.update(user);
    }

    @Override
    public List<Viewer> getViewerList(Long userId) {
        return viewerManager.listByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addViewer(Long userId, Viewer viewer) {
        // 校验观演人数量限制
        int count = viewerManager.countByUserId(userId);
        if (count >= MAX_VIEWER_COUNT) {
            throw new BizException(ErrorCode.VIEWER_LIMIT_EXCEEDED);
        }

        // 如果是第一个观演人，设为默认
        if (count == 0) {
            viewer.setIsDefault(1);
        } else {
            viewer.setIsDefault(0);
        }

        viewer.setUserId(userId);
        viewer.setDeleted(0);
        viewer.setCreateTime(LocalDateTime.now());
        viewer.setUpdateTime(LocalDateTime.now());
        viewerManager.insert(viewer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateViewer(Long userId, Long viewerId, Viewer viewer) {
        // 校验观演人归属
        Viewer existingViewer = viewerManager.getByIdAndUserId(viewerId, userId);
        if (existingViewer == null) {
            throw new BizException(ErrorCode.VIEWER_NOT_FOUND);
        }

        if (viewer.getName() != null) {
            existingViewer.setName(viewer.getName());
        }
        if (viewer.getIdType() != null) {
            existingViewer.setIdType(viewer.getIdType());
        }
        if (viewer.getIdNo() != null) {
            existingViewer.setIdNo(viewer.getIdNo());
        }
        if (viewer.getMobile() != null) {
            existingViewer.setMobile(viewer.getMobile());
        }
        existingViewer.setUpdateTime(LocalDateTime.now());
        viewerManager.update(existingViewer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteViewer(Long userId, Long viewerId) {
        // 校验观演人归属
        Viewer existingViewer = viewerManager.getByIdAndUserId(viewerId, userId);
        if (existingViewer == null) {
            throw new BizException(ErrorCode.VIEWER_NOT_FOUND);
        }

        viewerManager.delete(viewerId, userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验短信验证码
     */
    private void validateSmsCode(String mobile, String code) {
        String cacheKey = SMS_CODE_PREFIX + mobile;
        String cachedCode = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedCode == null) {
            throw new BizException(ErrorCode.SMS_CODE_EXPIRED);
        }
        if (!cachedCode.equals(code)) {
            throw new BizException(ErrorCode.SMS_CODE_ERROR);
        }
    }

    /**
     * 清除短信验证码
     */
    private void clearSmsCode(String mobile) {
        String cacheKey = SMS_CODE_PREFIX + mobile;
        stringRedisTemplate.delete(cacheKey);
    }

    /**
     * 存储 RefreshToken 到 Redis
     */
    private void saveRefreshToken(Long userId, String refreshToken) {
        String cacheKey = REFRESH_TOKEN_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(cacheKey, refreshToken,
                REFRESH_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 手机号脱敏
     */
    private String desensitizeMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }
}
