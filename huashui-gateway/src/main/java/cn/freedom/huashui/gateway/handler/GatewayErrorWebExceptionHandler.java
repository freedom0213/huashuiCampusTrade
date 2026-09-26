package cn.freedom.huashui.gateway.handler;

import cn.freedom.huashui.common.result.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一异常响应。
 *
 * <p>为什么网关要单独写一个：huashui-common 里的 {@code GlobalExceptionHandler}
 * 是 {@code @RestControllerAdvice}，属于 Servlet MVC 的机制，
 * 在 WebFlux 网关里根本不生效。不处理的话，网关抛出的错误会走 Spring Boot 默认的
 * 白页 / 默认 JSON 结构，导致前端拿到的错误格式和业务接口不一致。
 *
 * <p>最典型的场景：下游服务未启动或已下线时，网关返回 503，
 * 前端应该拿到和业务接口一致的 {@code {code, message, data}} 结构。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        // 响应已经开始写出时无法再改状态码，交回给容器处理
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();

        HttpStatus status;
        int code;
        String message;

        if (ex instanceof ResponseStatusException statusException) {
            status = HttpStatus.valueOf(statusException.getStatusCode().value());
            switch (status.value()) {
                case 404 -> {
                    code = 404;
                    message = "请求的接口不存在";
                }
                case 405 -> {
                    code = 405;
                    message = "请求方法不支持";
                }
                case 503 -> {
                    code = 503;
                    message = "服务暂时不可用，请稍后重试";
                }
                default -> {
                    code = 500;
                    message = "网关处理请求失败";
                }
            }
            log.warn("网关转发失败 | path={} | status={} | reason={}", path, status.value(), ex.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = 500;
            message = "系统繁忙，请稍后重试";
            log.error("网关异常 | path={}", path, ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return writeJson(response, Result.error(code, message));
    }

    private Mono<Void> writeJson(ServerHttpResponse response, Result<?> body) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":500,\"message\":\"系统繁忙，请稍后重试\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
