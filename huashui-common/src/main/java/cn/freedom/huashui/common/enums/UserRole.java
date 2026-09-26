package cn.freedom.huashui.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 用户角色。
 *
 * <p>本项目刻意不做 RBAC 角色权限表：只有「学生」和「管理员」两种角色，
 * 一个字段足以表达，引入权限表属于过度设计。
 *
 * @author freedom0213
 */
@Getter
public enum UserRole {

    STUDENT(1, "学生"),
    ADMIN(2, "管理员");

    private final Integer code;
    private final String desc;

    UserRole(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserRole of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static boolean isAdmin(Integer code) {
        return ADMIN.code.equals(code);
    }
}
