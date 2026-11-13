package com.shanyue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端提交订单参数。
 * 【山月 v2.0】新增 deliveryMode（1 门店自取 / 2 外送，外送必须带有效 addressBookId）。
 * 注意：金额 amount 虽然保留字段，但服务端会按购物车+规格加价+外送费重新计算并覆盖，
 *      永远不要信任前端传来的金额（防篡改，见 OrderServiceImpl.submit 六步法）。
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OrdersSubmitDTO implements Serializable {

    /** 地址簿 id（外送必填；自取可空，若传了会快照手机号/收货人） */
    private Long addressBookId;

    /** 付款方式 1 微信 2 支付宝 */
    private int payMethod;

    /** 备注 */
    private String remark;

    /** 预计送达时间（外送场景） */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;

    /** 配送状态 1 立即送出 0 选择具体时间 */
    private Integer deliveryStatus;

    /** 餐具数量 */
    private Integer tablewareNumber;

    /** 餐具数量状态 1 按餐量提供 0 选择具体数量 */
    private Integer tablewareStatus;

    /** 打包费 */
    private Integer packAmount;

    /** 总金额（前端展示用，服务端会重算覆盖） */
    private BigDecimal amount;

    /* ------------------ 山月 v2.0 新增 ------------------ */

    /** 下单模式 1 门店自取 2 外送（不传默认按自取处理） */
    private Integer deliveryMode;
}
