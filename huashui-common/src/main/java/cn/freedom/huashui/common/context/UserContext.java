package cn.freedom.huashui.common.context;

import cn.freedom.huashui.common.enums.UserRole;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.ResultCode;

/**
 * 当前登录用户上下文。
 *
 * <p>工作方式：网关校验 JWT 后把 userId / role 写入请求头，
 * 各服务的拦截器在 {@code preHandle} 中读出来放进 ThreadLocal，
 * 业务代码直接 {@code UserContext.getUserId()} 即可，不用每个方法都传 userId。
 *
 * <p><b>必须在请求结束时调用 {@link #clear()}。</b>
 * Tomcat 使用线程池复用线程，如果不清理，下一个请求可能读到上一个用户的身份（串号），
 * 同时 ThreadLocal 持有对象也会造成内存泄漏。清理动作由拦截器的 afterCompletion 完成。
 *
 * @author freedom0213
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, Integer role) {
        HOLDER.set(new CurrentUser(userId, role));
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser current = HOLDER.get();
        return current == null ? null : current.userId();
    }

    public static Integer getRole() {
        CurrentUser current = HOLDER.get();
        return current == null ? null : current.role();
    }

    /**
     * 取当前登录用户 id，未登录直接抛 401。
     * 用于「必须登录」的业务方法，省掉每个方法里的判空分支。
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static boolean isAdmin() {
        return UserRole.isAdmin(getRole());
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前登录用户快照。
     */
    public record CurrentUser(Long userId, Integer role) {
    }
}
