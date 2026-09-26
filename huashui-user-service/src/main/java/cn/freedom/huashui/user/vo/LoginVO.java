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

    @Schema(description = "有效期（分钟）")
    private Long expiresInMinutes;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    @Schema(description = "角色：1 学生，2 管理员")
    private Integer role;
}
