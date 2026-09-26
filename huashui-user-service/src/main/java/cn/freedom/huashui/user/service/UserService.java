package cn.freedom.huashui.user.service;

import cn.freedom.huashui.user.dto.ChangePasswordDTO;
import cn.freedom.huashui.user.dto.LoginDTO;
import cn.freedom.huashui.user.dto.RegisterDTO;
import cn.freedom.huashui.user.dto.UpdateProfileDTO;
import cn.freedom.huashui.user.vo.LoginVO;
import cn.freedom.huashui.user.vo.UserBriefVO;
import cn.freedom.huashui.user.vo.UserVO;

import java.util.List;

/**
 * 用户服务。
 *
 * <p>定义成接口 + 实现类，而不是直接写一个类：
 * 一方面符合分层规范，另一方面将来接入 Spring Cache 等基于代理的机制时，
 * 没有接口会有额外限制。
 *
 * @author freedom0213
 */
public interface UserService {

    /**
     * 注册，返回新用户 id。
     */
    Long register(RegisterDTO dto);

    /**
     * 登录，返回令牌与基础信息。
     */
    LoginVO login(LoginDTO dto);

    /**
     * 查询当前登录用户信息。
     */
    UserVO getCurrentUser();

    /**
     * 修改当前登录用户资料。
     */
    void updateCurrentUser(UpdateProfileDTO dto);

    /**
     * 修改当前登录用户密码。
     */
    void changePassword(ChangePasswordDTO dto);

    /**
     * 查询指定用户的公开信息。
     */
    UserBriefVO getPublicInfo(Long userId);

    /**
     * 获取指定用户的联系方式（手机号），仅登录用户可调用。
     * <p>刻意做成独立接口而不是塞进 {@link #getPublicInfo}：
     * 手机号是敏感字段，独立出口才能单独限流、单独审计，也避免它出现在任何批量响应里。
     */
    String getContactPhone(Long userId);

    /**
     * 按 id 批量查询用户公开信息（供其他服务调用，避免 N+1 次远程调用）。
     */
    List<UserBriefVO> listBriefByIds(List<Long> userIds);
}
