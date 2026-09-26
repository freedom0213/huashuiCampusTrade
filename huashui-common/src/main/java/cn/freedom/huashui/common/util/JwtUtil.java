package cn.freedom.huashui.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具。
 *
 * <p>设计要点：
 * <ol>
 *   <li><b>无状态</b>——token 自身携带 userId 与 role，服务端不存 session，
 *       这样网关校验完即可直接把身份透传给下游，任何一台实例都能独立完成校验。</li>
 *   <li><b>密钥长度强校验</b>——HS256 要求密钥至少 256 位（32 字节），
 *       长度不足 jjwt 会在运行期抛异常；这里提前到构造时抛出，启动阶段就能发现问题。</li>
 *   <li><b>解析失败返回 null 而非抛异常</b>——调用方（网关）只需判断 null，
 *       不必用 try-catch 包裹每一次校验。</li>
 * </ol>
 *
 * <p>该类刻意不做成静态工具类：密钥与过期时间来自配置，做成实例更容易测试与替换。
 *
 * @author freedom0213
 */
public class JwtUtil {

    /** HS256 算法要求的最小密钥字节数 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 自定义 claim：用户角色 */
    public static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long ttlMillis;

    /**
     * @param secret     签名密钥，UTF-8 编码后不得少于 32 字节
     * @param ttlMinutes token 有效期（分钟）
     */
    public JwtUtil(String secret, long ttlMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT 密钥长度不足：HS256 要求至少 " + MIN_SECRET_BYTES + " 字节（256 位）");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60L * 1000L;
    }

    /**
     * 签发 token。
     *
     * <p>带 jti（token 唯一 id）是为了将来支持「退出登录立即失效」——
     * 只需要把 jti 放进 Redis 黑名单，无需改动 token 结构。
     */
    public String generate(Long userId, Integer role) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验签名与有效期。
     *
     * @return 合法则返回 Claims；签名错误、格式非法或已过期一律返回 null
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parse(token);
        return claims == null ? null : Long.valueOf(claims.getSubject());
    }

    public Integer getRole(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.get(CLAIM_ROLE, Integer.class);
    }

    public boolean isValid(String token) {
        return parse(token) != null;
    }

    public long getTtlMinutes() {
        return ttlMillis / 60L / 1000L;
    }
}
