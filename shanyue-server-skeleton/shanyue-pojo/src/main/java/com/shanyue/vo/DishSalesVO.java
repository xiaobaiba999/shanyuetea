package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 饮品销量统计行（山月 v2.0，内部辅助 VO）。
 * OrderDetailMapper.sumDishSalesSince 的查询结果：
 *   SELECT od.dish_id AS dishId, SUM(od.number) AS salesNum
 *   FROM order_detail od JOIN orders o ON od.order_id = o.id ...
 * Service 拿到列表后转成 Map<饮品id, 销量> 再回填 DishVO.monthSales。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishSalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 饮品 id（SQL 别名必须叫 dishId，配合 map-underscore-to-camel-case） */
    private Long dishId;

    /** 近 30 天销量（SUM 结果） */
    private Integer salesNum;
}
