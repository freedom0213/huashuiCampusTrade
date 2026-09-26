package cn.freedom.huashui.order.service;

import java.math.BigDecimal;

/**
 * 支付服务。
 *
 * <p><b>本项目是模拟支付，资金不经手平台。</b>按钮的语义是「确认已线下付款」，
 * 而不是「在线支付」。这样做有三条硬理由：
 * <ol>
 *   <li>真实支付需要商户号，而商户号需要营业执照；</li>
 *   <li>平台一旦经手资金，就涉及资金池、清算、对账与监管合规；</li>
 *   <li>接了支付就必须接退款与对账，复杂度会失控。</li>
 * </ol>
 *
 * <p>面试口径：<b>平台定位为信息撮合，不介入资金流。</b>
 *
 * <p>之所以仍然抽成接口：将来若要接入真实支付，只需新增一个实现类替换
 * {@code MockPayServiceImpl}，订单状态机与商品流转那一整套逻辑都不需要动。
 * 这是「预留扩展点」，不是「提前实现」。
 *
 * @author freedom0213
 */
public interface PayService {

    /**
     * 确认收款。
     *
     * <p>当前实现只记一条日志——因为「线下已付款」本来就不需要平台做任何事。
     * 将来接真实支付时，这里会是「校验支付结果」的位置，
     * 并且应当<b>在返回成功之后</b>才允许订单流转为「已付款」。
     *
     * @param orderNo 订单号
     * @param amount  金额
     */
    void confirmPayment(String orderNo, BigDecimal amount);
}
