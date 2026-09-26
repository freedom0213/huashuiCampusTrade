package cn.freedom.huashui.order.service;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.order.dto.OrderQueryDTO;
import cn.freedom.huashui.order.vo.OrderVO;

/**
 * 交易服务。
 *
 * <p>状态机：{@code 待支付 → 已付款 → 交易完成}，分支 {@code 待支付 → 已取消}。
 * 所有流转一律用条件更新（{@code WHERE status = 前置状态}）+ 判断受影响行数，
 * 不先查后改。
 *
 * @author freedom0213
 */
public interface OrderService {

    /**
     * 创建订单。商品必须处于「在售」，成功后商品被锁定。
     *
     * <p>跨服务无事务：调商品服务锁定 → 本地建单。
     * 锁定成功但建单失败时，会用同步补偿把商品解锁（详见实现类注释）。
     *
     * @param productId 商品 id
     * @return 新订单（含支付截止时间与剩余秒数，供前端倒计时）
     */
    OrderVO create(Long productId);

    /**
     * 订单详情。只有买卖双方可以查看。
     */
    OrderVO detail(String orderNo);

    /**
     * 我的订单。支持按视角（买到的 / 卖出的 / 全部）与状态筛选。
     */
    PageResult<OrderVO> listMine(OrderQueryDTO query);

    /**
     * 买家确认已线下付款。待支付 → 已付款，并把商品置为已售出。
     */
    void pay(String orderNo);

    /**
     * 确认交易完成。已付款 → 交易完成。买卖双方都可以操作。
     */
    void complete(String orderNo);

    /**
     * 取消订单。只有「待支付」可以取消；取消后发消息通知商品域恢复在售。
     *
     * @param orderNo 订单号
     * @param reason  取消原因，为空时按操作人身份自动生成
     */
    void cancel(String orderNo, String reason);

    /**
     * 卖家历史成交笔数（已付款 + 交易完成）。卖家主页展示用，无需登录。
     */
    int soldCount(Long sellerId);

    /**
     * 取消所有已超过支付截止时间且仍处于「待支付」的订单。返回本轮实际取消的笔数。
     *
     * <p>正式方案是 XXL-JOB（阶段 10）；在那之前由一个本地定时任务驱动，
     * 避免「下单后不付款」把商品永久锁死。
     */
    int closeExpiredOrders();
}
