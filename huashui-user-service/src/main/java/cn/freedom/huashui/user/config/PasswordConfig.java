package cn.freedom.huashui.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器。
 *
 * <p><b>为什么用 BCrypt 而不是 MD5 / SHA-256：</b>
 * MD5、SHA 是「快哈希」，设计目标就是快——一张显卡每秒能算几十亿次，
 * 配合彩虹表可以在很短时间内反推出常见密码。BCrypt 是「慢哈希」，
 * 内置随机盐且计算代价可调，单次约 50~100ms，暴力破解成本极高。
 *
 * <p>另外 BCrypt 的盐是<b>随机生成并存在哈希串里</b>的，
 * 因此同一个密码每次加密结果都不同，不需要自己维护盐字段。
 *
 * @author freedom0213
 */
@Configuration
public class PasswordConfig {

    /**
     * strength 默认为 10，表示 2^10 次密钥扩展迭代。
     * 调高更安全但登录更慢，10 是安全与性能的常用平衡点。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
