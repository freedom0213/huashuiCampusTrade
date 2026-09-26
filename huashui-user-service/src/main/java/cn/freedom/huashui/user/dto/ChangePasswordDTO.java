package cn.freedom.huashui.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求。
 *
 * <p>必须校验旧密码：否则一旦 token 泄露，攻击者可以直接改密码锁死账号。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需为 6-32 位")
    @Schema(description = "新密码")
    private String newPassword;
}
