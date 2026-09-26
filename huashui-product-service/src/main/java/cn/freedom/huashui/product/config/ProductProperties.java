package cn.freedom.huashui.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商品业务配置。
 *
 * @author freedom0213
 */
@Data
@ConfigurationProperties(prefix = "huashui.product")
public class ProductProperties {

    /**
     * 发布商品后是否需要管理员审核。
     *
     * <p>最终形态是 {@code true}：发布 → 待审核 → 管理员审核 → 在售。
     * 但管理端在阶段 12 才做，在那之前没有任何入口能把商品从「待审核」放行，
     * 发布于是一进列表就查不到，整个发布流程无法自测。
     *
     * <p>因此做成配置项：开发期置为 {@code false}（发布即上架），
     * 阶段 12 接入管理端后改回 {@code true} 即可，<b>不需要改代码</b>。
     */
    private boolean auditRequired = true;
}
