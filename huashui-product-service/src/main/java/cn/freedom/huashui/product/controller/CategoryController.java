package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.product.service.CategoryService;
import cn.freedom.huashui.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类接口。
 *
 * <p>网关把 {@code /api/category/list} 转发到这里时已经去掉了 {@code /api}，
 * 因此本类映射到 {@code /category}。
 *
 * <p>分类是独立资源，所以走独立路由 {@code /api/category/**}，
 * 而不是挂在 {@code /api/product/category/**} 下——后者会让将来管理端的
 * 「新增分类」变成「操作商品的子资源」，语义上说不通。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
@Tag(name = "商品分类", description = "分类列表，公开接口")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    @Operation(summary = "分类列表", description = "无需登录。返回启用中的分类，按 sort 升序")
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryService.listEnabled());
    }
}
