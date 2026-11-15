package com.shanyue.vo;

import com.shanyue.entity.OrderDetail;
import com.shanyue.entity.Orders;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 订单详情 VO：继承 Orders 实体（课程同款做法），追加明细列表。
 * 继承复用的代价：Orders 的常量与字段都会被序列化，注意不要在父类加敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO extends Orders implements Serializable {

    /** 订单饮品摘要（字符串形式，如"山月拿铁*1;桂花冰酿*2"，报表用） */
    private String orderDrinks;

    /** 订单明细列表（含 specSelected 规格快照） */
    private List<OrderDetail> orderDetailList;
}
