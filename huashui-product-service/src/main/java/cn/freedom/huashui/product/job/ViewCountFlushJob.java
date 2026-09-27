package cn.freedom.huashui.product.job;

import cn.freedom.huashui.common.constant.RedisKeys;
import cn.freedom.huashui.product.cache.CacheService;
import cn.freedom.huashui.product.entity.Product;
import cn.freedom.huashui.product.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 浏览量结算任务：把 Redis 中累加的浏览量按差值回写到数据库。
 *
 * <p><b>为什么要有这个任务</b>：浏览商品时若直接
 * {@code UPDATE t_product SET view_count = view_count + 1}，
 * 每一次浏览都会对同一行加排他锁。热门商品会成为<b>行锁热点</b>，请求排队。
 * 所以改成：浏览时只做 Redis {@code INCR}（内存操作、无锁竞争），
 * 由本任务每 5 分钟把差值批量写回一次 —— 数据库压力从「每次浏览一次写」降到「5 分钟一次写」。
 *
 * <p><b>三处容易写错的地方（都按「不丢数」来设计）</b>：
 * <ol>
 *   <li><b>取差值必须用 {@code GETSET}</b>，不能「先读再清零」——
 *       两步之间到达的浏览会被清零那一步抹掉，而这属于静默丢数据；</li>
 *   <li><b>回写必须是「增量累加」</b>（{@code view_count = view_count + delta}），
 *       不能写绝对值。写绝对值的话，任务与商品编辑并发时会互相覆盖；</li>
 *   <li><b>回写失败要把差值还回 Redis</b>，并保留 id 在待结算集合里，
 *       否则这一次的浏览量就真的没了。</li>
 * </ol>
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushJob {

    private final CacheService cacheService;
    private final ProductMapper productMapper;

    @Value("${huashui.view-count.flush-batch-size:200}")
    private int flushBatchSize;

    @XxlJob("viewCountFlushJob")
    public void flushViewCount() {
        Set<String> dirtyIds = cacheService.members(RedisKeys.PRODUCT_DIRTY_VIEWS, flushBatchSize);
        if (dirtyIds.isEmpty()) {
            XxlJobHelper.log("没有待结算的商品，本次跳过");
            return;
        }

        int handled = 0;
        long totalDelta = 0L;
        int failed = 0;

        for (String idText : dirtyIds) {
            Long productId = parseId(idText);
            if (productId == null) {
                // 脏数据（集合里混进了非数字）：直接移出集合，否则每轮都会白白处理一次
                cacheService.removeFromSet(RedisKeys.PRODUCT_DIRTY_VIEWS, idText);
                log.warn("待结算集合中存在非法商品 id，已移除 | value={}", idText);
                continue;
            }

            String viewKey = RedisKeys.productView(productId);
            long delta = cacheService.getAndReset(viewKey);
            if (delta <= 0) {
                // 计数为 0：说明增量已经被上一轮结算过（集合尚未清干净），只需清理集合
                cacheService.removeFromSet(RedisKeys.PRODUCT_DIRTY_VIEWS, idText);
                continue;
            }

            try {
                productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("view_count = view_count + {0}", delta)
                        .set(Product::getUpdateTime, LocalDateTime.now()));
                // 结算成功才把 id 移出集合。注意：此刻之后新来的浏览会重新 SADD 进来，
                // 因此不会出现「移出集合后新增量永远结算不到」的情况
                cacheService.removeFromSet(RedisKeys.PRODUCT_DIRTY_VIEWS, idText);
                handled++;
                totalDelta += delta;
            } catch (Exception e) {
                // 把差值还回去，让下一轮重新结算。不还的话这些浏览就永久丢失了
                cacheService.incrementBy(viewKey, delta);
                failed++;
                log.error("浏览量结算失败，差值已归还待下轮重试 | productId={} | delta={}", productId, delta, e);
            }
        }

        String summary = String.format("浏览量结算完成 | 成功 %d 个商品、累计 %d 次浏览、失败 %d 个",
                handled, totalDelta, failed);
        XxlJobHelper.log(summary);
        log.info(summary);
    }

    private Long parseId(String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
