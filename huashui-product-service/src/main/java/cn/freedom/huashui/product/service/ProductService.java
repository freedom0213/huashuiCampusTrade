package cn.freedom.huashui.product.service;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.product.dto.ProductQueryDTO;
import cn.freedom.huashui.product.dto.ProductSaveDTO;
import cn.freedom.huashui.product.vo.ProductDetailVO;
import cn.freedom.huashui.product.vo.ProductListVO;

/**
 * 商品服务。
 *
 * @author freedom0213
 */
public interface ProductService {

    /**
     * 发布商品，返回新商品 id。
     * <p>是否进入「待审核」由 {@code huashui.product.audit-required} 决定。
     */
    Long publish(ProductSaveDTO dto);

    /**
     * 编辑商品。只有卖家本人可操作，且商品不能处于交易中/已售出状态。
     */
    void update(Long productId, ProductSaveDTO dto);

    /**
     * 下架（在售 → 已下架）。
     */
    void offShelf(Long productId);

    /**
     * 重新上架（已下架 → 在售 / 待审核）。
     */
    void onShelf(Long productId);

    /**
     * 删除（逻辑删除）。已被订单锁定的商品不允许删除。
     */
    void delete(Long productId);

    /**
     * 公开商品列表：只返回在售商品，支持分类 / 关键词 / 校区 / 成色 / 卖家 / 排序。
     */
    PageResult<ProductListVO> list(ProductQueryDTO query);

    /**
     * 我的发布：返回当前登录用户的全部商品，可按状态筛选。
     */
    PageResult<ProductListVO> listMine(ProductQueryDTO query);

    /**
     * 商品详情。待审核与已驳回的商品只有卖家本人可见。
     */
    ProductDetailVO detail(Long productId);
}
