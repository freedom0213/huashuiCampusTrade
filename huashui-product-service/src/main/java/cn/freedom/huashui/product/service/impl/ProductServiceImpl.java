package cn.freedom.huashui.product.service.impl;

import cn.freedom.huashui.common.api.product.ProductLockDTO;
import cn.freedom.huashui.common.constant.RedisKeys;
import cn.freedom.huashui.common.context.UserContext;
import cn.freedom.huashui.common.enums.Campus;
import cn.freedom.huashui.common.enums.ProductStatus;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.product.cache.CacheService;
import cn.freedom.huashui.product.config.ProductProperties;
import cn.freedom.huashui.product.convert.ProductConverter;
import cn.freedom.huashui.product.dto.ProductQueryDTO;
import cn.freedom.huashui.product.dto.ProductSaveDTO;
import cn.freedom.huashui.product.entity.Category;
import cn.freedom.huashui.product.entity.Favorite;
import cn.freedom.huashui.product.entity.Product;
import cn.freedom.huashui.product.entity.ProductImage;
import cn.freedom.huashui.product.mapper.CategoryMapper;
import cn.freedom.huashui.product.mapper.FavoriteMapper;
import cn.freedom.huashui.product.mapper.ProductImageMapper;
import cn.freedom.huashui.product.mapper.ProductMapper;
import cn.freedom.huashui.product.service.ProductService;
import cn.freedom.huashui.product.vo.ProductDetailVO;
import cn.freedom.huashui.product.vo.ProductListVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 商品服务实现。
 *
 * @author freedom0213
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String SORT_PRICE_ASC = "price_asc";
    private static final String SORT_PRICE_DESC = "price_desc";
    private static final String SORT_VIEWS = "views";

    /**
     * 商品详情缓存的基础 TTL。实际写入时还会加最多 1/3 的随机抖动（见 {@link CacheService}），
     * 避免一大批商品在同一时刻集中过期、请求同时回源（缓存雪崩）。
     */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final FavoriteMapper favoriteMapper;
    private final ProductProperties productProperties;
    private final CacheService cacheService;

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(ProductSaveDTO dto) {
        Long sellerId = UserContext.requireUserId();
        validateCategory(dto.getCategoryId());
        validateCampus(dto.getCampus());

        Product product = new Product();
        product.setSellerId(sellerId);
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setRejectReason("");
        applyFormFields(product, dto);

        boolean auditRequired = productProperties.isAuditRequired();
        product.setStatus(auditRequired
                ? ProductStatus.PENDING_AUDIT.getCode()
                : ProductStatus.ON_SALE.getCode());
        product.setPublishTime(auditRequired ? null : LocalDateTime.now());

        productMapper.insert(product);
        saveImages(product.getId(), dto.getImageUrls());

        log.info("商品发布成功 | productId={} | sellerId={} | auditRequired={}",
                product.getId(), sellerId, auditRequired);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long productId, ProductSaveDTO dto) {
        Long userId = UserContext.requireUserId();
        Product product = requireOwnedProduct(productId, userId);

        // 交易中或已售出的商品不允许编辑：
        // 订单里存的是下单时刻的快照，改了商品会让两边对不上，且容易被用来「成交后改价」
        Integer status = product.getStatus();
        if (Objects.equals(ProductStatus.LOCKED.getCode(), status)
                || Objects.equals(ProductStatus.SOLD.getCode(), status)) {
            throw new BizException(ResultCode.PRODUCT_STATUS_ILLEGAL, "商品正在交易中或已售出，不能编辑");
        }

        validateCategory(dto.getCategoryId());
        validateCampus(dto.getCampus());
        applyFormFields(product, dto);

        // 需要审核时，任何编辑都要重新走一遍审核（避免「先上架不合规商品再改成合规」绕过审核）
        if (productProperties.isAuditRequired()) {
            product.setStatus(ProductStatus.PENDING_AUDIT.getCode());
            product.setRejectReason("");
            product.setPublishTime(null);
        }

        productMapper.updateById(product);

        // 图片整体替换：先删后插。
        // 图片表没有 deleted 字段，属于商品的附属数据，不需要单独留痕
        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
        saveImages(productId, dto.getImageUrls());

        evictDetailCache(productId);
        log.info("商品编辑成功 | productId={} | sellerId={}", productId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offShelf(Long productId) {
        Long userId = UserContext.requireUserId();
        // 先做一次可读性校验，让「不存在」「不是你的商品」给出准确提示
        requireOwnedProduct(productId, userId);

        // 再用条件更新做状态流转：把「判断状态」和「改状态」合成一条原子语句，
        // 并发下不会出现「两个请求都判断成功、都执行了变更」
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getSellerId, userId)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getStatus, ProductStatus.OFF_SHELF.getCode())
                .set(Product::getUpdateTime, LocalDateTime.now()));

        if (affected == 0) {
            throw new BizException(ResultCode.PRODUCT_STATUS_ILLEGAL, "只有「在售」的商品才能下架");
        }
        evictDetailCache(productId);
        log.info("商品下架 | productId={}", productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onShelf(Long productId) {
        Long userId = UserContext.requireUserId();
        requireOwnedProduct(productId, userId);

        boolean auditRequired = productProperties.isAuditRequired();
        int targetStatus = auditRequired
                ? ProductStatus.PENDING_AUDIT.getCode()
                : ProductStatus.ON_SALE.getCode();

        LambdaUpdateWrapper<Product> update = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getSellerId, userId)
                .eq(Product::getStatus, ProductStatus.OFF_SHELF.getCode())
                .set(Product::getStatus, targetStatus)
                .set(Product::getUpdateTime, LocalDateTime.now());
        if (!auditRequired) {
            update.set(Product::getPublishTime, LocalDateTime.now());
        }

        int affected = productMapper.update(null, update);
        if (affected == 0) {
            throw new BizException(ResultCode.PRODUCT_STATUS_ILLEGAL, "只有「已下架」的商品才能重新上架");
        }
        evictDetailCache(productId);
        log.info("商品重新上架 | productId={} | targetStatus={}", productId, targetStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId) {
        Long userId = UserContext.requireUserId();
        Product product = requireOwnedProduct(productId, userId);

        // 已被下单锁定的商品删掉后，买家那边会出现「订单里的商品查不到」的诡异现象
        if (Objects.equals(ProductStatus.LOCKED.getCode(), product.getStatus())) {
            throw new BizException(ResultCode.PRODUCT_STATUS_ILLEGAL, "商品已被下单锁定，不能删除");
        }

        // @TableLogic 会把这条 delete 改写成 UPDATE ... SET deleted = 1
        productMapper.deleteById(productId);
        evictDetailCache(productId);
        log.info("商品已逻辑删除 | productId={} | sellerId={}", productId, userId);
    }

    // ==================== 读操作 ====================

    @Override
    public PageResult<ProductListVO> list(ProductQueryDTO query) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 公开列表强制只看在售：即使前端传了 status 也忽略，
        // 否则待审核 / 已驳回的商品会被翻出来
        wrapper.eq(Product::getStatus, ProductStatus.ON_SALE.getCode());
        applyFilters(wrapper, query);
        applySort(wrapper, query.getSort());
        return pageQuery(query, wrapper);
    }

    @Override
    public PageResult<ProductListVO> listMine(ProductQueryDTO query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSellerId, userId);
        if (query.getStatus() != null) {
            wrapper.eq(Product::getStatus, query.getStatus());
        }
        applyFilters(wrapper, query);
        // 「我的发布」默认按创建时间倒序（刚发的排最前），而不是上架时间
        wrapper.orderByDesc(Product::getCreateTime);
        wrapper.orderByDesc(Product::getId);
        return pageQuery(query, wrapper);
    }

    @Override
    public ProductDetailVO detail(Long productId) {
        if (productId == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        Long currentUserId = UserContext.getUserId();

        // 缓存里存的只是「商品本身」的数据，与「谁在看」无关。
        // 下面三步（权限判断、owned、favorited）都依赖当前登录用户，
        // 因此必须在缓存之外重算 —— 否则会把 A 用户看到的结果原样发给 B 用户。
        ProductDetailVO vo = cacheService.getOrLoad(
                RedisKeys.productDetail(productId),
                ProductDetailVO.class,
                DETAIL_TTL,
                () -> loadDetailFromDb(productId));

        // 回源返回 null 表示商品确实不存在（CacheService 已把它缓存成空值以防穿透）。
        // 这里必须补上业务语义 —— 少了这一步，下面取 sellerId 会抛 NPE，
        // 最终被兜底成「500 系统繁忙」，把「商品不存在」说成了「系统故障」
        if (vo == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        boolean owned = currentUserId != null && currentUserId.equals(vo.getSellerId());

        // 待审核 / 已驳回属于卖家的「私事」，对他人直接按不存在处理——
        // 返回「无权查看」等于告诉对方「这个 id 是存在的」，没必要泄露这个信息
        Integer status = vo.getStatus();
        if ((Objects.equals(ProductStatus.PENDING_AUDIT.getCode(), status)
                || Objects.equals(ProductStatus.REJECTED.getCode(), status)) && !owned) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        vo.setOwned(owned);
        vo.setFavorited(isFavorited(currentUserId, productId, owned));
        vo.setViewCount(currentViewCount(vo.getViewCount(), productId));
        return vo;
    }

    /**
     * 从数据库组装详情。
     *
     * <p><b>只组装与登录用户无关的字段</b>：{@code owned} 与 {@code favorited} 先置为 false，
     * 由 {@link #detail} 在缓存之外按当前用户重算。
     *
     * <p>返回 {@code null} 表示商品不存在，{@link CacheService} 会把它缓存成空值，
     * 后续请求在 60 秒内不再打数据库 —— 这就是防缓存穿透。
     */
    private ProductDetailVO loadDetailFromDb(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }

        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setTitle(product.getTitle());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setCoverUrl(product.getCoverUrl());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(loadCategoryName(product.getCategoryId()));
        vo.setCampus(product.getCampus());
        vo.setTradePlace(product.getTradePlace());
        vo.setConditionLevel(product.getConditionLevel());
        vo.setConditionDesc(ProductConverter.descOfCondition(product.getConditionLevel()));
        vo.setStatus(product.getStatus());
        vo.setStatusDesc(ProductConverter.descOfStatus(product.getStatus()));
        vo.setRejectReason(product.getRejectReason());
        vo.setViewCount(product.getViewCount());
        vo.setFavoriteCount(product.getFavoriteCount());
        vo.setPublishTime(product.getPublishTime());
        vo.setSoldTime(product.getSoldTime());
        vo.setSellerId(product.getSellerId());
        vo.setCreateTime(product.getCreateTime());
        vo.setImageUrls(loadImageUrls(productId));
        // 下面两个字段依赖登录用户，这里只占位，真正的值在 detail() 里算
        vo.setOwned(false);
        vo.setFavorited(false);
        return vo;
    }

    /**
     * 当前用户是否已收藏该商品。
     *
     * <p>未登录直接返回 false；<b>自己的商品也返回 false</b> ——
     * 自己可以浏览、可以进详情页，但不允许收藏，按钮应当是不可用状态而不是已收藏状态。
     */
    private boolean isFavorited(Long userId, Long productId, boolean owned) {
        if (userId == null || owned) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)) > 0;
    }

    /**
     * 浏览量：Redis 累加 + 与数据库值合并成展示值。
     *
     * <p><b>为什么不直接 {@code UPDATE view_count = view_count + 1}：</b>
     * 那会让每次浏览都对同一行加排他锁，热门商品上形成行锁热点、请求排队。
     * 改为在 Redis 自增后，由定时任务（阶段 10）用 {@code DECRBY} 取差值批量写回，
     * 数据库压力从「每次浏览一次写」降到「每 5 分钟一次写」。
     *
     * <p>代价是每次刷新都会 +1（未做防刷）。真实 PV 统计本来也是按请求计数，
     * 而防刷需要引入用户/设备维度去重，超出本项目范围。
     *
     * <p>Redis 不可用时降级为只返回数据库里的值 —— 「看商品」这件事不该因为计数失败而失败。
     */
    private Integer currentViewCount(Integer dbValue, Long productId) {
        int base = dbValue == null ? 0 : dbValue;
        try {
            long increment = cacheService.increment(RedisKeys.productView(productId));
            // 同时记进「有待结算」集合：阶段 10 的结算任务据此扫描，
            // 避免为了找出哪些商品有增量而全表扫描
            cacheService.addToSet(RedisKeys.PRODUCT_DIRTY_VIEWS, String.valueOf(productId));
            return base + (int) Math.min(increment, Integer.MAX_VALUE);
        } catch (Exception e) {
            log.warn("浏览量计数失败，降级返回数据库值 | productId={}", productId, e);
            return base;
        }
    }

    /**
     * 商品数据变化后清掉它的详情缓存。
     *
     * <p>策略是 Cache-Aside 的「<b>先更新数据库，再删缓存</b>」：删比更新可靠 ——
     * 并发更新时写缓存会把旧值写进去，而删缓存最坏只是多一次回源。
     *
     * <p>且必须等<b>事务提交之后</b>再删（见 {@code CacheService#deleteAfterCommit}）：
     * 提交前删的话，其他线程可能在新数据落库前读到旧值并写回缓存，
     * 此后缓存与数据库会长期不一致。
     */
    private void evictDetailCache(Long productId) {
        cacheService.deleteAfterCommit(List.of(RedisKeys.productDetail(productId)));
    }

    // ==================== 内部接口（仅供 order-service 调用） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductLockDTO lockForOrder(Long productId, Long buyerId) {
        if (productId == null || buyerId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品 id 与买家 id 不能为空");
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (Objects.equals(product.getSellerId(), buyerId)) {
            throw new BizException(ResultCode.CANNOT_BUY_OWN_PRODUCT);
        }

        // 状态判断只写在下面这一处条件更新里，不在上面再判断一次：
        // 两处判断会让「以哪个为准」变得含糊，而且并发下那次判断本就无意义
        // （判断通过之后、更新之前，状态仍可能被别人改掉）。
        // 上面的预检查只负责给出更准确的提示（商品不存在 / 买自己的商品）。
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getStatus, ProductStatus.LOCKED.getCode())
                .set(Product::getUpdateTime, LocalDateTime.now()));

        if (affected == 0) {
            // 商品曾存在但现在已不是在售：被别人抢先买走 / 卖家下架 / 已售出 / 已被逻辑删除。
            // 统一提示「手慢了」，不暴露具体原因——否则买家能据此推断卖家的操作意图。
            log.info("锁定商品失败，商品已不是在售状态 | productId={} | buyerId={}", productId, buyerId);
            throw new BizException(ResultCode.PRODUCT_LOCK_FAILED);
        }

        ProductLockDTO dto = new ProductLockDTO();
        dto.setProductId(product.getId());
        dto.setSellerId(product.getSellerId());
        dto.setTitle(product.getTitle());
        dto.setCoverUrl(product.getCoverUrl());
        dto.setPrice(product.getPrice());

        log.info("商品锁定成功 | productId={} | buyerId={} | sellerId={}",
                productId, buyerId, product.getSellerId());
        evictDetailCache(productId);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockForOrder(Long productId) {
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.LOCKED.getCode())
                .set(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getUpdateTime, LocalDateTime.now()));

        if (affected == 0) {
            // 幂等分支：重复解锁、MQ 重复消费、商品已售出或已删除都会走到这里。
            // 只记 warn 不抛异常——抛了会让 MQ 反复重投同一条消息，
            // 最终把一条「其实已经生效」的消息送进死信队列。
            log.warn("解锁商品未生效，商品当前不是「已锁定」状态（重复解锁或状态已变更）| productId={}", productId);
            return;
        }
        evictDetailCache(productId);
        log.info("商品已恢复在售 | productId={}", productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSold(Long productId) {
        LocalDateTime now = LocalDateTime.now();
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.LOCKED.getCode())
                .set(Product::getStatus, ProductStatus.SOLD.getCode())
                .set(Product::getSoldTime, now)
                .set(Product::getUpdateTime, now));

        if (affected == 0) {
            // 幂等分支：重复标记（商品已是「已售出」）直接放过
            Product product = productMapper.selectById(productId);
            if (product != null && Objects.equals(ProductStatus.SOLD.getCode(), product.getStatus())) {
                log.warn("商品已是「已售出」，重复标记被忽略 | productId={}", productId);
                return;
            }
            // 其余情况说明订单与商品的状态已经不一致（例如商品被别人买走），
            // 属于数据异常，必须立刻暴露而不是静默放过
            throw new BizException(ResultCode.PRODUCT_STATUS_ILLEGAL,
                    "商品不处于「已锁定」状态，无法标记为已售出");
        }
        evictDetailCache(productId);
        log.info("商品已售出 | productId={}", productId);
    }

    // ==================== 私有方法 ====================

    private void applyFormFields(Product product, ProductSaveDTO dto) {
        product.setCategoryId(dto.getCategoryId());
        // 用枚举归一化，保证入库的一定是三个合法值之一，而不是前端传什么存什么
        product.setCampus(Campus.of(dto.getCampus()).getName());
        product.setTitle(dto.getTitle().trim());
        product.setDescription(dto.getDescription());
        product.setConditionLevel(dto.getConditionLevel());
        product.setPrice(dto.getPrice());
        product.setOriginalPrice(dto.getOriginalPrice());
        product.setTradePlace(dto.getTradePlace().trim());
        product.setCoverUrl(dto.getImageUrls().get(0).trim());
    }

    private void saveImages(Long productId, List<String> urls) {
        for (int i = 0; i < urls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(urls.get(i).trim());
            image.setSort(i);
            image.setCreateTime(LocalDateTime.now());
            productImageMapper.insert(image);
        }
    }

    private List<String> loadImageUrls(Long productId) {
        return productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getSort))
                .stream()
                .map(ProductImage::getUrl)
                .toList();
    }

    private String loadCategoryName(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? "" : category.getName();
    }

    private Product requireOwnedProduct(Long productId, Long userId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (!Objects.equals(product.getSellerId(), userId)) {
            throw new BizException(ResultCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }

    private void validateCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || !Objects.equals(1, category.getStatus())) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }
    }

    private void validateCampus(String campus) {
        if (!Campus.isValid(campus)) {
            throw new BizException(ResultCode.PARAM_ERROR, "校区取值不合法，仅支持：龙子湖 / 花园 / 江淮");
        }
    }

    private void applyFilters(LambdaQueryWrapper<Product> wrapper, ProductQueryDTO query) {
        if (query.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, query.getCategoryId());
        }
        if (StringUtils.hasText(query.getKw())) {
            // 已知限制：LIKE '%kw%' 用不到索引，数据量上来后应改接 ES（见总设 §十七）
            wrapper.like(Product::getTitle, query.getKw().trim());
        }
        if (StringUtils.hasText(query.getCampus())) {
            wrapper.eq(Product::getCampus, query.getCampus().trim());
        }
        if (query.getConditionLevel() != null) {
            // 语义是「不低于该成色」。因为数值越小越新，所以是 <=
            wrapper.le(Product::getConditionLevel, query.getConditionLevel());
        }
        if (query.getSellerId() != null) {
            wrapper.eq(Product::getSellerId, query.getSellerId());
        }
    }

    /**
     * 排序。用 switch 而不是把字符串拼进 SQL，从根上排除 SQL 注入。
     * 非法值兜底成 newest 而不是报错——排序参数写错不值得给用户一个错误页。
     */
    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        String safeSort = StringUtils.hasText(sort) ? sort.trim() : "";
        switch (safeSort) {
            case SORT_PRICE_ASC -> wrapper.orderByAsc(Product::getPrice);
            case SORT_PRICE_DESC -> wrapper.orderByDesc(Product::getPrice);
            case SORT_VIEWS -> wrapper.orderByDesc(Product::getViewCount);
            default -> wrapper.orderByDesc(Product::getPublishTime);
        }
        // 兜底排序键：价格可能重复，不加 id 会导致翻页时记录顺序不稳定、出现重复或漏项
        wrapper.orderByDesc(Product::getId);
    }

    private PageResult<ProductListVO> pageQuery(ProductQueryDTO query, LambdaQueryWrapper<Product> wrapper) {
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        IPage<Product> result = productMapper.selectPage(page, wrapper);

        List<ProductListVO> records = result.getRecords().stream()
                .map(ProductConverter::toListVO)
                .toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

}
