package cn.freedom.huashui.common.api.product;

import cn.freedom.huashui.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品域内部接口客户端（Feign）。
 *
 * <p><b>三个方法的共同点：都是「条件更新 + 判断受影响行数」</b>，
 * 因此<b>天然幂等</b> —— 重复调用第二次受影响行数为 0，不产生任何副作用。
 * 这一点对 MQ 重复消费至关重要（见 {@code order.cancel.queue} 的消费者）。
 *
 * <p><b>路径 {@code /inner/product/**} 不对网关暴露。</b>
 * 网关路由只覆盖 {@code /api/product/**}（StripPrefix 后是 {@code /product/**}），
 * 所以 {@code /inner/**} 无法从外网进来，只有服务间直接调用才能访问。
 * 这是「不信任外部请求头」的落地方式：内部接口的调用方是可信的兄弟服务，
 * 而对外接口的身份一律来自网关校验过的 JWT。
 *
 * <p><b>为什么这里用显式参数传 buyerId，而不是靠请求头透传身份：</b>
 * Feign 发出的是一个全新的 HTTP 请求，网关加的 {@code X-User-Id} 头不会自动带过去。
 * 若靠拦截器复制请求头，就形成了「隐式契约」——参数从哪来、有没有传丢，
 * 编译期看不出来，出问题只能靠抓包。显式参数则是编译期可查的。
 *
 * @author freedom0213
 */
@FeignClient(
        name = "huashui-product-service",
        // 同一个服务被多个 Feign 接口引用时，必须给 contextId，
        // 否则 Spring 容器会因 Bean 名称冲突而启动失败
        contextId = "productClient",
        path = "/inner/product"
)
public interface ProductClient {

    /**
     * 锁定商品（在售 → 已锁定），成功时一并返回商品快照。
     *
     * <p>由「条件更新」保证并发安全：{@code UPDATE t_product SET status=2 WHERE id=? AND status=1}。
     * 受影响行数为 0 说明商品已不是「在售」（被别人买走 / 已下架 / 已售出）。
     *
     * @param productId 商品 id
     * @param buyerId   买家 id，用于拦截「购买自己发布的商品」
     * @return 锁定成功返回快照；失败返回错误码（商品不存在 / 已下架 / 不能买自己的商品）
     */
    @PostMapping("/{id}/lock")
    Result<ProductLockDTO> lock(@PathVariable("id") Long productId,
                                @RequestParam("buyerId") Long buyerId);

    /**
     * 解锁商品（已锁定 → 在售）。
     *
     * <p>两个使用场景：
     * <ol>
     *   <li><b>补偿</b>：order 侧锁定成功后本地建单失败，同步调用它把商品放回去；</li>
     *   <li><b>消费 MQ</b>：订单取消后由 product-service 自己消费消息调用（不走本接口）。</li>
     * </ol>
     */
    @PostMapping("/{id}/unlock")
    Result<Void> unlock(@PathVariable("id") Long productId);

    /**
     * 标记商品已售出（已锁定 → 已售出，终态）。
     *
     * <p>由买家「确认已线下付款」时调用。这一步用同步调用而不是 MQ，原因见订单服务的说明。
     */
    @PostMapping("/{id}/sold")
    Result<Void> markSold(@PathVariable("id") Long productId);
}
