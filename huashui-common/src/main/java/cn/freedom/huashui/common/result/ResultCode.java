package cn.freedom.huashui.common.result;

import lombok.Getter;

/**
 * 全项目统一错误码。
 *
 * <p>分段规则：
 * <ul>
 *   <li>200 / 4xx / 500 —— 通用状态</li>
 *   <li>1xxxx —— 用户域</li>
 *   <li>2xxxx —— 商品域</li>
 *   <li>3xxxx —— 交易域</li>
 * </ul>
 *
 * @author freedom0213
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    /**
     * 被 Sentinel 限流拦下。
     *
     * <p>用 429 而不是 500：这是「你自己请求太快」而不是「服务出故障」，
     * 两者的用户动作完全不同（前者稍后重试，后者反馈问题）。
     * 文案刻意写成「操作过于频繁」而不是「系统繁忙」—— 后者会让用户以为平台挂了。
     */
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),

    // ==================== 用户域 1xxxx ====================
    USERNAME_EXISTS(10001, "用户名已被注册"),
    PHONE_EXISTS(10002, "手机号已被注册"),
    USER_NOT_FOUND(10003, "用户不存在"),
    USERNAME_OR_PASSWORD_ERROR(10004, "用户名或密码错误"),
    USER_DISABLED(10005, "账号已被禁用"),
    OLD_PASSWORD_ERROR(10006, "原密码不正确"),

    // ==================== 商品域 2xxxx ====================
    CATEGORY_NOT_FOUND(20001, "商品分类不存在"),
    CATEGORY_NAME_EXISTS(20002, "分类名称已存在"),
    PRODUCT_NOT_FOUND(20003, "商品不存在"),
    PRODUCT_NOT_ON_SALE(20004, "商品已下架或已被购买"),
    PRODUCT_NOT_OWNED(20005, "只能操作自己发布的商品"),
    PRODUCT_STATUS_ILLEGAL(20006, "商品当前状态不允许该操作"),
    ALREADY_FAVORITED(20007, "已经收藏过该商品"),
    NOT_FAVORITED(20008, "尚未收藏该商品"),
    IMAGE_EMPTY(20009, "请选择要上传的图片"),
    IMAGE_TYPE_NOT_ALLOWED(20010, "只支持 jpg / jpeg / png / gif / webp 格式的图片"),
    IMAGE_TOO_LARGE(20011, "图片大小超出限制"),
    IMAGE_STORE_FAILED(20012, "图片保存失败，请稍后重试"),
    CANNOT_FAVORITE_OWN_PRODUCT(20013, "不能收藏自己发布的商品"),

    // ==================== 交易域 3xxxx ====================
    ORDER_NOT_FOUND(30001, "订单不存在"),
    PRODUCT_LOCK_FAILED(30002, "手慢了，商品已被别人抢先买走"),
    ORDER_STATUS_ILLEGAL(30003, "订单当前状态不允许该操作"),
    CANNOT_BUY_OWN_PRODUCT(30004, "不能购买自己发布的商品"),
    ORDER_NOT_OWNED(30005, "只能操作自己的订单"),
    /**
     * 商品服务被熔断后，交易域对买家的统一回执。
     *
     * <p><b>它必须是一句「明确的失败」而不是任何形式的成功</b>：
     * 下单涉及真金白银（线下见面付钱），「以为下单成功、到地方发现没有」的代价
     * 远大于「让用户重试一次」。所以商品服务不可用时宁可快速失败。
     *
     * <p>文案要说清是「依赖方的问题 + 可重试」，不要把矛盾指向上层业务
     * （用户会以为是自己哪里操作错了）。
     */
    PRODUCT_SERVICE_UNAVAILABLE(30006, "商品服务暂时不可用，请稍后重试");

    /** 业务状态码 */
    private final int code;

    /** 面向用户的提示信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 按状态码反查枚举。
     *
     * <p>用途是「翻译下游服务的错误码」：调用方拿到 Feign 返回的 {@code Result} 后，
     * 需要把里面的业务码还原成枚举，才能原样抛出本服务的业务异常。
     * 没有这个方法，调用方就只能把错误码硬编码成数字或字符串，一改就漏。
     *
     * @param code 业务状态码
     * @return 找不到时返回 {@code null}，由调用方决定兜底策略
     */
    public static ResultCode of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ResultCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
