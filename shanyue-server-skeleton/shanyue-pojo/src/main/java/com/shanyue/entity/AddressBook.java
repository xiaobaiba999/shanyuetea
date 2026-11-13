package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地址簿实体（C 端收货地址）。对应表 address_book。
 * 外送订单下单时会把 consignee/phone/detail 快照进 orders 表（地址以后改了也不影响历史订单）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressBook implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 用户 id */
    private Long userId;

    /** 收货人 */
    private String consignee;

    /** 手机号 */
    private String phone;

    /** 性别 0 女 1 男 */
    private String sex;

    /** 省级区划编号 */
    private String provinceCode;

    /** 省级名称 */
    private String provinceName;

    /** 市级区划编号 */
    private String cityCode;

    /** 市级名称 */
    private String cityName;

    /** 区级区划编号 */
    private String districtCode;

    /** 区级名称 */
    private String districtName;

    /** 详细地址 */
    private String detail;

    /** 标签（家/公司/学校） */
    private String label;

    /** 是否默认 0 否 1 是 */
    private Integer isDefault;
}
