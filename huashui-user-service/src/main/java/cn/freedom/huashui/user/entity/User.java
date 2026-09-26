package cn.freedom.huashui.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应 {@code t_user}。
 *
 * <p>注意字段命名：数据库是下划线（{@code student_no}），实体是驼峰（{@code studentNo}），
 * 由 MyBatis-Plus 的 {@code map-underscore-to-camel-case} 自动映射。
 *
 * @author freedom0213
 */
@Data
@TableName("t_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。{@code ASSIGN_ID} 表示由 MyBatis-Plus 生成雪花 ID，
     * 而不是依赖数据库自增——这样多库多服务场景下不会冲突。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /**
     * 密码哈希（BCrypt）。<b>任何情况下都不允许返回给前端</b>，
     * 对外一律使用 {@code UserVO} 而不是本实体。
     */
    private String password;

    private String nickname;

    private String phone;

    private String avatar;

    private String studentNo;

    private String dept;

    /** 角色：1 学生，2 管理员（{@code UserRole}） */
    private Integer role;

    /** 状态：1 正常，0 禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。加 {@code @TableLogic} 后，MyBatis-Plus 会自动：
     * 查询时追加 {@code deleted = 0}；删除时改成 {@code UPDATE ... SET deleted = 1}。
     * 业务代码里不要手动判断这个字段。
     */
    @TableLogic
    private Integer deleted;
}
