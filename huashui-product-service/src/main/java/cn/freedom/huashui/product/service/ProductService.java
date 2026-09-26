package cn.freedom.huashui.product.service;

import cn.freedom.huashui.common.api.product.ProductLockDTO;
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

    // ==================== 内部接口（仅供 order-service 调用） ====================

    /**
     * 锁定商品：在售 → 已锁定，成功时一并返回下单所需的商品快照。
     *
     * <p>并发安全由条件更新（{@code WHERE status = 在售}）+ 受影响行数保证，
     * 不需要额外加锁。两个买家同时下单时，只有一个人能改到状态。
     *
     * @throws cn.freedom.huashui.common.exception.BizException
     *         商品不存在 / 购买自己发布的商品 / 商品已被别人抢先买走
     */
    ProductLockDTO lockForOrder(Long productId, Long buyerId);

    /**
     * 解锁商品：已锁定 → 在售。
     *
     * <p>用于两种情况：order 侧建单失败后的补偿调用，以及消费「订单已取消」消息。
     * <b>本方法必须幂等且不抛异常</b>——重复调用与重复消费消息都会走到这里，
     * 一旦抛异常，MQ 会不断重投同一条消息直到进入死信队列。
     */
    void unlockForOrder(Long productId);

    /**
     * 标记商品已售出：已锁定 → 已售出（终态）。由买家确认付款时调用。
     */
    void markSold(Long productId);
}
