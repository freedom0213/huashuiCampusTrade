package cn.freedom.huashui.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 *
 * <p>为什么单独建 DTO 而不直接用 {@code User} 实体接收参数：
 * <ol>
 *   <li>实体里有 {@code role}、{@code status} 这类<b>不允许前端指定</b>的字段，
 *       用实体接参等于把提权入口直接暴露出去（攻击者传 {@code role=2} 就成了管理员）</li>
 *   <li>校验规则属于接口契约，放在 DTO 上语义更清晰</li>
 * </ol>
 *
 * @author freedom0213
 */
@Data
@Schema(description = "注册请求")
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
    @Schema(description = "用户名", example = "freedom0213")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    @Schema(description = "密码（明文传输，服务端用 BCrypt 加密存储）", example = "abc123456")
    private String password;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号，仅做唯一性校验，不发验证码", example = "13800138000")
    private String phone;

    @Size(max = 20, message = "昵称最长 20 个字符")
    @Schema(description = "昵称，不填则默认等于用户名", example = "自由")
    private String nickname;

    @Size(max = 32)
    @Schema(description = "学号，选填")
    private String studentNo;

    @Size(max = 64)
    @Schema(description = "院系，选填", example = "软件工程")
    private String dept;
}
