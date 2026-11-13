package com.shanyue.vo;

import com.shanyue.entity.DishFlavor;
import com.shanyue.vo.SpecGroupVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 饮品 VO：管理端"修改饮品回显"与 C 端点单页列表都在用。
 * 【山月 v2.0】
 *   - specGroups：结构化规格组（杯型/温度/糖度...），C 端规格弹窗的数据源；
 *   - monthSales：近 30 天月售 —— 不建数据库字段！由 Service 实时 SQL 统计（order_detail
 *     JOIN orders(status=5) 近 30 天 SUM(number) GROUP BY dish_id），
 *     好处是永远不会统计错、不需要维护冗余字段；代价是每次查询多一次聚合 SQL，
 *     高并发场景可改成"订单完成时 +1 的冗余计数/定时任务"（两种方案取舍见 DishServiceImpl 注释）。
 * flavors 字段保留以对照课程代码（山月不填充）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 饮品名称 */
    private String name;

    /** 饮品分类 id */
    private Long categoryId;

    /** 基础价格 */
    private BigDecimal price;

    /** 图片 */
    private String image;

    /** 描述信息 */
    private String description;

    /** 0 停售 1 起售 */
    private Integer status;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 分类名称（分页查询联表带出） */
    private String categoryName;

    /** 口味（课程同款字段保留，山月不填充，仅供对照） */
    private List<DishFlavor> flavors = new ArrayList<>();

    /** 规格组（山月 v2.0），每组含 options 选项 */
    private List<SpecGroupVO> specGroups = new ArrayList<>();

    /** 近 30 天月售（山月 v2.0，实时统计，非数据库字段） */
    private Integer monthSales;
}
