package com.damai.user.controller;

import com.damai.common.api.ApiResult;
import com.damai.user.entity.Viewer;
import com.damai.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 *
 * @author damai
 */
@Tag(name = "用户服务", description = "用户注册、登录、个人信息管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "手机号 + 密码 + 验证码注册")
    @PostMapping("/register")
    public ApiResult<Map<String, Object>> register(@Validated @RequestBody RegisterRequest request) {
        Map<String, Object> result = userService.register(
                request.getMobile(), request.getPassword(), request.getCode());
        return ApiResult.success(result);
    }

    /**
     * 密码登录
     */
    @Operation(summary = "密码登录", description = "手机号 + 密码登录")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@Validated @RequestBody LoginRequest request) {
        Map<String, Object> result = userService.login(request.getMobile(), request.getPassword());
        return ApiResult.success(result);
    }

    /**
     * 验证码登录
     */
    @Operation(summary = "验证码登录", description = "手机号 + 验证码登录")
    @PostMapping("/login-by-code")
    public ApiResult<Map<String, Object>> loginByCode(
            @Validated @RequestBody LoginByCodeRequest request) {
        Map<String, Object> result = userService.loginByCode(request.getMobile(), request.getCode());
        return ApiResult.success(result);
    }

    /**
     * 刷新 Token
     */
    @Operation(summary = "刷新 Token", description = "使用 RefreshToken 刷新 AccessToken")
    @PostMapping("/refresh-token")
    public ApiResult<Map<String, Object>> refreshToken(
            @Validated @RequestBody RefreshTokenRequest request) {
        Map<String, Object> result = userService.refreshToken(request.getRefreshToken());
        return ApiResult.success(result);
    }

    /**
     * 用户登出
     */
    @Operation(summary = "用户登出", description = "吊销 RefreshToken")
    @PostMapping("/logout")
    public ApiResult<Void> logout(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        userService.logout(userId);
        return ApiResult.success(null);
    }

    /**
     * 获取当前用户信息
     */
    @Operation(summary = "获取用户信息", description = "获取当前登录用户信息（脱敏）")
    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> getUserInfo(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = userService.getUserInfo(userId);
        return ApiResult.success(result);
    }

    /**
     * 更新个人信息
     */
    @Operation(summary = "更新个人信息", description = "更新昵称、头像、性别")
    @PutMapping("/profile")
    public ApiResult<Void> updateProfile(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Validated @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(userId, request.getNickname(), request.getAvatar(), request.getGender());
        return ApiResult.success(null);
    }

    /**
     * 获取观演人列表
     */
    @Operation(summary = "获取观演人列表", description = "获取当前用户的所有观演人")
    @GetMapping("/viewer/list")
    public ApiResult<List<Viewer>> getViewerList(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        List<Viewer> viewers = userService.getViewerList(userId);
        return ApiResult.success(viewers);
    }

    /**
     * 新增观演人
     */
    @Operation(summary = "新增观演人", description = "添加观演人信息（最多5个）")
    @PostMapping("/viewer")
    public ApiResult<Void> addViewer(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Validated @RequestBody AddViewerRequest request) {
        Viewer viewer = new Viewer();
        viewer.setName(request.getName());
        viewer.setIdType(request.getIdType());
        viewer.setIdNo(request.getIdNo());
        viewer.setMobile(request.getMobile());
        userService.addViewer(userId, viewer);
        return ApiResult.success(null);
    }

    /**
     * 更新观演人
     */
    @Operation(summary = "更新观演人", description = "修改观演人信息")
    @PutMapping("/viewer/{viewerId}")
    public ApiResult<Void> updateViewer(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "观演人ID", required = true)
            @PathVariable Long viewerId,
            @Validated @RequestBody UpdateViewerRequest request) {
        Viewer viewer = new Viewer();
        viewer.setName(request.getName());
        viewer.setIdType(request.getIdType());
        viewer.setIdNo(request.getIdNo());
        viewer.setMobile(request.getMobile());
        userService.updateViewer(userId, viewerId, viewer);
        return ApiResult.success(null);
    }

    /**
     * 删除观演人
     */
    @Operation(summary = "删除观演人", description = "删除指定观演人")
    @DeleteMapping("/viewer/{viewerId}")
    public ApiResult<Void> deleteViewer(
            @Parameter(description = "用户ID", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "观演人ID", required = true)
            @PathVariable Long viewerId) {
        userService.deleteViewer(userId, viewerId);
        return ApiResult.success(null);
    }

    // ==================== 内部 Feign 接口 ====================

    /**
     * 内部接口：根据用户ID获取用户信息（供其他服务调用）
     */
    @Operation(summary = "内部接口-获取用户信息", description = "供其他服务通过 Feign 调用")
    @GetMapping("/inner/getById")
    public ApiResult<Map<String, Object>> getById(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        Map<String, Object> result = userService.getUserInfo(userId);
        return ApiResult.success(result);
    }

    /**
     * 内部接口：获取观演人列表（供其他服务调用）
     */
    @Operation(summary = "内部接口-获取观演人列表", description = "供其他服务通过 Feign 调用")
    @GetMapping("/inner/viewer/list")
    public ApiResult<List<Viewer>> getViewerListInner(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        List<Viewer> viewers = userService.getViewerList(userId);
        return ApiResult.success(viewers);
    }

    // ==================== 请求体 DTO ====================

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String mobile;

        @NotBlank(message = "密码不能为空")
        @jakarta.validation.constraints.Size(min = 6, max = 20, message = "密码长度为6-20位")
        private String password;

        @NotBlank(message = "验证码不能为空")
        @jakarta.validation.constraints.Size(min = 4, max = 6, message = "验证码长度为4-6位")
        private String code;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String mobile;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginByCodeRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String mobile;

        @NotBlank(message = "验证码不能为空")
        private String code;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;
    }

    @Data
    public static class UpdateProfileRequest {
        private String nickname;
        private String avatar;
        private Integer gender;
    }

    @Data
    public static class AddViewerRequest {
        @NotBlank(message = "观演人姓名不能为空")
        private String name;

        @jakarta.validation.constraints.NotNull(message = "证件类型不能为空")
        private Integer idType;

        @NotBlank(message = "证件号码不能为空")
        private String idNo;

        private String mobile;
    }

    @Data
    public static class UpdateViewerRequest {
        private String name;
        private Integer idType;
        private String idNo;
        private String mobile;
    }
}
