package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提交订单返回 VO。
 * 【山月 v2.0】新增 pickupNumber / deliveryFee：
 *   - 自取单：pickupNumber 为取餐号（A01，Redis 按天自增），前端在"下单成功/取餐"页大字展示；
 *   - 外送单：pickupNumber 为 null，deliveryFee 为外送费（5 元），已计入 orderAmount。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmitVO implements Serializable {

    /** 订单 id */
    private Long id;

    /** 订单号 */
    private String orderNumber;

    /** 订单金额（服务端重算后的金额：商品小计 + 外送费） */
    private BigDecimal orderAmount;

    /** 下单时间 */
    private LocalDateTime orderTime;

    /* ------------------ 山月 v2.0 新增 ------------------ */

    /** 取餐号（自取单才有，如 A01） */
    private String pickupNumber;

    /** 外送费（外送单才有，5 元） */
    private BigDecimal deliveryFee;
}
