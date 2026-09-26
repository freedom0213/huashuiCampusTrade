package cn.freedom.huashui.common.exception;

import cn.freedom.huashui.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>业务校验失败时抛出该异常，由 {@code GlobalExceptionHandler} 统一转成 {@code Result} 返回，
 * 避免在每个 Service 里手写 if-else 组装错误响应。
 *
 * <p>注意：继承 {@link RuntimeException} 而非受检异常，这样在 Service 层抛出时
 * 不需要在方法签名上层层声明 throws。
 *
 * @author freedom0213
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
