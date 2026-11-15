package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 饮品销量统计行（山月 v2.0，内部辅助 VO）。
 * OrdersMapper.getMonthSales 的查询结果：
 *   SELECT od.drink_id AS drinkId, SUM(od.number) AS salesNum
 *   FROM order_detail od JOIN orders o ON od.order_id = o.id ...
 * Service 拿到列表后转成 Map<饮品id, 销量> 再回填 DrinkVO.monthSales。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrinkSalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 饮品 id（SQL 别名必须叫 drinkId，配合 map-underscore-to-camel-case） */
    private Long drinkId;

    /** 近 30 天销量（SUM 结果） */
    private Integer salesNum;
}
