package com.shanyue.service;

import com.shanyue.dto.DrinkDTO;
import com.shanyue.dto.DrinkPageQueryDTO;
import com.shanyue.entity.Drink;
import com.shanyue.result.PageResult;
import com.shanyue.vo.DrinkVO;

import java.util.List;

/**
 * 饮品（菜品）服务接口 —— 本工程的核心范本类
 * 与课程的差异：口味(drink_flavor)升级为规格树(spec_group → spec_option)，方法名带 WithSpec。
 */
public interface DrinkService {

    /**
     * 新增饮品（含规格树）：插 drink → 插 spec_group → 插 spec_option（一个事务）
     *
     * @param drinkDTO 饮品 + specGroups（每组含 options）
     */
    void saveWithSpec(DrinkDTO drinkDTO);

    /**
     * 更新饮品（含规格树）：先删旧规格树再重插（"先删后插"策略）
     *
     * @param drinkDTO 饮品 + specGroups
     */
    void updateWithSpec(DrinkDTO drinkDTO);

    /**
     * 批量删除饮品（校验：起售中不能删、被套餐引用不能删；连规格树一起删）
     *
     * @param ids 饮品 id 集合
     */
    void delete(List<Long> ids);

    /**
     * 根据 id 查询饮品详情（管理端编辑页回显：含规格树）
     *
     * @param id 饮品 id
     * @return DrinkVO（specGroups 树）
     */
    DrinkVO getByIdWithSpec(Long id);

    /**
     * 分页查询饮品（管理端列表，联表带 categoryName）
     *
     * @param drinkPageQueryDTO page/pageSize/name/categoryId/status
     * @return 分页结果
     */
    PageResult pageQuery(DrinkPageQueryDTO drinkPageQueryDTO);

    /**
     * 管理端：根据分类 id 查询启售饮品列表（下拉选择）
     *
     * @param categoryId 分类 id
     * @return 饮品列表
     */
    List<Drink> list(Long categoryId);

    /**
     * C 端：按分类查询启售饮品（DrinkVO 含规格树 + 月售 monthSales）
     *
     * @param categoryId 分类 id
     * @return DrinkVO 列表
     */
    List<DrinkVO> getList(Long categoryId);

    /**
     * 起售/停售饮品
     *
     * @param status 0 停售 1 起售
     * @param id     饮品 id
     */
    void startOrStop(Integer status, Long id);
}
