package cn.freedom.huashui.product.config;

import cn.freedom.huashui.product.sentinel.ProductDetailBlockHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 商品域的 Sentinel 规则（阶段 11）。
 *
 * <p>规则写在代码里的理由见网关的 {@code SentinelGatewayConfig}：
 * 保证服务启动即具备防护，控制台负责演示动态调整，阶段 12 改为从 Nacos 推送。
 *
 * @author freedom0213
 */
@Slf4j
@Configuration
public class SentinelRuleConfig {

    /**
     * 商品详情的资源名。
     *
     * <p>必须是编译期常量（{@code @SentinelResource} 的 value 要求），
     * 同时被 {@link ProductDetailBlockHandler} 与下面的规则共用 ——
     * 三处写同一个字面量是最容易出错的地方（改一处漏两处、规则静默失效），
     * 所以统一引用这个常量。
     */
    public static final String RESOURCE_PRODUCT_DETAIL = "productDetail";

    /** 单个商品的详情页 QPS 上限 */
    private static final double DETAIL_QPS_PER_PRODUCT = 20;

    @PostConstruct
    public void init() {
        initHotParamRules();
        initSystemRules();
    }

    // ==================== 热点参数限流 ====================

    /**
     * 按 productId 给「单个商品」限流。
     *
     * <p><b>它解决的是普通 QPS 限流解决不了的问题：</b>
     * 网关那条「商品详情 QPS 50」限的是<b>总量</b>，它分不出这 50 次是散在 50 个商品上，
     * 还是全砸在同 1 个商品上。前者完全正常（50 个买家各看各的），
     * 后者才是危险的形态 —— 一个商品被脚本刷，缓存击穿逻辑（阶段 8 的 SETNX 互斥锁）
     * 会被持续触发，最后所有请求一起去压数据库。
     *
     * <p>热点参数限流按参数值分别计数，所以结果是：<b>正常用户的浏览完全不受影响，
     * 只有那个被集中的商品会被单独拦下</b>，其他商品的流量照走不误。
     * 这正是「热点」二字的意思 —— 限的是热点本身，不是整个接口。
     *
     * <p>⚙️ 参数说明：
     * <ul>
     *   <li>paramIdx = 0 —— {@code detail(Long id)} 的第 0 个参数就是商品 id</li>
     *   <li>count = 20 —— 单个商品每秒 20 次。真实用户看完详情页至少停留几秒，
     *       这个值只有脚本才够得着</li>
     *   <li>durationInSec = 1 —— 1 秒一个统计窗口</li>
     * </ul>
     */
    private void initHotParamRules() {
        ParamFlowRule detailRule = new ParamFlowRule(RESOURCE_PRODUCT_DETAIL)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(DETAIL_QPS_PER_PRODUCT)
                .setDurationInSec(1);

        ParamFlowRuleManager.loadRules(List.of(detailRule));
        log.info("Sentinel 热点参数规则已装载 | resource={} | 单个商品 QPS <= {}",
                RESOURCE_PRODUCT_DETAIL, (int) DETAIL_QPS_PER_PRODUCT);
    }

    // ==================== 系统自适应保护 ====================

    /**
     * 系统规则：与「按接口算 QPS」互补的兜底，细节见 order-service 的同类方法。
     *
     * <p>商品服务是读多写少的服务，唯一的重活是「缓存未命中时回源查库」。
     * 一旦平均 RT 被拖起来，说明缓存已经在失效了，此时继续放流量只会雪上加霜。
     */
    private void initSystemRules() {
        SystemRule rule = new SystemRule();
        rule.setAvgRt(1000);
        rule.setMaxThread(200);
        rule.setQps(1000);
        rule.setHighestCpuUsage(0.8);
        // Windows 上拿不到 load1，设了等于没设
        rule.setHighestSystemLoad(-1);

        SystemRuleManager.loadRules(List.of(rule));
        log.info("Sentinel 系统保护规则已装载 | avgRt<={}ms | maxThread<={} | qps<={} | cpu<={}",
                1000, 200, 1000, 0.8);
    }
}
