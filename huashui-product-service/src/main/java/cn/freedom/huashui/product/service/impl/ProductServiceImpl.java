package cn.freedom.huashui.product.service.impl;

import cn.freedom.huashui.common.context.UserContext;
import cn.freedom.huashui.common.enums.Campus;
import cn.freedom.huashui.common.enums.ProductCondition;
import cn.freedom.huashui.common.enums.ProductStatus;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.product.config.ProductProperties;
import cn.freedom.huashui.product.dto.ProductQueryDTO;
import cn.freedom.huashui.product.dto.ProductSaveDTO;
import cn.freedom.huashui.product.entity.Category;
import cn.freedom.huashui.product.entity.Product;
import cn.freedom.huashui.product.entity.ProductImage;
import cn.freedom.huashui.product.mapper.CategoryMapper;
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

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final ProductProperties productProperties;

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
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        Long currentUserId = UserContext.getUserId();
        boolean owned = currentUserId != null && currentUserId.equals(product.getSellerId());

        // 待审核 / 已驳回属于卖家的「私事」，对他人直接按不存在处理——
        // 返回「无权查看」等于告诉对方「这个 id 是存在的」，没必要泄露这个信息
        Integer status = product.getStatus();
        if ((Objects.equals(ProductStatus.PENDING_AUDIT.getCode(), status)
                || Objects.equals(ProductStatus.REJECTED.getCode(), status)) && !owned) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
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
        vo.setConditionDesc(descOfCondition(product.getConditionLevel()));
        vo.setStatus(product.getStatus());
        vo.setStatusDesc(descOfStatus(product.getStatus()));
        vo.setRejectReason(product.getRejectReason());
        vo.setViewCount(product.getViewCount());
        vo.setFavoriteCount(product.getFavoriteCount());
        vo.setPublishTime(product.getPublishTime());
        vo.setSoldTime(product.getSoldTime());
        vo.setSellerId(product.getSellerId());
        vo.setOwned(owned);
        vo.setCreateTime(product.getCreateTime());
        vo.setImageUrls(loadImageUrls(productId));
        return vo;
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

        List<ProductListVO> records = result.getRecords().stream().map(this::toListVO).toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    private ProductListVO toListVO(Product product) {
        ProductListVO vo = new ProductListVO();
        vo.setId(product.getId());
        vo.setTitle(product.getTitle());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setCoverUrl(product.getCoverUrl());
        vo.setTradePlace(product.getTradePlace());
        vo.setCampus(product.getCampus());
        vo.setConditionLevel(product.getConditionLevel());
        vo.setConditionDesc(descOfCondition(product.getConditionLevel()));
        vo.setStatus(product.getStatus());
        vo.setStatusDesc(descOfStatus(product.getStatus()));
        vo.setViewCount(product.getViewCount());
        vo.setFavoriteCount(product.getFavoriteCount());
        vo.setPublishTime(product.getPublishTime());
        return vo;
    }

    private String descOfCondition(Integer code) {
        ProductCondition condition = ProductCondition.of(code);
        return condition == null ? "" : condition.getDesc();
    }

    private String descOfStatus(Integer code) {
        ProductStatus status = ProductStatus.of(code);
        return status == null ? "" : status.getDesc();
    }
}
