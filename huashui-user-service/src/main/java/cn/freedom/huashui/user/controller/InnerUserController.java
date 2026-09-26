package cn.freedom.huashui.user.controller;

import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.user.service.UserService;
import cn.freedom.huashui.user.vo.UserBriefVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间内部接口。
 *
 * <p>路径统一以 {@code /inner} 开头，与对外接口物理隔离。
 * 网关的路由只覆盖 {@code /api/user/**}（StripPrefix 后是 {@code /user/**}），
 * 因此 {@code /inner/**} <b>根本无法从网关进来</b>，只有服务间直接调用才能访问。
 *
 * <p>为什么必须提供批量接口：商品列表一页 20 条，如果为了拿卖家昵称逐个调用
 * 会变成 20 次远程调用（典型的 N+1 问题），一次批量查询即可解决。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/inner/user")
@RequiredArgsConstructor
@Tag(name = "内部接口", description = "仅供其他微服务调用，不对网关暴露")
public class InnerUserController {

    private final UserService userService;

    @GetMapping("/batch")
    @Operation(summary = "按 id 批量查询用户公开信息")
    public Result<List<UserBriefVO>> batch(@RequestParam("ids") List<Long> ids) {
        return Result.success(userService.listBriefByIds(ids));
    }
}
