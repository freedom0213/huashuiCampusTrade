package cn.freedom.huashui.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求。
 *
 * <p>刻意<b>不包含</b>手机号与密码：手机号是登录凭证、密码是敏感信息，
 * 它们的修改应该走独立的接口并做额外校验（如验证旧密码），
 * 混在资料修改里容易出现「改昵称顺手把密码也改了」这类事故。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "修改个人资料请求")
public class UpdateProfileDTO {

    @Size(max = 20, message = "昵称最长 20 个字符")
    @Schema(description = "昵称")
    private String nickname;

    @Size(max = 255)
    @Schema(description = "头像地址")
    private String avatar;

    @Size(max = 32)
    @Schema(description = "学号")
    private String studentNo;

    @Size(max = 64)
    @Schema(description = "院系")
    private String dept;
}
