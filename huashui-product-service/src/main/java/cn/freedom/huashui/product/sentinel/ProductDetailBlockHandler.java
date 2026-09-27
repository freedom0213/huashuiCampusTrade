package cn.freedom.huashui.product.sentinel;

import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.product.vo.ProductDetailVO;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;

/**
 * 商品详情被限流时的出口（阶段 11）。
 *
 * <p><b>为什么单独一个类而不是把方法写在 Controller 里：</b>
 * {@code blockHandler} 与业务方法必须是同一个类、且返回值 / 参数签名严格对应，
 * 混在 Controller 里会让「接口定义」和「限流兜底」缠在一起，读接口时先撞上一堆
 * Sentinel 的类型。放到 {@code blockHandlerClass} 里，Controller 上只剩一行注解，
 * 同时强制这些方法都是 static（无状态，天然线程安全）。
 *
 * <p><b>为什么返回 429 而不是降到「商品不存在」之类的假数据：</b>
 * 详情页拿不到数据时，返回「商品不存在」会让用户以为商品被删了或下架了，
 * 从而放弃这件商品 —— 这是把「限流」伪装成了「业务结果」。
 * 明确告诉他「操作过于频繁」才是诚实的：稍等片刻还能看到。
 *
 * @author freedom0213
 */
@Slf4j
public final class ProductDetailBlockHandler {

    private ProductDetailBlockHandler() {
    }

    /**
     * 签名规则：原方法参数（这里是商品 id）+ {@link BlockException}，返回值与原方法一致。
     */
    public static Result<ProductDetailVO> blocked(Long id, BlockException e) {
        log.warn("商品详情被热点参数限流 | productId={} | rule={}", id, e.getClass().getSimpleName());
        return Result.error(ResultCode.TOO_MANY_REQUESTS);
    }
}
