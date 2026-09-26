package cn.freedom.huashui.gateway.filter;

import cn.freedom.huashui.common.constant.AuthConstants;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.common.util.JwtUtil;
import cn.freedom.huashui.gateway.config.GatewayAuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * JWT 鉴权全局过滤器 —— 整个平台唯一的鉴权入口。
 *
 * <p>职责边界：<b>只做认证，不做授权</b>。
 * 它只回答「你是谁」，不回答「你能不能做这件事」。
 * 例如「只能修改自己发布的商品」属于业务规则，由 product-service 自己判断，
 * 网关不掺和，否则每加一条业务规则都要改网关、重新发版。
 *
 * <p>执行流程：
 * <ol>
 *   <li>OPTIONS 预检请求直接放行（浏览器发预检时不带 token，拦了会导致跨域全挂）</li>
 *   <li>命中白名单则放行</li>
 *   <li>取 token → 校验签名与有效期 → 失败返回 401</li>
 *   <li>校验通过后把 userId / role 写入请求头，向下游透传</li>
 * </ol>
 *
 * <p>为什么不在这里查数据库或 Redis：网关是所有流量的必经之路，
 * 每多一次远程调用就多一个故障点和一份延迟。token 自包含身份信息，
 * 校验签名即可，无需回源。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final GatewayAuthProperties authProperties;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. CORS 预检请求不带 Authorization，必须放行，否则跨域请求全部失败
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 2. 白名单
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        // 3. 取 token
        String token = resolveToken(request);
        if (token == null) {
            log.debug("请求未携带 token | path={}", path);
            return unauthorized(exchange);
        }

        // 4. 校验签名与有效期（解析失败一律按未登录处理，不区分「过期」和「伪造」，
        //    避免给攻击者提供信息）
        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            log.warn("token 校验失败 | path={}", path);
            return unauthorized(exchange);
        }

        // 5. 透传身份
        Long userId = Long.valueOf(claims.getSubject());
        Integer role = claims.get(JwtUtil.CLAIM_ROLE, Integer.class);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(AuthConstants.HEADER_USER_ID, String.valueOf(userId))
                .header(AuthConstants.HEADER_USER_ROLE, role == null ? "" : String.valueOf(role))
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isWhiteList(String path) {
        return authProperties.getWhiteList().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(AuthConstants.HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(AuthConstants.BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 返回 401。这里用 HTTP 401 而不是「HTTP 200 + 业务码 401」，
     * 因为鉴权失败属于协议层面的语义，用状态码表达更标准，
     * 前端拦截器也能直接按状态码统一跳登录页。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return writeJson(response, Result.error(ResultCode.UNAUTHORIZED));
    }

    private Mono<Void> writeJson(ServerHttpResponse response, Result<?> body) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":500,\"message\":\"系统繁忙，请稍后重试\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 顺序必须早于路由级过滤器（如 StripPrefix），否则拿到的 path 已经被改写，
     * 白名单就匹配不上了。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
