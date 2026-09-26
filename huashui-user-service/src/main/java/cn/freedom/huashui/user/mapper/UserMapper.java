package cn.freedom.huashui.user.mapper;

import cn.freedom.huashui.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>继承 {@code BaseMapper<User>} 即自动获得单表增删改查、条件构造、分页等能力，
 * 阶段 4 的注册 / 登录 / 改资料全部够用，因此<b>暂时不写 XML</b>。
 * 等真出现「多表关联或复杂统计」的 SQL 时再补 XML，不要为了形式而写。
 *
 * @author freedom0213
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
