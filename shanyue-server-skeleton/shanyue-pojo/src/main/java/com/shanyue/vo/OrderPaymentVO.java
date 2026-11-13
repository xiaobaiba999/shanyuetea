package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付参数 VO（苍穹原版保留）：真实微信支付场景下，这些字段会被下发到
 * 小程序 wx.requestPayment() 发起支付；山月骨架做本地模拟，字段填占位值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentVO implements Serializable {

    /** 随机字符串 */
    private String nonceStr;

    /** 签名 */
    private String paySign;

    /** 时间戳 */
    private String timeStamp;

    /** 签名算法 */
    private String signType;

    /** 统一下单接口返回的 prepay_id 参数值（注意：微信返回字段叫 package，与 Java 关键字冲突改为 packageStr） */
    private String packageStr;
}
