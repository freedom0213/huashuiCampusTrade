package cn.freedom.huashui.user.service.impl;

import cn.freedom.huashui.common.context.UserContext;
import cn.freedom.huashui.common.enums.UserRole;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.common.util.JwtUtil;
import cn.freedom.huashui.user.dto.ChangePasswordDTO;
import cn.freedom.huashui.user.dto.LoginDTO;
import cn.freedom.huashui.user.dto.RegisterDTO;
import cn.freedom.huashui.user.dto.UpdateProfileDTO;
import cn.freedom.huashui.user.entity.User;
import cn.freedom.huashui.user.mapper.UserMapper;
import cn.freedom.huashui.user.service.UserService;
import cn.freedom.huashui.user.vo.LoginVO;
import cn.freedom.huashui.user.vo.UserBriefVO;
import cn.freedom.huashui.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务实现。
 *
 * @author freedom0213
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        // 先查一次是为了给出友好的错误提示；
        // 真正的唯一性保证来自数据库唯一索引（见下方 catch）
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()))) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()))) {
            throw new BizException(ResultCode.PHONE_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        // 存哈希，永不存明文；同一个密码每次加密结果都不同（BCrypt 自带随机盐）
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        // 学号未填写时存 NULL 而不是空串：唯一索引允许多个 NULL，但不允许多个 ''
        user.setStudentNo(StringUtils.hasText(dto.getStudentNo()) ? dto.getStudentNo() : null);
        user.setDept(dto.getDept() == null ? "" : dto.getDept());
        user.setAvatar("");
        user.setRole(UserRole.STUDENT.getCode());
        user.setStatus(STATUS_ENABLED);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发场景：两个请求同时通过了上面的 exists 检查。
            // 唯一索引是最后一道防线，把它翻译成业务异常，而不是抛出 500。
            log.warn("注册时触发唯一索引冲突 | username={}", dto.getUsername());
            throw new BizException(ResultCode.USERNAME_EXISTS, "用户名或手机号已被注册");
        }

        log.info("用户注册成功 | userId={} | username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));

        // 「用户不存在」和「密码错误」返回同一个错误码。
        // 如果分开提示，攻击者就能用它来枚举出平台上有哪些用户名。
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!Objects.equals(STATUS_ENABLED, user.getStatus())) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        String token = jwtUtil.generate(user.getId(), user.getRole());
        log.info("用户登录成功 | userId={} | username={}", user.getId(), user.getUsername());

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMinutes(jwtUtil.getTtlMinutes())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
    }

    @Override
    public UserVO getCurrentUser() {
        return toUserVO(requireUser(UserContext.requireUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentUser(UpdateProfileDTO dto) {
        Long userId = UserContext.requireUserId();
        requireUser(userId);

        User update = new User();
        update.setId(userId);
        update.setNickname(dto.getNickname());
        update.setAvatar(dto.getAvatar());
        update.setStudentNo(StringUtils.hasText(dto.getStudentNo()) ? dto.getStudentNo() : null);
        update.setDept(dto.getDept());

        // updateById 默认只更新「非 null」字段，因此 DTO 里没传的字段不会被动成 null，
        // 天然实现了「部分更新」的语义。
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = UserContext.requireUserId();
        User user = requireUser(userId);

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.OLD_PASSWORD_ERROR);
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);

        log.info("用户修改密码 | userId={}", userId);
    }

    @Override
    public UserBriefVO getPublicInfo(Long userId) {
        return toBriefVO(requireUser(userId));
    }

    @Override
    public String getContactPhone(Long userId) {
        // 要求登录：网关已经拦过一道，这里是第二道防线。
        // 如果将来有人绕过网关直连本服务，这一行仍然能挡住未登录请求。
        UserContext.requireUserId();
        return requireUser(userId).getPhone();
    }

    @Override
    public List<UserBriefVO> listBriefByIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .map(this::toBriefVO)
                .toList();
    }

    // ==================== 私有方法 ====================

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStudentNo(user.getStudentNo());
        vo.setDept(user.getDept());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    private UserBriefVO toBriefVO(User user) {
        UserBriefVO vo = new UserBriefVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setDept(user.getDept());
        return vo;
    }
}
