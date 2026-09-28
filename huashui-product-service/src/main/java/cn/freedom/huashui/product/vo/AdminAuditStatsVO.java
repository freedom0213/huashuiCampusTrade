package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理端审核统计（2026-09-28 扩展契约，见仓库根「管理端-后端接口扩展需求.md」）。
 *
 * <p>四个计数对应管理端四张统计卡与状态 Tab；「全部」由前端求和。
 * 只统计管理相关的四个状态——LOCKED / SOLD 是交易在途 / 终态，
 * 归订单域管，不进审核视角。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "管理端审核统计")
public class AdminAuditStatsVO {

    @Schema(description = "待审核数（status=0）")
    private int pending;

    @Schema(description = "在售数（status=1，即「已通过」）")
    private int onSale;

    @Schema(description = "已驳回数（status=5）")
    private int rejected;

    @Schema(description = "已下架数（status=4）")
    private int offShelf;
}
