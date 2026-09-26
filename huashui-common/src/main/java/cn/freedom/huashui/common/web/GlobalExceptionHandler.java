package cn.freedom.huashui.common.web;

import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>目标：Controller 里不出现任何 try-catch，业务失败统一抛 {@link BizException}，
 * 由这里转成 {@link Result}。这样任何一个接口的异常出口都是统一格式，
 * 前端只需要处理一套结构。
 *
 * <p>日志分级的判断依据：业务异常是「预期内的失败」（参数错、状态不对），
 * 用 warn 且不打堆栈；系统异常是「预期外的故障」，用 error 并打完整堆栈。
 *
 * @author freedom0213
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：Service 主动抛出的、可预期的失败。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常 | code={} | message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * @Valid 校验失败（RequestBody）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败 | {}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /**
     * @Valid 校验失败（表单 / 查询参数绑定）。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数绑定失败 | {}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 缺少必填参数 / 参数类型不匹配。
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public Result<Void> handleBadRequest(Exception e) {
        log.warn("请求参数非法 | {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR);
    }

    /**
     * 兜底：任何未被上面捕获的异常。
     * 对外只返回统一文案，不暴露堆栈信息；真实原因记录在服务端日志里。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }

    /**
     * 静态资源不存在（未匹配到任何 Controller 的请求最终会走到这里）。
     *
     * <p>必须单独处理，否则会被上面的兜底处理器吃成 500：
     * 图片加载失败会被记成「系统异常」，既污染日志又给了错误的排查方向。
     * 而且浏览器请求 &lt;img&gt; 时，返回 200 + 错误 JSON 比返回 404 更糟。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求的资源不存在 | {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ResultCode.NOT_FOUND));
    }
}
