package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.api.product.ProductLockDTO;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品域内部接口。
 *
 * <p>路径以 {@code /inner} 开头，与对外接口物理隔离。网关路由只覆盖
 * {@code /api/product/**}（StripPrefix 后是 {@code /product/**}），
 * 因此 {@code /inner/**} <b>无法从网关进来</b>，只有服务间直接调用才能访问。
 *
 * <p>三个接口都是「条件更新 + 判断受影响行数」，所以天然幂等。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/inner/product")
@RequiredArgsConstructor
@Tag(name = "内部接口", description = "仅供其他微服务调用，不对网关暴露")
public class InnerProductController {

    private final ProductService productService;

    @PostMapping("/{id}/lock")
    @Operation(summary = "锁定商品", description = "在售 → 已锁定，一并返回商品快照。供下单使用")
    public Result<ProductLockDTO> lock(@PathVariable("id") Long id,
                                       @RequestParam("buyerId") Long buyerId) {
        return Result.success(productService.lockForOrder(id, buyerId));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "解锁商品", description = "已锁定 → 在售。建单失败补偿与订单取消共用，幂等")
    public Result<Void> unlock(@PathVariable("id") Long id) {
        productService.unlockForOrder(id);
        return Result.success();
    }

    @PostMapping("/{id}/sold")
    @Operation(summary = "标记已售出", description = "已锁定 → 已售出。买家确认付款时调用")
    public Result<Void> markSold(@PathVariable("id") Long id) {
        productService.markSold(id);
        return Result.success();
    }
}
