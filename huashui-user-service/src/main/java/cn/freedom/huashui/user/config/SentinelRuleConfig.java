package cn.freedom.huashui.user.config;

import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 用户域的 Sentinel 规则（阶段 11）。
 *
 * <p><b>本服务没有业务规则，只有系统自适应保护。</b>
 * 登录接口的防爆破限流配在网关（{@code login_api}）—— 那里能拦在服务之前，
 * 不用让无效请求占着 Tomcat 线程跑完一次密码校验。
 *
 * <p>那为什么还要接 Sentinel？两个理由：
 * <ol>
 *   <li>系统规则是<b>进程级</b>的，只在 order / product 装载等于漏了半边 ——
 *       全站过载时用户域照样会把 CPU 吃满（密码哈希是 CPU 密集操作，这正是
 *       登录接口最需要 CPU 兜底的地方）；</li>
 *   <li>控制台里能看到本服务的实时调用曲线，排查「到底是哪个服务慢」时不用猜。</li>
 * </ol>
 *
 * @author freedom0213
 */
@Slf4j
@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void init() {
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
