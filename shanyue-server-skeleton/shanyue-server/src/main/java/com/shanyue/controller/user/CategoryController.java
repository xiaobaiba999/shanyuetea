package com.shanyue.controller.user;

import com.shanyue.constant.StatusConstant;
import com.shanyue.entity.Category;
import com.shanyue.result.Result;
import com.shanyue.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【C 端：分类】（对照课程 user/CategoryController）
 * └ GET /user/category/list?type=  查询启用状态的分类列表（点单页左侧分类栏）
 *
 * 【本类知识点：C 端查询要"自带过滤条件"】
 * 管理端查分类要看到禁用的（管理视角）；C 端只能看到启用的（顾客视角）——
 * 所以这里固定拼上 status=ENABLE 再去查（复用同一个 Service 方法和同一个 Mapper）。
 * type 可传可不传：传 1 只查饮品分类，传 2 只查套餐分类，不传查全部启用分类。
 */
@RestController("userCategoryController")
@RequestMapping("/user/category")
@Tag(name = "C端-分类接口")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * C 端：查询启用状态的分类列表
     */
    @GetMapping("/list")
    @Operation(summary = "C端-查询分类列表")
    public Result<List<Category>> list(Integer type) {
        log.info("C 端查询分类列表，type={}", type);
        // 只查启用的分类：type 由前端传，status 固定 ENABLE（C 端永远不给看禁用分类）
        Category category = Category.builder()
                .type(type)
                .status(StatusConstant.ENABLE)
                .build();
        List<Category> categoryList = categoryService.getByCategoryType(category);
        return Result.success(categoryList);
    }
}
