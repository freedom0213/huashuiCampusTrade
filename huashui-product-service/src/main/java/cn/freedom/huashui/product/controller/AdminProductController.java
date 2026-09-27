package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.product.dto.AuditRejectDTO;
import cn.freedom.huashui.product.service.ProductService;
import cn.freedom.huashui.product.vo.ProductListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 - 商品审核接口。
 *
 * <p>路由走既有网关规则 {@code /api/product/**} + StripPrefix=1，本类实际暴露在
 * {@code /api/product/admin/**}。四个接口全部要求管理员角色（role=2），
 * 校验在服务层 {@code UserContext.requireAdmin()} 完成，非管理员统一 403。
 *
 * <p>刻意不做的：管理端登录态之外的额外鉴权体系、批量操作、操作日志表——
 * 管理是低频动作，单人操作，用最少的东西闭环。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/product/admin")
@RequiredArgsConstructor
@Tag(name = "管理端-商品审核", description = "仅管理员可用：审核列表、通过、驳回、强制下架")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping("/audit/list")
    @Operation(summary = "审核列表", description = "需要管理员。status 不传查待审核(0)，传 5 查已驳回")
    public Result<PageResult<ProductListVO>> auditList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer size) {
        return Result.success(productService.listForAudit(status, current, size));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "审核通过", description = "需要管理员。仅「待审核」可操作，通过后商品进入公共列表")
    public Result<Void> approve(@PathVariable("id") Long id) {
        productService.approve(id);
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "审核驳回", description = "需要管理员。仅「待审核」可操作，必须填写驳回原因")
    public Result<Void> reject(@PathVariable("id") Long id,
                               @Valid @RequestBody AuditRejectDTO dto) {
        productService.reject(id, dto.getReason());
        return Result.success();
    }

    @PutMapping("/{id}/force-off")
    @Operation(summary = "强制下架", description = "需要管理员。仅「在售」可操作，用于处置已上架的违规商品")
    public Result<Void> forceOff(@PathVariable("id") Long id) {
        productService.forceOffShelf(id);
        return Result.success();
    }
}
