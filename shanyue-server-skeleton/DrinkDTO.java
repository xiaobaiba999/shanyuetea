package com.shanyue.dto;

import com.shanyue.entity.DrinkFlavor;
import com.shanyue.dto.SpecGroupDTO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 新增/修改饮品的参数模型（管理端）。
 * 【山月 v2.0】specGroups：结构化规格组，替代课程的 flavors 口味。
 *   保存策略为"整体覆盖"：先删该饮品的全部规格组/选项，再把提交内容整体插入（先删后插），
 *   这样前端无需做复杂的 diff 比较，逻辑最简单。
 * flavors 字段保留是为了对照课程代码；山月业务请只使用 specGroups。
 */
@Data
public class DrinkDTO implements Serializable {

    private Long id;

    /** 饮品名称 */
    private String name;

    /** 饮品分类 id */
    private Long categoryId;

    /** 饮品基础价格 */
    private BigDecimal price;

    /** 图片 URL（/admin/common/upload 返回） */
    private String image;

    /** 描述信息 */
    private String description;

    /** 0 停售 1 起售 */
    private Integer status;

    /** 口味（课程同款字段保留，山月不使用） */
    private List<DrinkFlavor> flavors = new ArrayList<>();

    /** 规格组（山月 v2.0）：杯型/温度/糖度...，每组下带 options 选项列表 */
    private List<SpecGroupDTO> specGroups = new ArrayList<>();
}
