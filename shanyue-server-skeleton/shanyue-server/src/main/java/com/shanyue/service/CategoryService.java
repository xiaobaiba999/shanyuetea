package com.shanyue.service;

import com.shanyue.dto.CategoryPageQueryDTO;
import com.shanyue.entity.Category;
import com.shanyue.result.PageResult;

import java.util.List;

/**
 * 分类服务接口（对照课程 CategoryService，方法签名 1:1）
 */
public interface CategoryService {

    /**
     * 分页查询分类（管理端）
     *
     * @param categoryPageQueryDTO page/pageSize/name/type
     * @return 分页结果
     */
    PageResult page(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 更新分类
     *
     * @param category 分类实体（含 id）
     */
    void update(Category category);

    /**
     * 启用/禁用分类（禁用后 C 端不可见）
     *
     * @param status 0 禁用 1 启用
     * @param id     分类 id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据类型查询分类（管理端下拉框）
     *
     * @param type 1 饮品分类 2 套餐分类
     * @return 分类列表
     */
    List<Category> getByType(Integer type);

    /**
     * 新增分类（默认启用）
     *
     * @param category 分类实体
     */
    void save(Category category);

    /**
     * 删除分类（被饮品/套餐引用时抛 DeletionNotAllowedException）
     *
     * @param id 分类 id
     */
    void deleteById(Long id);

    /**
     * C 端：查询启用状态的分类列表（小程序点餐页左侧分类栏）
     *
     * @param category type + status(ENABLE)
     * @return 分类列表
     */
    List<Category> getByCategoryType(Category category);
}
