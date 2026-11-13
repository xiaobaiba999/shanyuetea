package com.shanyue.controller.user;

import com.shanyue.constant.StatusConstant;
import com.shanyue.entity.Setmeal;
import com.shanyue.result.Result;
import com.shanyue.service.SetmealService;
import com.shanyue.vo.DrinkItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【C 端：套餐】（对照课程 user/SetmealController，2 个接口）
 * ┌ GET /user/setmeal/list?categoryId=  按分类查询起售套餐
 * └ GET /user/setmeal/drink/{id}        查询套餐内包含的饮品明细（弹窗展示）
 *
 * 【本类知识点：C 端列表查询固定"status=ENABLE"过滤】
 * 顾客只该看到起售中的套餐 —— 组参数时把 status 固定装进 Setmeal（同 C 端分类的套路），
 * Mapper 动态 where 按它过滤；停售套餐在 C 端"不存在"。
 * 课程还在这两个接口加了 @Cacheable("setmealCache") 注解缓存（Spring Cache，
 * 管理端写操作上 @CacheEvict 自动清）——骨架为保持"月售/状态实时性"暂未启用，
 * 进阶动手时补注解即可，注意 management 端 save/update/deleteBatch 也别忘了 @CacheEvict。
 */
@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Tag(name = "C端-套餐接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * C 端：按分类查询起售套餐
     */
    @GetMapping("/list")
    @Operation(summary = "C端-按分类查询套餐列表")
    public Result<List<Setmeal>> list(Long categoryId) {
        log.info("C 端查询套餐列表，categoryId={}", categoryId);
        // 固定拼 status=ENABLE：顾客端只见起售套餐
        Setmeal setmeal = new Setmeal();
        setmeal.setCategoryId(categoryId);
        setmeal.setStatus(StatusConstant.ENABLE);
        List<Setmeal> setmealList = setmealService.getByCategoryId(setmeal);
        return Result.success(setmealList);
    }

    /**
     * C 端：查询套餐内饮品明细（点开套餐弹窗展示"里面有什么"）
     */
    @GetMapping("/drink/{id}")
    @Operation(summary = "C端-根据套餐id查询包含的饮品")
    public Result<List<DrinkItemVO>> drinkList(@PathVariable Long id) {
        log.info("C 端查询套餐内饮品，setmealId={}", id);
        List<DrinkItemVO> drinkItemVOList = setmealService.getDrinkItemById(id);
        return Result.success(drinkItemVOList);
    }
}
