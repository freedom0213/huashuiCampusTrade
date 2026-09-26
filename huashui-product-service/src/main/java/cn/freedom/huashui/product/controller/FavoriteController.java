package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.product.dto.FavoriteQueryDTO;
import cn.freedom.huashui.product.service.FavoriteService;
import cn.freedom.huashui.product.vo.ProductListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口。
 *
 * <p>网关路由 {@code /api/favorite/**} + StripPrefix=1 → 本类的 {@code /favorite/**}。
 * 三个接口都需要登录，均不在网关白名单内。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
@Tag(name = "收藏接口", description = "收藏、取消收藏、我的收藏")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{productId}")
    @Operation(summary = "收藏商品", description = "需要登录。不能收藏自己发布的商品；重复收藏会提示已收藏")
    public Result<Void> add(@PathVariable("productId") Long productId) {
        favoriteService.add(productId);
        return Result.success();
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "取消收藏", description = "需要登录。未收藏过会提示")
    public Result<Void> remove(@PathVariable("productId") Long productId) {
        favoriteService.remove(productId);
        return Result.success();
    }

    @GetMapping("/mine")
    @Operation(summary = "我的收藏", description = "需要登录。按收藏时间倒序，已下架/已售出的商品仍会返回并带出当前状态")
    public Result<PageResult<ProductListVO>> mine(@Valid FavoriteQueryDTO query) {
        return Result.success(favoriteService.listMine(query));
    }
}
