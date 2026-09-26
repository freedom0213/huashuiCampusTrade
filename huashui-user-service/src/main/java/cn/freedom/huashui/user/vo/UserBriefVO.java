package cn.freedom.huashui.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户公开信息（他人可见）。
 *
 * <p><b>刻意不含手机号。</b>手机号是私密信息，本项目只在商品详情页的
 * 「联系卖家」处、且用户已登录时才单独展示，绝不随列表批量返回——
 * 否则一次列表请求就能把全站用户的手机号爬走。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "用户公开信息（不含手机号）")
public class UserBriefVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String dept;
}
