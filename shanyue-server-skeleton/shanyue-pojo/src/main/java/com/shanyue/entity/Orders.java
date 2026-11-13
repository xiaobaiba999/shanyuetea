package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体。对应表 orders。
 * 【状态机（苍穹 6 状态，山月沿用）】
 *   1 待付款 → 2 待接单 → 3 已接单(制作中) → 4 派送中(外送)·待取餐(自取) → 5 已完成
 *                                  任意环节可 → 6 已取消（规则见各状态流转方法）
 * 【支付状态】0 未支付 1 已支付 2 退款
 * 【山月 v2.0 双模式】deliveryMode：1 门店自取 / 2 外送
 *   - 自取单：生成取餐号 pickupNumber（Redis 按天自增 A01/A02...），addressBookId 可空
 *   - 外送单：必须有 addressBookId，收货信息快照进本表，deliveryFee 固定 5 元
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orders implements Serializable {

    /** 订单状态 1待付款 2待接单 3已接单 4派送中(外送)·待取餐(自取) 5已完成 6已取消 */
    public static final Integer PENDING_PAYMENT = 1;
    public static final Integer TO_BE_CONFIRMED = 2;
    public static final Integer CONFIRMED = 3;
    public static final Integer DELIVERY_IN_PROGRESS = 4;
    public static final Integer COMPLETED = 5;
    public static final Integer CANCELLED = 6;

    /** 支付状态 0未支付 1已支付 2退款 */
    public static final Integer UN_PAID = 0;
    public static final Integer PAID = 1;
    public static final Integer REFUND = 2;

    /** 下单模式（山月 v2.0）：1 门店自取 2 外送 */
    public static final Integer DELIVERY_MODE_PICKUP = 1;
    public static final Integer DELIVERY_MODE_DELIVERY = 2;

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 订单号（时间戳生成） */
    private String number;

    /** 订单状态 1待付款 2待接单 3已接单 4派送中·待取餐 5已完成 6已取消 */
    private Integer status;

    /** 下单用户 id */
    private Long userId;

    /** 地址簿 id（外送必填，自取可空） */
    private Long addressBookId;

    /** 下单时间 */
    private LocalDateTime orderTime;

    /** 结账时间（支付成功时写入） */
    private LocalDateTime checkoutTime;

    /** 支付方式 1 微信，2 支付宝 */
    private Integer payMethod;

    /** 支付状态 0未支付 1已支付 2退款 */
    private Integer payStatus;

    /** 实收金额（含外送费） */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 用户名 */
    private String userName;

    /** 手机号（收货/联系手机快照） */
    private String phone;

    /** 地址（收货地址快照，自取为空或门店地址） */
    private String address;

    /** 收货人快照 */
    private String consignee;

    /** 订单取消原因 */
    private String cancelReason;

    /** 订单拒绝原因（管理端拒单） */
    private String rejectionReason;

    /** 订单取消时间 */
    private LocalDateTime cancelTime;

    /** 预计送达时间 */
    private LocalDateTime estimatedDeliveryTime;

    /** 配送状态 1 立即送出 0 选择具体时间 */
    private Integer deliveryStatus;

    /** 送达时间 */
    private LocalDateTime deliveryTime;

    /** 打包费 */
    private int packAmount;

    /** 餐具数量 */
    private int tablewareNumber;

    /** 餐具数量状态 1 按餐量提供 0 选择具体数量 */
    private Integer tablewareStatus;

    /* ------------------ 山月 v2.0 新增 ------------------ */

    /** 下单模式 1 门店自取 2 外送 */
    private Integer deliveryMode;

    /** 取餐号（自取单，如 A01；Redis 按天自增 key: shanyue:pickup:yyyy-MM-dd） */
    private String pickupNumber;

    /** 外送费（外送固定 5 元，自取 0） */
    private BigDecimal deliveryFee;
}
