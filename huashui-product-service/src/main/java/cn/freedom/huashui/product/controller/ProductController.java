package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.product.config.SentinelRuleConfig;
import cn.freedom.huashui.product.dto.ProductQueryDTO;
import cn.freedom.huashui.product.dto.ProductSaveDTO;
import cn.freedom.huashui.product.sentinel.ProductDetailBlockHandler;
import cn.freedom.huashui.product.service.ProductService;
import cn.freedom.huashui.product.vo.ProductDetailVO;
import cn.freedom.huashui.product.vo.ProductListVO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口。
 *
 * <p>网关路由 {@code /api/product/**} + StripPrefix=1 → 本类的 {@code /product/**}。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
@Tag(name = "商品接口", description = "发布、编辑、上下架、列表、详情")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "发布商品", description = "需要登录。是否进入待审核由服务端配置决定")
    public Result<Long> publish(@Valid @RequestBody ProductSaveDTO dto) {
        return Result.success("发布成功", productService.publish(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑商品", description = "需要登录，仅卖家本人可操作")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ProductSaveDTO dto) {
        productService.update(id, dto);
        return Result.success();
    }

    @PutMapping("/{id}/off-shelf")
    @Operation(summary = "下架商品", description = "需要登录，仅卖家本人可操作。只有「在售」可下架")
    public Result<Void> offShelf(@PathVariable("id") Long id) {
        productService.offShelf(id);
        return Result.success();
    }

    @PutMapping("/{id}/on-shelf")
    @Operation(summary = "重新上架", description = "需要登录，仅卖家本人可操作。只有「已下架」可上架")
    public Result<Void> onShelf(@PathVariable("id") Long id) {
        productService.onShelf(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品", description = "需要登录，仅卖家本人可操作。逻辑删除，已被锁定的商品不可删")
    public Result<Void> delete(@PathVariable("id") Long id) {
        productService.delete(id);
        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "商品列表", description = "无需登录。只返回在售商品，支持分类/关键词/校区/成色/卖家/排序")
    public Result<PageResult<ProductListVO>> list(@Valid ProductQueryDTO query) {
        return Result.success(productService.list(query));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的发布", description = "需要登录。返回自己的全部商品，可按状态筛选")
    public Result<PageResult<ProductListVO>> mine(@Valid ProductQueryDTO query) {
        return Result.success(productService.listMine(query));
    }

    @GetMapping("/detail/{id}")
    @SentinelResource(value = SentinelRuleConfig.RESOURCE_PRODUCT_DETAIL,
            blockHandler = "blocked",
            blockHandlerClass = ProductDetailBlockHandler.class)
    @Operation(summary = "商品详情", description = "无需登录。待审核与已驳回的商品仅卖家本人可见")
    public Result<ProductDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(productService.detail(id));
    }
}
