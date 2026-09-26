package cn.freedom.huashui.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息（本人可见）。
 *
 * <p>刻意不让实体直接对外返回：{@code password} 字段一旦被 Jackson 序列化出去，
 * 就是一次凭据泄露。所有对外接口一律返回 VO，实体只在服务层内部使用。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "用户信息（本人可见）")
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private String avatar;

    private String studentNo;

    private String dept;

    @Schema(description = "角色：1 学生，2 管理员")
    private Integer role;

    @Schema(description = "状态：1 正常，0 禁用")
    private Integer status;

    private LocalDateTime createTime;
}
