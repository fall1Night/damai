package com.damai.api.fallback.user;

import com.damai.api.client.user.UserFeignClient;
import com.damai.api.dto.user.UserDto;
import com.damai.api.dto.user.ViewerDto;
import com.damai.common.api.ApiResult;
import com.damai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户 Feign 客户端降级实现 —— user 服务不可达时兜底。
 *
 * <p>设计要点（技术文档 §8.4 熔断降级）：
 * <ul>
 *   <li>降级方法返回"系统繁忙"业务码 + 空数据，<b>不抛异常</b>（避免级联故障）。</li>
 *   <li>记录 WARN 日志，便于告警与事后排查。</li>
 *   <li>调用方需根据返回的成功/失败自行决定后续逻辑。</li>
 * </ul>
 */
@Slf4j
@Component
public class UserFeignClientFallback implements UserFeignClient {

    @Override
    public ApiResult<UserDto> getById(Long userId) {
        log.warn("[UserFeignFallback] getById 降级触发, userId={}", userId);
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "用户服务暂不可用");
    }

    @Override
    public ApiResult<List<ViewerDto>> listViewer(Long userId) {
        log.warn("[UserFeignFallback] listViewer 降级触发, userId={}", userId);
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "用户服务暂不可用");
    }
}
