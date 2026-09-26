package cn.freedom.huashui.product.service.impl;

import cn.freedom.huashui.common.constant.RedisKeys;
import cn.freedom.huashui.common.context.UserContext;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.product.cache.CacheService;
import cn.freedom.huashui.product.convert.ProductConverter;
import cn.freedom.huashui.product.dto.FavoriteQueryDTO;
import cn.freedom.huashui.product.entity.Favorite;
import cn.freedom.huashui.product.entity.Product;
import cn.freedom.huashui.product.mapper.FavoriteMapper;
import cn.freedom.huashui.product.mapper.ProductMapper;
import cn.freedom.huashui.product.service.FavoriteService;
import cn.freedom.huashui.product.vo.ProductListVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏服务实现。
 *
 * @author freedom0213
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final CacheService cacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long productId) {
        if (productId == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        Long userId = UserContext.requireUserId();

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        // 自己可以浏览自己的商品、也能进详情页，但不能收藏 ——
        // 给出明确提示而不是静默成功，静默成功会让用户以为收藏上了
        if (Objects.equals(product.getSellerId(), userId)) {
            throw new BizException(ResultCode.CANNOT_FAVORITE_OWN_PRODUCT);
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreateTime(LocalDateTime.now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 刻意不做「先查再插」：并发下两个请求可能都查到「未收藏」然后都执行插入。
            // 直接依赖 uk_user_product 唯一索引兜底，冲突时翻译成业务异常
            throw new BizException(ResultCode.ALREADY_FAVORITED);
        }

        // 收藏数原子自增。不先查后写：既多一次往返，又会在并发下互相覆盖
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .setSql("favorite_count = favorite_count + 1"));

        // 详情里带 favoriteCount，所以收藏数一变就要清掉商品详情缓存
        evictDetailCache(productId);
        log.info("收藏成功 | userId={} | productId={}", userId, productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long productId) {
        if (productId == null) {
            throw new BizException(ResultCode.NOT_FAVORITED);
        }
        Long userId = UserContext.requireUserId();

        // 用删除的受影响行数判断「之前是否收藏过」，不先查后删
        int affected = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (affected == 0) {
            throw new BizException(ResultCode.NOT_FAVORITED);
        }

        // 收藏数原子自减。带上 favorite_count > 0 的条件，防止计数被减成负数
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .gt(Product::getFavoriteCount, 0)
                .setSql("favorite_count = favorite_count - 1"));

        evictDetailCache(productId);
        log.info("取消收藏 | userId={} | productId={}", userId, productId);
    }

    @Override
    public PageResult<ProductListVO> listMine(FavoriteQueryDTO query) {
        Long userId = UserContext.requireUserId();

        // 先按收藏时间倒序分页取出收藏记录，再批量查商品。
        // 不用 JOIN 是为了让「查哪张表」始终留在同一个 Service 内，
        // 而且两次查询都命中索引（idx_user_id_create_time / 主键），代价很低
        Page<Favorite> page = new Page<>(query.getPage(), query.getSize());
        IPage<Favorite> favoritePage = favoriteMapper.selectPage(page, new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                // 兜底排序键：同一秒内收藏的多件商品若只按时间排，翻页时顺序不稳定
                .orderByDesc(Favorite::getId));

        List<Long> productIds = favoritePage.getRecords().stream()
                .map(Favorite::getProductId)
                .toList();
        if (productIds.isEmpty()) {
            return PageResult.of(favoritePage.getTotal(), favoritePage.getCurrent(),
                    favoritePage.getSize(), List.of());
        }

        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 按 productIds 的顺序重建列表：selectBatchIds 的返回顺序不保证与入参一致，
        // 直接用它的顺序会让「按收藏时间倒序」失效
        List<ProductListVO> records = productIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(ProductConverter::toListVO)
                .toList();

        return PageResult.of(favoritePage.getTotal(), favoritePage.getCurrent(),
                favoritePage.getSize(), records);
    }

    private void evictDetailCache(Long productId) {
        cacheService.deleteAfterCommit(List.of(RedisKeys.productDetail(productId)));
    }
}
