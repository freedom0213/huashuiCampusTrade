package cn.freedom.huashui.order.mapper;

import cn.freedom.huashui.order.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 订单数据访问。
 *
 * <p>没有自定义 XML：状态流转全部用条件更新（{@code LambdaUpdateWrapper} 的
 * {@code eq(status, 前置状态)}）表达，MyBatis-Plus 已经足够，
 * 额外写 XML 只会多一处需要同步维护的地方。
 *
 * @author freedom0213
 */
public interface OrderMapper extends BaseMapper<Order> {
}
