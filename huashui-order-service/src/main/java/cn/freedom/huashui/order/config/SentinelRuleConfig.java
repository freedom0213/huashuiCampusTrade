package cn.freedom.huashui.order.config;

import cn.freedom.huashui.order.remote.ProductInvoker;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 交易域的 Sentinel 规则（阶段 11）。
 *
 * <p>规则写在代码里的理由见网关的 {@code SentinelGatewayConfig}：
 * 保证服务启动即具备防护。**阶段 12 起，Nacos（dataId: huashui-order-degrade-rules.json）
 * 是规则的事实来源，加载在后、覆盖这里的同资源规则**；本类保留作为 Nacos
 * 未配置 / 未启动时的兜底，不能删。
 *
 * @author freedom0213
 */
@Slf4j
@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void init() {
        initDegradeRules();
        initSystemRules();
    }

    // ==================== 熔断降级 ====================

    /**
     * 商品服务调用熔断。
     *
     * <p><b>为什么用「异常比例」而不是「慢调用比例」：</b>
     * 商品服务不可用有两种形态 —— 进程挂了（连接直接被拒，立刻抛异常）
     * 和进程活着但卡住（请求一直挂到 Feign 的读超时 3 秒）。前者的表现是异常，
     * 后者的表现是慢。看起来慢调用比例更全面，但它需要一个额外的 RT 阈值，
     * 而这个项目的 RT 基线没压测过、定不准；异常比例只要「有一半的调用失败」
     * 就熔断，对上面两种形态都成立 —— 因为卡住超过 3 秒最终也会变成超时异常。
     *
     * <p>⚙️ 参数含义：
     * <ul>
     *   <li>count = 0.5 —— 异常比例超过 50%</li>
     *   <li>statIntervalMs = 10000 —— 统计窗口 10 秒（太短会让偶发抖动就熔断）</li>
     *   <li>minRequestAmount = 5 —— 窗口内至少 5 次请求才参与统计，
     *       防止「第一秒只有 1 个请求且失败」就 100% 熔断</li>
     *   <li>timeWindow = 30 —— 熔断 30 秒。这是「给下游恢复的时间」，
     *       太长会让一次短暂抖动变成半分钟不可用；太短则恢复瞬间又被压垮</li>
     * </ul>
     *
     * <p>降级时抛的是 {@code DegradeException}（BlockException 的子类），
     * 由 {@link ProductInvoker} 里各方法的 blockHandler 接住，
     * 返回「商品服务暂时不可用」而不是任何形式的成功。
     */
    private void initDegradeRules() {
        DegradeRule productCallRule = new DegradeRule(ProductInvoker.RESOURCE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.5)
                .setStatIntervalMs(10_000)
                .setMinRequestAmount(5)
                .setTimeWindow(30);

        DegradeRuleManager.loadRules(List.of(productCallRule));
        log.info("Sentinel 熔断规则已装载 | resource={} | 异常比例>{}% 且窗口内请求数>={} 时熔断 {}s",
                ProductInvoker.RESOURCE, 50, 5, 30);
    }

    // ==================== 系统自适应保护 ====================

    /**
     * 系统规则：与「按接口算 QPS」互补的兜底。
     *
     * <p>限流规则要人工估一个 QPS 阈值，估小了误伤、估大了没保护；
     * 系统规则不猜业务量，而是盯着进程自己扛不扛得住（平均 RT / 并发线程 / CPU），
     * 任何一项越界就整体刹车。它是「最后一道闸」，不是主要手段 ——
     * 所以阈值都取得很松，正常情况下永远不会触发。
     *
     * <p>触发时抛出的 {@code SystemBlockException} 会走到全局异常处理，
     * 返回「系统繁忙，请稍后重试」—— 这个文案在这里恰好是准确的。
     */
    private void initSystemRules() {
        SystemRule rule = new SystemRule();
        // 全部资源的平均 RT 超过 1 秒：说明已经开始堆积了（本项目正常接口在几十毫秒）
        rule.setAvgRt(1000);
        // 并发处理线程数上限。Tomcat 默认最大 200，这里留出余量，
        // 在容器把线程池打满、请求开始排队之前就拦住
        rule.setMaxThread(200);
        // 入口总 QPS 上限。各接口的细粒度限流在网关做，这里只是防「规则被绕过」的极端情况
        rule.setQps(1000);
        // CPU 使用率超过 80%：继续放流量只会让所有请求一起变慢
        rule.setHighestCpuUsage(0.8);
        // 系统负载（load1）刻意不设：Windows 上这个指标不可用，设了等于没设
        rule.setHighestSystemLoad(-1);

        SystemRuleManager.loadRules(List.of(rule));
        log.info("Sentinel 系统保护规则已装载 | avgRt<={}ms | maxThread<={} | qps<={} | cpu<={}",
                1000, 200, 1000, 0.8);
    }
}
