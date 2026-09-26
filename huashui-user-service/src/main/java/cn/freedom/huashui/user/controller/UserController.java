package cn.freedom.huashui.user.controller;

import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.user.dto.ChangePasswordDTO;
import cn.freedom.huashui.user.dto.LoginDTO;
import cn.freedom.huashui.user.dto.RegisterDTO;
import cn.freedom.huashui.user.dto.UpdateProfileDTO;
import cn.freedom.huashui.user.service.UserService;
import cn.freedom.huashui.user.vo.LoginVO;
import cn.freedom.huashui.user.vo.UserBriefVO;
import cn.freedom.huashui.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 *
 * <p>注意这里<b>没有任何 try-catch</b>：业务失败一律由 Service 抛 {@code BizException}，
 * 交给 huashui-common 里的全局异常处理器转成统一响应体。
 *
 * <p>网关会把 {@code /api/user/register} 改写为 {@code /user/register} 后转发到这里，
 * 所以本类的映射路径不带 {@code /api} 前缀。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户接口", description = "注册 / 登录 / 用户信息")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "注册", description = "无需登录。用户名与手机号必须唯一")
    public Result<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "无需登录。成功后返回 JWT 令牌")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    @GetMapping("/info")
    @Operation(summary = "当前登录用户信息", description = "需要登录。身份由网关从令牌解析后透传")
    public Result<UserVO> info() {
        return Result.success(userService.getCurrentUser());
    }

    @PutMapping("/info")
    @Operation(summary = "修改个人资料", description = "需要登录。只更新请求中出现的字段")
    public Result<Void> updateInfo(@Valid @RequestBody UpdateProfileDTO dto) {
        userService.updateCurrentUser(dto);
        return Result.success();
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "需要登录。必须校验原密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "查看指定用户公开信息", description = "不含手机号")
    public Result<UserBriefVO> detail(@PathVariable("id") Long id) {
        return Result.success(userService.getPublicInfo(id));
    }

    @GetMapping("/{id}/contact")
    @Operation(summary = "获取卖家联系方式",
            description = "需要登录。返回手机号，仅在商品详情页按需调用；不在任何列表接口中批量返回")
    public Result<String> contact(@PathVariable("id") Long id) {
        return Result.success(userService.getContactPhone(id));
    }
}
