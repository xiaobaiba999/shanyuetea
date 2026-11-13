package com.shanyue.mapper;

import com.shanyue.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：订单明细 Mapper——接口零 SQL，全部语句在 XML】
 * 订单明细 = 下单瞬间购物车行的"快照"（名称/单价/规格快照都冗余存下来，
 * 即使之后饮品改价/改规格，历史订单依然能还原当时的购买信息——电商通用做法）。
 * v2.0 新增 spec_selected 快照列：记录每行的规格选项与加价，供"再来一单"原样复刻。
 * 2 个方法的 SQL 都在 src/main/resources/mapper/OrderDetailMapper.xml：
 *   - insertBatch：<foreach> 批量插，submit 一次 insert N 行，比循环单插效率高；
 *   - getByOrderId：按订单 id 查明细列表。
 */
@Mapper
public interface OrderDetailMapper {

    /**
     * 批量插入订单明细（XML <foreach>；submit 第 5 步）
     *
     * @param orderDetailList 明细集合（orderId 已由 Service 填好——依赖 orders 插入后的自增回填）
     */
    void insertBatch(@Param("orderDetailList") List<OrderDetail> orderDetailList);

    /**
     * 根据订单 id 查询明细列表（订单详情页 / 历史订单展开）
     *
     * @param orderId 订单 id
     * @return 明细列表
     */
    List<OrderDetail> getByOrderId(Long orderId);
}
