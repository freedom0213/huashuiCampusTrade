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

    // ==================== 交易域 3xxxx ====================
    ORDER_NOT_FOUND(30001, "订单不存在"),
    PRODUCT_LOCK_FAILED(30002, "手慢了，商品已被别人抢先买走"),
    ORDER_STATUS_ILLEGAL(30003, "订单当前状态不允许该操作"),
    CANNOT_BUY_OWN_PRODUCT(30004, "不能购买自己发布的商品"),
    ORDER_NOT_OWNED(30005, "只能操作自己的订单");

    /** 业务状态码 */
    private final int code;

    /** 面向用户的提示信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
