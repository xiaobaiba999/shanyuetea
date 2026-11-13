package com.shanyue.mapper;

import com.shanyue.entity.SpecOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：规格选项 Mapper（山月 v2.0 新表 spec_option）——接口零 SQL，全部语句在 XML】
 * 1. price_delta：加价金额。最终售价 = drink.price + Σ(所选项 price_delta)，
 *    加价只信数据库，不信前端传的金额（购物车/下单时服务端重算）。
 * 2. 5 个方法的 SQL 全部在 src/main/resources/mapper/SpecOptionMapper.xml：
 *    简单查询（getByGroupId/getByDrinkId/deleteByDrinkId）和批量语句
 *    （insertBatch/getByIds 的 <foreach>）统一放 XML，接口里只留方法声明。
 * 3. getByIds 的 @Param("ids")：XML <foreach collection="ids"> 按名取参。
 */
@Mapper
public interface SpecOptionMapper {

    /**
     * 批量新增规格选项（XML <foreach>）
     *
     * @param options 选项集合（groupId 已由 Service 填好）
     */
    void insertBatch(@Param("options") List<SpecOption> options);

    /**
     * 查询某规格组下的全部选项（树的第二层）
     *
     * @param groupId 规格组 id
     * @return 选项列表（按 sort 升序）
     */
    List<SpecOption> getByGroupId(Long groupId);

    /**
     * 查询某饮品的全部选项（联规格组表，按组内 sort 排好序）
     * 购物车算价用它：把前端选中的 optionId 与"该饮品的合法选项"求交集，防止越权传别人家饮品的选项 id。
     *
     * @param drinkId 饮品 id
     * @return 选项列表
     */
    List<SpecOption> getByDrinkId(Long drinkId);

    /**
     * 根据 id 集合批量查询选项（XML <foreach>；购物车按 optionId 集合取加价）
     *
     * @param ids 选项 id 集合
     * @return 选项列表
     */
    List<SpecOption> getByIds(@Param("ids") List<Long> ids);

    /**
     * 删除某饮品的全部选项（MySQL 多表删除语法：delete 别名 from join where，见 XML）
     * 更新饮品时与 deleteByDrinkId（规格组）配套，实现规格树整体"先删后插"。
     *
     * @param drinkId 饮品 id
     */
    void deleteByDrinkId(Long drinkId);
}
