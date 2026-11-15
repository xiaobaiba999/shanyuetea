package com.shanyue.controller.admin;

import com.shanyue.dto.CategoryPageQueryDTO;
import com.shanyue.entity.Category;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import com.shanyue.service.CategoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【管理端：分类管理】（对照课程 CategoryController，7 个接口；实现见 CategoryServiceImpl）
 * ┌ GET    /admin/category/page    分页查询（name 模糊 + type 精确）
 * ├ GET    /admin/category/list    按类型查询（管理端下拉框：type 1 饮品分类 2 套餐分类）
 * ├ PUT    /admin/category         更新分类
 * ├ POST   /admin/category/status/{status}?id=  启用/禁用（禁用后 C 端不可见）
 * ├ POST   /admin/category         新增分类（默认启用；name 唯一索引防重名）
 * └ DELETE /admin/category?id=     删除分类（被饮品/套餐引用时 Service 抛异常拒绝）
 *
 * 【本类知识点：Controller 的"薄"原则】
 * 本类每个方法都只有三步：log 参数 → 调 Service → Result.success 包装。
 * 业务规则（删除校验、状态默认值）全在 Service —— Controller 只做"参数进、结果出"的翻译，
 * 这样同一套业务能被管理端/C 端/定时任务复用（面试常问"为什么不全写在 Controller"）。
 * 注意 DELETE 的 id 是 query 参数（?id=1），不是路径参数 —— 前端调用时别拼错。
 */
@RestController("adminCategoryController")
@RequestMapping("/admin/category")
@Tag(name = "分类管理")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 分页查询分类
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询分类")
    public Result<PageResult> page(CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分页查询分类，参数：{}", categoryPageQueryDTO);
        PageResult pageResult = categoryService.page(categoryPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据类型查询分类（管理端下拉框）
     */
    @GetMapping("/list")
    @Operation(summary = "根据类型查询分类")
    public Result<List<Category>> getByType(Integer type) {
        log.info("根据类型查询分类，type={}", type);
        List<Category> categoryList = categoryService.getByType(type);
        return Result.success(categoryList);
    }

    /**
     * 更新分类
     */
    @PutMapping
    @Operation(summary = "更新分类")
    public Result update(@RequestBody Category category) {
        log.info("更新分类，参数：{}", category);
        categoryService.update(category);
        return Result.success();
    }

    /**
     * 启用/禁用分类（status 路径参数 + id query 参数）
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "停用/启用分类")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("停用/启用分类，id={}，status={}", id, status);
        categoryService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 新增分类（默认启用）
     */
    @PostMapping
    @Operation(summary = "新增分类")
    public Result save(@RequestBody Category category) {
        log.info("新增分类，参数：{}", category);
        categoryService.save(category);
        return Result.success();
    }

    /**
     * 删除分类（被饮品/套餐引用时抛 DeletionNotAllowedException → Result.error 提示）
     */
    @DeleteMapping
    @Operation(summary = "删除分类")
    public Result deleteById(Long id) {
        log.info("删除分类，id={}", id);
        categoryService.deleteById(id);
        return Result.success();
    }
}
