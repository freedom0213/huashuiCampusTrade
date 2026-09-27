package cn.freedom.huashui.product.service;

import cn.freedom.huashui.common.api.product.ProductLockDTO;
import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.product.dto.ProductQueryDTO;
import cn.freedom.huashui.product.dto.ProductSaveDTO;
import cn.freedom.huashui.product.vo.ProductDetailVO;
import cn.freedom.huashui.product.vo.ProductListVO;

import java.util.List;

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

    // ==================== 管理端（阶段 13：商品审核） ====================

    /**
     * 审核列表：按状态查商品（默认待审核，可查已驳回）。仅管理员可调用。
     */
    PageResult<ProductListVO> listForAudit(Integer status, Integer current, Integer size);

    /**
     * 审核通过：待审核 → 在售，并补记发布时间。仅管理员可调用。
     */
    void approve(Long productId);

    /**
     * 审核驳回：待审核 → 已驳回，记录驳回原因。仅管理员可调用。
     */
    void reject(Long productId, String reason);

    /**
     * 强制下架：在售 → 已下架。仅管理员可调用。
     * <p>已锁定 / 已售出的商品有在途交易，不允许强制下架；
     * 卖家重新上架时会因审核开关再次进入待审核，无法借机绕过审核。
     */
    void forceOffShelf(Long productId);

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

    /**
     * 查询「已锁定且长时间未变动」的商品 id（阶段 10 的兜底扫描任务用）。
     *
     * <p>「商品被锁定了、但订单没建出来」时商品会永远停在「已锁定」，
     * 没有任何机制会救它。这个方法提供候选名单，由 order 侧确认有无有效订单。
     *
     * @param beforeMinutes 锁定超过多少分钟算超时
     * @param limit         最多返回多少条
     */
    List<Long> listStaleLockedIds(int beforeMinutes, int limit);
}
