package com.damai.starter.web;

import com.alibaba.fastjson2.JSON;
import com.damai.common.api.ApiResult;
import com.damai.common.exception.BaseException;
import com.damai.common.exception.BizException;
import com.damai.common.exception.ErrorCode;
import com.damai.common.exception.SystemException;
import com.damai.common.utils.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 对应技术文档 §6 四层分层约束与 PRD §11.2 错误码体系。
 *
 * <p>设计原则：
 * <ul>
 *   <li><b>业务异常</b>（{@link BizException}）：可预知错误，返回明确业务码 + 中文提示，
 *       仅 DEBUG 日志（避免噪声）。</li>
 *   <li><b>系统异常</b>（{@link SystemException}）：不可预知故障，记录 ERROR 日志（含堆栈），
 *       对外返回统一的 {@link ErrorCode#SYSTEM_ERROR}（不暴露内部细节）。</li>
 *   <li><b>未捕获异常</b>（兜底）：记录 ERROR 日志 + 返回 SYSTEM_ERROR。</li>
 *   <li>所有返回统一携带 {@code traceId}（网关生成，链路透传）。</li>
 * </ul>
 *
 * <p>错误码段位（PRD §11.2）：
 * <pre>
 *   0       成功
 *   1xxxx   通用错误（参数/权限/系统）
 *   2xxxx   用户域
 *   3xxxx   节目域
 *   4xxxx   订单域
 *   5xxxx   支付域
 *   9xxxx   基础设施
 * </pre>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常（可预知，DEBUG 级别） ====================

    /**
     * 业务异常处理。
     * <p>BizException 携带 ErrorCode，直接使用其 code + message。
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public <T> ApiResult<T> handleBizException(BizException e) {
        log.debug("[BizException] code={}, message={}", e.getCode(), e.getMessage());
        return buildFail(e.getErrorCode());
    }

    // ==================== 系统异常（不可预知，ERROR 级别） ====================

    /**
     * 系统异常处理。
     * <p>SystemException 携带 ErrorCode，记录完整堆栈，但不暴露内部细节。
     */
    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ApiResult<T> handleSystemException(SystemException e) {
        log.error("[SystemException] code={}, message={}", e.getCode(), e.getMessage(), e);
        return buildFail(e.getErrorCode());
    }

    // ==================== 参数校验异常 ====================

    /**
     * {@code @Validated} 参数校验失败（Controller 方法参数）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ApiResult<T> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.debug("[ParamValid] {}", detail);
        return ApiResult.fail(ErrorCode.PARAM_INVALID, detail);
    }

    /**
     * {@code @Valid} Bean 校验失败（BindException）。
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ApiResult<T> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.debug("[ParamValid] {}", detail);
        return ApiResult.fail(ErrorCode.PARAM_INVALID, detail);
    }

    /**
     * 单个参数校验失败（如 {@code @NotBlank @RequestParam}）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ApiResult<T> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.debug("[ParamValid] {}", detail);
        return ApiResult.fail(ErrorCode.PARAM_INVALID, detail);
    }

    // ==================== 请求格式异常 ====================

    /**
     * 缺少必要请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ApiResult<T> handleMissingParam(MissingServletRequestParameterException e) {
        log.debug("[MissingParam] {}", e.getMessage());
        return ApiResult.fail(ErrorCode.PARAM_MISSING,
                String.format("缺少必要参数: %s", e.getParameterName()));
    }

    /**
     * 参数类型转换错误（如传字符串给 Integer 参数）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ApiResult<T> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.debug("[TypeMismatch] {}", e.getMessage());
        return ApiResult.fail(ErrorCode.DATA_TYPE_ERROR,
                String.format("参数类型错误: %s", e.getPropertyName()));
    }

    /**
     * 请求体不可读（JSON 格式错误等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ApiResult<T> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.debug("[MessageNotReadable] {}", e.getMessage());
        return ApiResult.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    // ==================== 其他异常（兜底） ====================

    /**
     * 兜底处理所有未捕获异常。
     * <p>记录 ERROR 日志（含堆栈），对外返回统一的 SYSTEM_ERROR，不暴露内部细节。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ApiResult<T> handleException(Exception e) {
        log.error("[UnhandledException] 未捕获异常", e);
        return buildFail(ErrorCode.SYSTEM_ERROR);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建统一失败返回（注入当前 traceId）。
     */
    private <T> ApiResult<T> buildFail(ErrorCode errorCode) {
        ApiResult<T> result = ApiResult.fail(errorCode);
        result.setTraceId(TraceIdUtil.get());
        return result;
    }
}
