package com.shanyue.controller.admin;

import com.shanyue.dto.SetmealDTO;
import com.shanyue.dto.SetmealPageQueryDTO;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import com.shanyue.service.SetmealService;
import com.shanyue.vo.SetmealVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【管理端：套餐管理】（对照课程 SetmealController，6 个接口）
 * ┌ GET    /admin/setmeal/page      分页查询套餐
 * ├ POST   /admin/setmeal           新增套餐（SetmealVO 接收，含 setmealDrinkes 关联）
 * ├ DELETE /admin/setmeal?ids=1,2   批量删除
 * ├ GET    /admin/setmeal/{id}      按 id 查询（编辑回显，含套餐饮品明细）
 * ├ PUT    /admin/setmeal           修改套餐（SetmealDTO 接收）
 * └ POST   /admin/setmeal/status/{status}?id=  起售/停售（起售前校验套餐内饮品全部启售）
 *
 * 【本类知识点：新增/修改接收为什么一个用 VO 一个用 DTO？】（课程同款保留）
 *   新增前端提交 {套餐字段 + setmealDrinkes 列表} → 直接用 SetmealVO 承接；
 *   修改时课程把它单独抽成了 SetmealDTO（字段基本一样）——历史习惯，不必纠结，
 *   理解"VO 面向展示/VO 可作提交体"的课程约定即可。
 *
 * 【教学现状】Service 的 save/update 等管理端方法留 TODO（见 SetmealServiceImpl 的步骤提示），
 * 接口先行、返回 Result.success 前调用会 log.warn 提示 —— 动手补完 Service+Mapper SQL 即全通。
 */
@RestController("adminSetmealController")
@RequestMapping("/admin/setmeal")
@Tag(name = "套餐管理")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 分页查询套餐
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询套餐")
    public Result<PageResult> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐，参数：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 新增套餐（含套餐-饮品关联）
     */
    @PostMapping
    @Operation(summary = "新增套餐")
    public Result save(@RequestBody SetmealVO setmealVO) {
        log.info("新增套餐，参数：{}", setmealVO);
        setmealService.save(setmealVO);
        return Result.success();
    }

    /**
     * 批量删除套餐（ids query 参数；Service 校验"起售中不能删"）
     */
    @DeleteMapping
    @Operation(summary = "删除套餐")
    public Result deleteBatch(@RequestParam List<Long> ids) {
        log.info("删除套餐，参数：{}", ids);
        setmealService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据 id 查询套餐（编辑回显）
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询套餐")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        log.info("根据id查询套餐，参数：{}", id);
        SetmealVO setmealVO = setmealService.getById(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐（关联饮品整体先删后插）
     */
    @PutMapping
    @Operation(summary = "修改套餐")
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐，参数：{}", setmealDTO);
        setmealService.update(setmealDTO);
        return Result.success();
    }

    /**
     * 起售/停售套餐
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "起售/停售套餐")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("起售/停售套餐，参数：status={}，id={}", status, id);
        setmealService.startOrStop(status, id);
        return Result.success();
    }
}
