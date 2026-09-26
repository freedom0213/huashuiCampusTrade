package cn.freedom.huashui.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 单元测试。
 *
 * @author freedom0213
 */
class JwtUtilTest {

    private static final String SECRET = "huashui-campus-trade-jwt-secret-key-2026";
    private static final String ANOTHER_SECRET = "another-huashui-jwt-secret-key-000000";
    private static final long TTL_MINUTES = 120L;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, TTL_MINUTES);

    @Test
    @DisplayName("签发的 token 能被解析出 userId 与 role")
    void generateAndParse() {
        String token = jwtUtil.generate(9L, 1);

        assertNotNull(token);

        Claims claims = jwtUtil.parse(token);
        assertNotNull(claims);
        assertEquals("9", claims.getSubject());
        assertEquals(1, claims.get(JwtUtil.CLAIM_ROLE, Integer.class));

        assertEquals(9L, jwtUtil.getUserId(token));
        assertEquals(1, jwtUtil.getRole(token));
        assertTrue(jwtUtil.isValid(token));
    }

    @Test
    @DisplayName("用其他密钥签发的 token 校验失败，返回 null")
    void tokenSignedByAnotherSecretReturnsNull() {
        String foreignToken = new JwtUtil(ANOTHER_SECRET, TTL_MINUTES).generate(9L, 1);

        assertNull(jwtUtil.parse(foreignToken));
        assertFalse(jwtUtil.isValid(foreignToken));
    }

    @Test
    @DisplayName("已过期的 token 返回 null")
    void expiredTokenReturnsNull() {
        String expiredToken = new JwtUtil(SECRET, -1L).generate(9L, 1);

        assertNull(jwtUtil.parse(expiredToken));
    }

    @Test
    @DisplayName("格式非法的 token 返回 null 而不是抛异常")
    void malformedTokenReturnsNull() {
        assertNull(jwtUtil.parse(null));
        assertNull(jwtUtil.parse(""));
        assertNull(jwtUtil.parse("   "));
        assertNull(jwtUtil.parse("not-a-jwt-at-all"));
    }

    @Test
    @DisplayName("密钥不足 32 字节时在构造阶段就失败，而不是运行期才报错")
    void shortSecretRejectedOnConstruct() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short", TTL_MINUTES));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(null, TTL_MINUTES));
    }

    @Test
    @DisplayName("默认有效期与配置一致")
    void ttlMatchesConfiguration() {
        assertEquals(TTL_MINUTES, jwtUtil.getTtlMinutes());
    }
}
