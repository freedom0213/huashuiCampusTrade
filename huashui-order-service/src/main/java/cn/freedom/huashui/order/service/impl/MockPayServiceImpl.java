package cn.freedom.huashui.order.service.impl;

import cn.freedom.huashui.order.service.PayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 模拟支付实现。
 *
 * <p>什么也不做，只记一条日志——这正是「线下确认付款」这个业务动作的全部内容：
 * 买卖双方当面交割，平台只是把这个事实记进订单状态。
 *
 * @author freedom0213
 */
@Slf4j
@Service
public class MockPayServiceImpl implements PayService {

    @Override
    public void confirmPayment(String orderNo, BigDecimal amount) {
        log.info("[模拟支付] 买家确认已线下付款 | orderNo={} | amount={}（资金不经手平台）",
                orderNo, amount);
    }
}
