package com.shanyue.mapper;

import com.shanyue.entity.SetmealDrink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：套餐-饮品关联表 Mapper（多对多中间表）——接口零 SQL，全部语句在 XML】
 * 核心用途一：删除饮品前校验"是否被套餐引用"（getSetmealIdsByDrinkIds，XML <foreach>）。
 * 核心用途二：套餐管理端维护关联关系（insertBatch/deleteBatch —— TODO 自己补在 XML）。
 * 已写好的 3 条（getSetmealIdsByDrinkIds/getBySetmealId/deleteBySetmealId）
 * 与待补的 2 条（insertBatch/deleteBatch）都在 src/main/resources/mapper/SetmealDrinkMapper.xml。
 */
@Mapper
public interface SetmealDrinkMapper {

    /**
     * 根据饮品 id 集合查询关联的套餐 id 集合（XML <foreach> 拼 in）
     * （课程原版这里写的是 select * 导致映射错列，山月修正为显式取 setmeal_id 并去重）
     *
     * @param drinkIds 饮品 id 集合
     * @return 关联到的套餐 id 集合
     */
    List<Long> getSetmealIdsByDrinkIds(@Param("drinkIds") List<Long> drinkIds);

    /**
     * 根据套餐 id 查询关联的饮品记录（套餐编辑页回显）
     *
     * @param setmealId 套餐 id
     * @return 套餐-饮品关联列表
     */
    List<SetmealDrink> getBySetmealId(Long setmealId);

    /**
     * 根据套餐 id 删除关联关系（套餐更新"先删后插"的删）
     *
     * @param setmealId 套餐 id
     */
    void deleteBySetmealId(Long setmealId);

    /**
     * TODO 管理端：批量新增套餐-饮品关联（XML 里补 insertBatch：<foreach> 拼 values，参照课程）
     *
     * @param setmealDrinkes 关联记录集合（setmealId 已由 Service 填好）
     */
    void insertBatch(@Param("setmealDrinkes") List<SetmealDrink> setmealDrinkes);

    /**
     * TODO 管理端：批量删除套餐-饮品关联（XML 里补 deleteBatch：delete from setmeal_drink where setmeal_id in (...)）
     *
     * @param setmealIds 套餐 id 集合
     */
    void deleteBatch(@Param("ids") List<Long> setmealIds);
}
