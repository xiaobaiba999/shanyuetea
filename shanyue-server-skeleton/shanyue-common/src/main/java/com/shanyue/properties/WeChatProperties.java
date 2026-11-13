package com.shanyue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 【本类知识点：微信小程序配置占位】
 * C 端微信登录（UserServiceImpl）拿这里的 appid/secret 去调 jscode2session
 * 用 code 换 openid；把 application-dev.yml 里的占位替换为真实 appid/secret 即可
 * （appid/secret 属于敏感信息，不要提交到公开仓库！）。
 * 支付相关字段（商户号/证书等）课程进阶内容用到，先注释占位，保持配置结构完整。
 */
@Component
@ConfigurationProperties(prefix = "shanyue.wechat")
@Data
public class WeChatProperties {

    /** 小程序的 appid */
    private String appid;

    /** 小程序的秘钥 */
    private String secret;

    /* ---- 以下为微信支付相关（接入真实支付时再启用） ---- */
    // private String mchid;                 // 商户号
    // private String mchSerialNo;           // 商户API证书的证书序列号
    // private String privateKeyFilePath;    // 商户私钥文件
    // private String apiV3Key;              // 证书解密的密钥
    // private String weChatPayCertFilePath; // 平台证书
    // private String notifyUrl;             // 支付成功的回调地址
    // private String refundNotifyUrl;       // 退款成功的回调地址
}
