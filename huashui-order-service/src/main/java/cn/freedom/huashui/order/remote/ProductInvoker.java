package cn.freedom.huashui.order.remote;

import cn.freedom.huashui.common.api.product.ProductClient;
import cn.freedom.huashui.common.api.product.ProductLockDTO;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品域远程调用的唯一出口（阶段 11）。
 *
 * <p><b>本类存在的唯一理由：让 Sentinel 的熔断注解真的生效。</b>
 * 改造前，四个 Feign 调用散落在 {@code OrderServiceImpl}（三个 private 包装方法）
 * 与 {@code StaleLockScanJob} 里。{@link SentinelResource} 是靠 Spring AOP 代理实现的，
 * 而 <b>同类内部调用私有方法不经过代理</b> —— 注解写上去也不会生效，
 * 且不报任何错，只表现为「商品服务停了，下单还是要干等 Feign 超时」。
 * 把它们集中到一个被注入的 Bean 里，调用方拿到的是代理对象，注解才真正起作用。
 *
 * <p><b>为什么四个方法共用同一个资源名：</b>
 * 资源名 {@value #RESOURCE} 代表的是「商品域这个依赖的整体健康状况」，
 * 而不是某一次调用。四次调用里任意一次在失败，都说明商品服务有问题，
 * 熔断器就应该打开。分开统计的话，冷门接口（比如兜底扫描任务每 10 分钟才调一次）
 * 永远攒不够最小请求数，熔断永远不会触发。
 *
 * <p><b>为什么刻意不配 {@code fallback}：</b>
 * 熔断降级要有明确边界 —— 「依赖挂了」可以降级，「业务拒绝了」不能。
 * 本类里所有业务失败都是正常的 {@link Result} 返回（商品已售出、不能买自己的商品），
 * 一条异常都不抛，所以压根不会进入熔断统计；而远程调用失败会被统一转成
 * {@link BizException} 抛出 —— 这既让 Sentinel 把它记成「故障」，
 * 也让上层能原样拿到「商品服务暂时不可用」的提示。
 *
 * <p><b>降级策略：快速失败，绝不伪造成功。</b>
 * 下单是「线下见面付款」的起点，让用户以为下单成功、到地方发现没有，
 * 代价远大于让他重试一次。所以熔断后的 blockHandler 只返回失败，不返回任何替代数据。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductInvoker {

    /** Sentinel 资源名。四个方法共用，见类注释 */
    public static final String RESOURCE = "productClient";

    private final ProductClient productClient;

    // ==================== 锁定（下单主链路，失败必须快速失败）====================

    @SentinelResource(value = RESOURCE, blockHandler = "lockBlocked")
    public Result<ProductLockDTO> lock(Long productId, Long buyerId) {
        try {
            return productClient.lock(productId, buyerId);
        } catch (Exception e) {
            // Feign 抛异常只有两种情况：连不上商品服务，或对方返回了非 2xx。
            // 「商品已被别人买走」这类业务失败走的是 HTTP 200 + 业务码，不会落到这里
            log.error("调用商品服务锁定商品失败 | productId={} | buyerId={}", productId, buyerId, e);
            throw new BizException(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 熔断打开时的短路出口。
     *
     * <p>签名规则：原方法参数 + {@link BlockException}，返回值类型必须与原方法一致，
     * 方法必须是 public 且同类实例方法 —— 任何一条不满足都会静默失效。
     */
    public Result<ProductLockDTO> lockBlocked(Long productId, Long buyerId, BlockException e) {
        log.warn("商品服务处于熔断状态，下单直接快速失败 | productId={} | buyerId={} | rule={}",
                productId, buyerId, e.getClass().getSimpleName());
        return Result.error(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    // ==================== 解锁（补偿与兜底扫描用）====================

    @SentinelResource(value = RESOURCE, blockHandler = "unlockBlocked")
    public Result<Void> unlock(Long productId) {
        try {
            return productClient.unlock(productId);
        } catch (Exception e) {
            log.error("调用商品服务解锁商品失败 | productId={}", productId, e);
            throw new BizException(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    public Result<Void> unlockBlocked(Long productId, BlockException e) {
        log.warn("商品服务处于熔断状态，解锁请求被短路 | productId={} | rule={}",
                productId, e.getClass().getSimpleName());
        return Result.error(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    // ==================== 标记已售出（付款时调用）====================

    @SentinelResource(value = RESOURCE, blockHandler = "markSoldBlocked")
    public Result<Void> markSold(Long productId) {
        try {
            return productClient.markSold(productId);
        } catch (Exception e) {
            log.error("调用商品服务标记已售出失败 | productId={}", productId, e);
            throw new BizException(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    public Result<Void> markSoldBlocked(Long productId, BlockException e) {
        log.warn("商品服务处于熔断状态，标记已售出被短路 | productId={} | rule={}",
                productId, e.getClass().getSimpleName());
        return Result.error(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    // ==================== 查询锁定超时的商品（兜底扫描任务用）====================

    @SentinelResource(value = RESOURCE, blockHandler = "staleLockedBlocked")
    public Result<List<Long>> listStaleLocked(int beforeMinutes, int limit) {
        try {
            return productClient.listStaleLocked(beforeMinutes, limit);
        } catch (Exception e) {
            log.error("调用商品服务查询锁定超时商品失败 | beforeMinutes={}", beforeMinutes, e);
            throw new BizException(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    public Result<List<Long>> staleLockedBlocked(int beforeMinutes, int limit, BlockException e) {
        log.warn("商品服务处于熔断状态，兜底扫描本轮跳过 | beforeMinutes={} | rule={}",
                beforeMinutes, e.getClass().getSimpleName());
        return Result.error(ResultCode.PRODUCT_SERVICE_UNAVAILABLE);
    }
}
