package com.damai.api.client.user;

import com.damai.api.dto.user.UserDto;
import com.damai.api.dto.user.ViewerDto;
import com.damai.api.fallback.user.UserFeignClientFallback;
import com.damai.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务 Feign 客户端 —— 供 order/pay 等服务远程调用用户域能力。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code name} 必须与 user 服务在 Nacos 注册的服务名一致（{@code damai-user}）。</li>
 *   <li>{@code fallback} 指向降级实现，依赖方异常时返回兜底值，避免级联雪崩。</li>
 *   <li>所有方法返回 {@link ApiResult}，保持与被调用方 Controller 一致的返回结构。</li>
 * </ul>
 *
 * <p>注意：本接口仅定义契约，实现由 {@code damai-user} 服务的 Controller 提供。
 */
@FeignClient(name = "damai-user", fallback = UserFeignClientFallback.class)
public interface UserFeignClient {

    /**
     * 根据 userId 查询用户信息（不含密码）。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    @GetMapping("/user/inner/getById")
    ApiResult<UserDto> getById(@RequestParam("userId") Long userId);

    /**
     * 查询指定用户的观演人列表（下单时锁定实名信息用）。
     *
     * @param userId 用户 ID
     * @return 观演人列表
     */
    @GetMapping("/user/inner/viewer/list")
    ApiResult<List<ViewerDto>> listViewer(@RequestParam("userId") Long userId);
}
