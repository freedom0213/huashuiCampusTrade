package cn.freedom.huashui.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录结果。
 *
 * @author freedom0213
 */
@Data
@Builder
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "访问令牌，后续请求放在请求头 Authorization: Bearer <token>")
    private String token;

    @Schema(description = "令牌类型，固定为 Bearer")
    private String tokenType;

    /**
     * 有效期（分钟）。
     *
     * <p><b>用 {@code Integer} 而非 {@code Long}：</b>common 把 Long 全局序列化成了字符串
     * （为了让雪花 ID 在前端不丢精度），而有效期是「数量」、必须保持数字，
     * 否则前端 {@code expiresInMinutes / 60} 这类计算会得到 NaN。
     * <p>约定：<b>Long 只用于 ID，数量类字段一律用 int / Integer。</b>
     */
    @Schema(description = "有效期（分钟），数字")
    private Integer expiresInMinutes;

    @Schema(description = "用户 id（字符串，雪花 ID 超出 JS 安全整数范围）")
    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    @Schema(description = "角色：1 学生，2 管理员")
    private Integer role;
}
