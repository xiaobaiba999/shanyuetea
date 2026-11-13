package com.shanyue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shanyue.constant.MessageConstant;
import com.shanyue.constant.StatusConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.dto.CategoryPageQueryDTO;
import com.shanyue.entity.Category;
import com.shanyue.exception.DeletionNotAllowedException;
import com.shanyue.mapper.CategoryMapper;
import com.shanyue.mapper.DrinkMapper;
import com.shanyue.mapper.SetmealMapper;
import com.shanyue.result.PageResult;
import com.shanyue.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 【本类知识点：分类服务实现（完整实现，对照课程 CategoryServiceImpl）】
 * 亮点：删除分类的"关联校验"（课程同款面试高频）——
 *   分类下还有饮品 → 抛 DeletionNotAllowedException(CATEGORY_BE_RELATED_BY_DRINK)；
 *   分类下还有套餐 → 抛 DeletionNotAllowedException(CATEGORY_BE_RELATED_BY_SETMEAL)。
 * 注意：课程原版抛的是 RuntimeException（走不到精确的异常分支），山月改抛语义更准的
 * DeletionNotAllowedException，由全局异常处理器统一转成 Result.error 返回前端。
 */
@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private DrinkMapper drinkMapper;

    /**
     * 分页查询分类
     */
    @Override
    public PageResult page(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageHelper.startPage(categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());

        Page<Category> page = categoryMapper.page(categoryPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据类型查询分类
     */
    @Override
    public List<Category> getByType(Integer type) {
        return categoryMapper.getByType(type);
    }

    /**
     * 更新分类
     */
    @Override
    public void update(Category category) {
        // update_time/update_user 交给 AutoFillAspect 填充（BaseContext 取当前登录人）
        categoryMapper.update(category);
    }

    /**
     * 启用/禁用分类
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Category category = Category.builder().id(id).status(status).build();
        categoryMapper.update(category);
    }

    /**
     * 新增分类（默认启用；name 有唯一索引，重名会触发全局异常处理器的 Duplicate entry 分支）
     */
    @Override
    public void save(Category category) {
        category.setStatus(StatusConstant.ENABLE);
        categoryMapper.insert(category);
    }

    /**
     * 删除分类（先校验关联，再删除）
     */
    @Override
    public void deleteById(Long id) {
        // 校验 1：分类下是否还有饮品
        int drinkCount = drinkMapper.countDrink(id);
        log.info("删除分类前校验，分类id：{}，关联饮品数：{}", id, drinkCount);
        if (drinkCount > 0) {
            // 当前分类关联了饮品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DRINK);
        }

        // 校验 2：分类下是否还有套餐
        int setmealCount = setmealMapper.countSetmeal(id);
        log.info("删除分类前校验，分类id：{}，关联套餐数：{}", id, setmealCount);
        if (setmealCount > 0) {
            // 当前分类关联了套餐，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        }

        categoryMapper.deleteById(id);
    }

    /**
     * C 端：查询启用状态的分类列表
     */
    @Override
    public List<Category> getByCategoryType(Category category) {
        // Controller 已把 type + status(ENABLE) 装进参数，XML 里按条件过滤
        return categoryMapper.getByCategoryType(category);
    }
}
