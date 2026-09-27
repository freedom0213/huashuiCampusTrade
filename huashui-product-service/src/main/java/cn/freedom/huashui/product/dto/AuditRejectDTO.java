package cn.freedom.huashui.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核驳回请求。
 *
 * <p>驳回必须给出理由：它是卖家「改了再提」的唯一依据，
 * 没有理由的驳回等于只告诉对方「不行」，卖家只能盲猜重试。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "审核驳回请求")
public class AuditRejectDTO {

    @NotBlank(message = "请填写驳回原因")
    @Size(max = 255, message = "驳回原因不能超过 255 字")
    @Schema(description = "驳回原因", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
    private String reason;
}
