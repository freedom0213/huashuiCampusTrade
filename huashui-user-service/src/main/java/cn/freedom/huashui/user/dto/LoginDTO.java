package cn.freedom.huashui.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "登录请求")
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "freedom0213")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "abc123456")
    private String password;
}
