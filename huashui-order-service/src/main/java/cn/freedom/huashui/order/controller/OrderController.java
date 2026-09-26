package cn.freedom.huashui.order.controller;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.order.dto.CreateOrderDTO;
import cn.freedom.huashui.order.dto.OrderQueryDTO;
import cn.freedom.huashui.order.service.OrderService;
import cn.freedom.huashui.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 *
 * <p>网关路由 {@code /api/order/**} + StripPrefix=1 → 本类的 {@code /order/**}。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Tag(name = "订单接口", description = "下单、付款、完成、取消、我的订单")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "创建订单", description = "需要登录。商品须处于「在售」，成功后商品被锁定，其他买家无法下单")
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderDTO dto) {
        return Result.success("下单成功", orderService.create(dto.getProductId()));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的订单", description = "需要登录。role=buyer 我买到的 / seller 我卖出的 / all 全部（默认）")
    public Result<PageResult<OrderVO>> mine(@Valid OrderQueryDTO query) {
        return Result.success(orderService.listMine(query));
    }

    @GetMapping("/detail/{orderNo}")
    @Operation(summary = "订单详情", description = "需要登录。只有买卖双方可以查看")
    public Result<OrderVO> detail(@PathVariable("orderNo") String orderNo) {
        return Result.success(orderService.detail(orderNo));
    }

    @GetMapping("/sold-count/{sellerId}")
    @Operation(summary = "卖家历史成交笔数", description = "无需登录。已付款与交易完成的订单计入，已取消不计")
    public Result<Integer> soldCount(@PathVariable("sellerId") Long sellerId) {
        return Result.success(orderService.soldCount(sellerId));
    }

    @PutMapping("/{orderNo}/pay")
    @Operation(summary = "确认已付款", description = "需要登录，仅买家。模拟支付：表示双方已线下完成付款，资金不经手平台")
    public Result<Void> pay(@PathVariable("orderNo") String orderNo) {
        orderService.pay(orderNo);
        return Result.success();
    }

    @PutMapping("/{orderNo}/complete")
    @Operation(summary = "确认交易完成", description = "需要登录，买卖双方均可。已付款 → 交易完成")
    public Result<Void> complete(@PathVariable("orderNo") String orderNo) {
        orderService.complete(orderNo);
        return Result.success();
    }

    @PutMapping("/{orderNo}/cancel")
    @Operation(summary = "取消订单", description = "需要登录，买卖双方均可。只有「待支付」可以取消，取消后商品自动恢复在售")
    public Result<Void> cancel(@PathVariable("orderNo") String orderNo,
                               @RequestParam(value = "reason", required = false) String reason) {
        orderService.cancel(orderNo, reason);
        return Result.success();
    }
}
