package cn.freedom.huashui.gateway.config;

import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 网关限流（阶段 11）。
 *
 * <p><b>为什么限流放在网关：</b>
 * 网关是所有外部流量的唯一入口，规则在这里配一处就能拦住打向任何服务的请求。
 * 若分散到各服务，同一条「登录防爆破」要在 user-service 里再写一遍，
 * 而且拦不住「压根不该进到服务里」的流量（无效请求已经占用了网关→服务的连接）。
 *
 * <p><b>为什么不按服务名（route id）限流：</b>
 * route id 是 {@code user-service / product-service} 这种粒度，
 * 限流等于「把这个服务所有接口一起掐掉」——登录接口被限流时，
 * 改密码、看个人资料也一起不可用了。所以这里用 <b>API 分组</b>，
 * 精确到「哪一个接口」。
 *
 * <p><b>规则为什么写在代码里而不是只在控制台配：</b>
 * Sentinel 默认把规则放在内存，控制台里改的规则一重启就没了，
 * 也进不了 git、没法被 review。这里装载一份「默认规则」保证服务起来即具备防护；
 * 控制台的作用是演示动态调整（改完立即生效，重启回到这里的默认值）。
 * 阶段 12 接入 Nacos 配置中心后，规则改为从配置中心推送（Push 模式持久化），
 * 这里的硬编码再删掉。
 *
 * <p><b>本类不用 {@code huashui-common} 里那套 Web 相关配置：</b>
 * 网关是 WebFlux，common 中的 Web 配置都带 {@code @ConditionalOnWebApplication(SERVLET)}，
 * 在这里根本不生效。
 *
 * @author freedom0213
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    /** 登录接口分组的资源名 */
    public static final String API_LOGIN = "login_api";
    /** 商品详情分组的资源名 */
    public static final String API_PRODUCT_DETAIL = "product_detail_api";
    /** 下单接口分组的资源名 */
    public static final String API_ORDER_CREATE = "order_create_api";

    @PostConstruct
    public void init() {
        initApiDefinitions();
        initFlowRules();
    }

    // ==================== API 分组 ====================

    /**
     * 注册三个 API 分组。
     *
     * <p>分组只是「给一组路径起个名字」，本身不产生任何限制；限流规则引用的是这个名字。
     * 名字同时也是控制台里看到的资源名 —— 所以取名要能自解释。
     */
    private void initApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 登录：精确匹配。一个路径就一个接口，没有同前缀的兄弟接口，用精确匹配最不容易误伤
        definitions.add(new ApiDefinition(API_LOGIN).setPredicateItems(
                Set.of(pathItem("/api/user/login", SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT))));

        // 商品详情：前缀匹配。id 在路径里（/api/product/detail/{id}），没法枚举，
        // 只能按前缀。⚠️ 这里写的是 Ant 模式而不是「字符串前缀」——
        // 匹配内核是 AntPathMatcher，漏了 /** 会匹配不到任何带 id 的请求，
        // 表现为「规则配了但从不触发」
        definitions.add(new ApiDefinition(API_PRODUCT_DETAIL).setPredicateItems(
                Set.of(pathItem("/api/product/detail/**", SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX))));

        // 下单：POST /api/order（网关侧）。其余订单接口都带子路径（/mine、/detail/...），
        // 所以精确匹配这个路径就只命中下单，不会把「查我的订单」也一起限掉
        definitions.add(new ApiDefinition(API_ORDER_CREATE).setPredicateItems(
                Set.of(pathItem("/api/order", SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT))));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
        log.info("Sentinel 网关 API 分组已装载 | {}", definitions.stream()
                .map(ApiDefinition::getApiName).sorted().toList());
    }

    private static ApiPredicateItem pathItem(String pattern, int matchStrategy) {
        return new ApiPathPredicateItem().setPattern(pattern).setMatchStrategy(matchStrategy);
    }

    // ==================== 限流规则 ====================

    /**
     * 装载默认限流规则。
     *
     * <p>⚠️ <b>这里用的是普通 {@link FlowRule}，不是官方为网关定制的 {@code GatewayFlowRule}。</b>
     * 这是实测后的选择，理由如下（2026-09-27 在本项目的依赖组合上验证）：
     * <ul>
     *   <li>用 {@code GatewayRuleManager.loadRules(GatewayFlowRule...)} 装载后，
     *       规则在运行时确实存在（{@code /gateway/getRules} 能查到、控制台也显示），
     *       API 分组对应的资源（{@code login_api}）也在正常统计调用量，
     *       但<b>大流量打进来一次都不拦</b>（{@code /cnode} 里 blocked 恒为 0）。</li>
     *   <li>改成 {@code FlowRuleManager.loadRules(FlowRule...)} 后立即生效：
     *       把阈值调成 QPS 1 连打 5 次，稳定出现 2 次 429。</li>
     * </ul>
     * 官方文档说网关规则会被转成热点参数规则、由 {@code GatewayFlowSlot} 检查，
     * 但在这套组合下没有触发。**以实际行为为准**——限流的价值在于「真的拦得住」，
     * 不在于「用的是哪个 API」。资源名依然来自 API 分组
     * （{@code SentinelGatewayFilter} 用分组名创建资源），所以按接口限流的能力不受影响。
     *
     * <p>控制台里给这些资源改规则的位置：**簇点链路** → 找到 {@code login_api} 等资源 → 点「流控」。
     *
     * <p>⚙️ 阈值说明（都是「单机 QPS 上限」）：
     * <ul>
     *   <li><b>登录 5</b>：正常用户不可能每秒登录 5 次。这个值的意义是「把脚本爆破的
     *       尝试速率压到人工可忽略」，同时留足真实用户重试的余量（输错密码连点几次不会撞限流）。</li>
     *   <li><b>商品详情 50</b>：详情页有缓存（阶段 8），单机扛 50 QPS 很轻松；
     *       设这个值是为了拦住「某个商品被脚本刷」的形态。</li>
     *   <li><b>下单 10</b>：下单本身就是低频动作（一个人一次只买一件）。
     *       限流不是为了防正常用户，而是「商品被锁定」这一步会写库，
     *       需要一道入口闸门防止瞬时洪峰把商品库打满。</li>
     * </ul>
     *
     * <p>限流效果用默认的「快速失败」：超出的请求立刻收到 429，不排队。
     * 排队（匀速排队）会把请求挂住，对移动端 H5 反而是更差的体验 ——
     * 用户宁可马上看到「操作过于频繁」，也不愿页面转圈三秒后再告诉他。
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(apiRule(API_LOGIN, 5));
        rules.add(apiRule(API_PRODUCT_DETAIL, 50));
        rules.add(apiRule(API_ORDER_CREATE, 10));

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 网关默认限流规则已装载 | login={} | productDetail={} | orderCreate={} (QPS)",
                5, 50, 10);
    }

    private static FlowRule apiRule(String apiName, double qps) {
        FlowRule rule = new FlowRule(apiName);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        // CONTROL_BEHAVIOR_DEFAULT = 快速失败（超出的请求直接拒绝，不排队）
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }

    // ==================== 限流后的响应 ====================

    /**
     * 被限流时返回什么。
     *
     * <p>Sentinel 默认返回 429 + 它自己的一段 JSON（{@code {"code":429,"message":"Blocked by Sentinel: ..."}}）。
     * 那段 JSON 的结构恰好和我们的 {@link Result} 很像，但 message 是英文、而且暴露了中间件名字 ——
     * 前端拿到的提示会是「Blocked by Sentinel: ParamFlowException」这种东西。
     *
     * <p>改成统一的 {@link Result} 后，前端不需要为限流单独写一套解析。
     * HTTP 状态仍用 429（与网关既有的 404/503 处理风格一致：状态码表达类别，
     * body 表达给用户看的话）。注意与业务失败的区别 —— 业务失败是 HTTP 200 + 业务码，
     * 而限流属于「这一层拒绝了你」，用状态码表达更准确。
     *
     * <p>注入方式说明：这个 Bean 会被 SCA 的 {@code SentinelSCGAutoConfiguration}
     * 通过 {@code Optional<BlockRequestHandler>} 自动取走并注册到 {@code GatewayCallbackManager}，
     * 不需要我们手动调 setBlockHandler。
     */
    @Bean
    @ConditionalOnMissingBean
    public BlockRequestHandler blockRequestHandler() {
        return (exchange, ex) -> {
            String path = exchange.getRequest().getURI().getPath();
            log.warn("请求被限流 | path={} | rule={}", path, ex.getClass().getSimpleName());
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(Result.error(ResultCode.TOO_MANY_REQUESTS)));
        };
    }
}
