package com.shanyue.service;

import com.shanyue.dto.SetmealDTO;
import com.shanyue.dto.SetmealPageQueryDTO;
import com.shanyue.entity.Setmeal;
import com.shanyue.result.PageResult;
import com.shanyue.vo.DrinkItemVO;
import com.shanyue.vo.SetmealVO;

import java.util.List;

/**
 * 套餐服务接口（方法签名与课程 1:1；管理端实现留 TODO，C 端两个查询已实现）
 */
public interface SetmealService {

    /**
     * TODO 管理端：分页查询套餐
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * TODO 管理端：新增套餐（含套餐-饮品关联先删后插）
     */
    void save(SetmealVO setmealVO);

    /**
     * TODO 管理端：批量删除套餐
     */
    void deleteBatch(List<Long> ids);

    /**
     * TODO 管理端：根据 id 查询套餐（含套餐饮品明细）
     */
    SetmealVO getById(Long id);

    /**
     * TODO 管理端：修改套餐
     */
    void update(SetmealDTO setmealDTO);

    /**
     * TODO 管理端：起售/停售套餐（起售前校验套餐内饮品全部启售）
     */
    void startOrStop(Integer status, Long id);

    /**
     * C 端：按分类查询起售套餐列表（已实现）
     */
    List<Setmeal> getByCategoryId(Setmeal setmeal);

    /**
     * C 端：查询套餐内饮品明细（已实现）
     */
    List<DrinkItemVO> getDrinkItemById(Long id);
}
