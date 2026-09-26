package cn.freedom.huashui.common.web;

import cn.freedom.huashui.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 把网关透传的用户身份写入 {@link UserContext}。
 *
 * <p>网关校验 JWT 后会在请求头上写入 {@code X-User-Id} / {@code X-User-Role}，
 * 本拦截器负责把它们搬到 ThreadLocal，业务代码就能直接取用。
 *
 * <p><b>安全前提</b>：下游服务必须只在内网暴露、不能直接对外，
 * 否则任何人都能伪造这两个请求头绕过鉴权。生产部署时通过 Docker 内部网络 + 只暴露网关来解决。
 *
 * @author freedom0213
 */
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            // 游客请求，不设置上下文，由具体接口自行决定是否需要登录
            return true;
        }
        try {
            String role = request.getHeader(HEADER_USER_ROLE);
            UserContext.set(Long.valueOf(userId),
                    (role == null || role.isBlank()) ? null : Integer.valueOf(role));
        } catch (NumberFormatException e) {
            log.warn("请求头中的用户信息格式非法 | X-User-Id={} | X-User-Role={}",
                    userId, request.getHeader(HEADER_USER_ROLE));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理：线程池复用线程，不清理会导致身份串号与内存泄漏
        UserContext.clear();
    }
}
