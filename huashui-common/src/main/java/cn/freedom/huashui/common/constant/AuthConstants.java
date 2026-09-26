package cn.freedom.huashui.common.constant;

/**
 * 网关与各服务之间传递身份的请求头名称。
 *
 * <p><b>为什么单独放在这里，而不是写在 {@code UserContextInterceptor} 里：</b>
 * 网关是 WebFlux 应用，classpath 上没有 {@code jakarta.servlet}。
 * 一旦网关引用 {@code UserContextInterceptor}，就会触发该类加载并抛
 * {@code NoClassDefFoundError}。因此这些跨模块共享的常量必须放在
 * 与 Web 框架无关的类中。
 *
 * @author freedom0213
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** 认证方式请求头，格式为 {@code Bearer <token>} */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 网关校验 JWT 后写入的用户 id，下游服务据此识别当前登录用户 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关校验 JWT 后写入的用户角色（1 学生 / 2 管理员） */
    public static final String HEADER_USER_ROLE = "X-User-Role";
}
