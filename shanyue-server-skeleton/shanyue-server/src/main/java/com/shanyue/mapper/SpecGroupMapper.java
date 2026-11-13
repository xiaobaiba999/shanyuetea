package com.shanyue.mapper;

import com.shanyue.entity.SpecGroup;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 【本类知识点：规格组 Mapper（山月 v2.0 新表 spec_group，课程没有的表）】
 * 规格模型（替代课程的 drink_flavor 口味表）：
 *   drink(饮品) 1 → N spec_group(规格组：杯型/温度/糖度) 1 → N spec_option(选项：大杯/少糖，含加价)
 * 1. 3 个方法的 SQL 全部在 src/main/resources/mapper/SpecGroupMapper.xml。
 * 2. insert 在 XML 里带 useGeneratedKeys="true" keyProperty="id"：
 *    自增 id 回填 specGroup.id，DrinkServiceImpl.saveWithSpec 插完规格组立刻拿它去插选项
 *    （等价于注解版的 @Options(useGeneratedKeys = true, keyProperty = "id")）。
 * 3. 字段全部 snake_case：drink_id/sort → 实体 drinkId/sort（驼峰映射自动对应）。
 */
@Mapper
public interface SpecGroupMapper {

    /**
     * 新增规格组（XML：useGeneratedKeys 回填自增 id）
     *
     * @param specGroup 含 drinkId/name/sort
     */
    void insert(SpecGroup specGroup);

    /**
     * 查询某饮品的全部规格组（C 端规格弹窗树的第一层）
     *
     * @param drinkId 饮品 id
     * @return 规格组列表（按 sort 升序）
     */
    List<SpecGroup> getByDrinkId(Long drinkId);

    /**
     * 删除某饮品的全部规格组（更新饮品"先删后插"策略的一半；
     * 规格选项由 SpecOptionMapper.deleteByDrinkId 走联表一并删除）
     *
     * @param drinkId 饮品 id
     */
    void deleteByDrinkId(Long drinkId);
}
