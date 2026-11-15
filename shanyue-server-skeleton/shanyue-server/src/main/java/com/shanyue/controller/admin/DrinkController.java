package com.shanyue.controller.admin;

import com.shanyue.dto.DrinkDTO;
import com.shanyue.dto.DrinkPageQueryDTO;
import com.shanyue.entity.Drink;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import com.shanyue.service.DrinkService;
import com.shanyue.vo.DrinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.Set;

/**
 * 【管理端：饮品管理】（接口语义与课程 1:1，规格树版本）
 * 命名口径：表名、URL、类名全链统一用 Drink（山月 v1.2 起）。
 * ┌ POST   /admin/drink                新增饮品（含规格树 specGroups → DrinkService.saveWithSpec）
 * ├ GET    /admin/drink/page           分页查询（name/categoryId/status 过滤，联表带 categoryName）
 * ├ DELETE /admin/drink?ids=1,2        批量删除（起售中/被套餐引用会被 Service 拒绝）
 * ├ GET    /admin/drink/{id}           按 id 查询（编辑回显，含规格树 → getByIdWithSpec）
 * ├ PUT    /admin/drink                修改饮品（规格树先删后插 → updateWithSpec）
 * ├ GET    /admin/drink/list           按分类查启售饮品（套餐编辑的下拉选择）
 * └ POST   /admin/drink/status/{status}?id=  起售/停售
 *
 * 【本类知识点：缓存一致性 —— 管理端改了数据，必须"顺手"清 C 端缓存】
 * C 端菜单接口会用 Redis 缓存 "drink_分类id" 的查询结果（课程方案）。管理端一旦
 * 新增/删除/改状态，缓存里的旧菜单就会与数据库不一致 —— 所以这 4 个写操作之后
 * 都要 clearCache 清掉相关 key。课程用 keys 通配删除（演示友好）；
 * 生产环境 keys 是 O(N) 阻塞命令，应换 SCAN 或精确 key 删除（面试讲点）。
 * （山月 C 端 list 因含实时月售统计暂未启用缓存，但清缓存逻辑保留：启用缓存后无需再改。）
 */
@RestController
@RequestMapping("/admin/drink")
@Tag(name = "饮品接口")
@Slf4j
public class DrinkController {

    @Autowired
    private DrinkService drinkService;
    @Autowired
    private RedisTemplate redisTemplate;

    /** C 端菜单缓存 key 前缀（山月口径：drink_ + 分类id） */
    private static final String DRINK_CACHE_PREFIX = "drink_";

    /**
     * 新增饮品（含规格树）
     */
    @PostMapping
    @Operation(summary = "新增饮品")
    public Result save(@RequestBody DrinkDTO drinkDTO) {
        log.info("新增饮品：{}", drinkDTO);
        drinkService.saveWithSpec(drinkDTO);

        // 清除该分类的 C 端菜单缓存（新饮品要能被看见）
        clearCache(DRINK_CACHE_PREFIX + drinkDTO.getCategoryId());
        return Result.success();
    }

    /**
     * 分页查询饮品
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询饮品")
    public Result<PageResult> page(DrinkPageQueryDTO drinkPageQueryDTO) {
        log.info("分页查询饮品：{}", drinkPageQueryDTO);
        PageResult pageResult = drinkService.pageQuery(drinkPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除饮品（ids 走 query 参数，逗号分隔自动转 List<Long>）
     */
    @DeleteMapping
    @Operation(summary = "删除饮品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("删除饮品：{}", ids);
        drinkService.delete(ids);

        // 删除/改价影响所有分类的菜单缓存 → 通配清空
        clearCache(DRINK_CACHE_PREFIX + "*");
        return Result.success();
    }

    /**
     * 根据 id 查询饮品详情（编辑页回显：含规格树）
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询饮品详情")
    public Result<DrinkVO> getById(@PathVariable Long id) {
        log.info("根据id查询饮品详情：{}", id);
        DrinkVO drinkVO = drinkService.getByIdWithSpec(id);
        return Result.success(drinkVO);
    }

    /**
     * 修改饮品（含规格树，先删后插）
     */
    @PutMapping
    @Operation(summary = "修改饮品")
    public Result update(@RequestBody DrinkDTO drinkDTO) {
        log.info("修改饮品：{}", drinkDTO);
        drinkService.updateWithSpec(drinkDTO);

        // 改名/改价/改规格都会让 C 端旧缓存失真 → 通配清空（课程同款从简）
        clearCache(DRINK_CACHE_PREFIX + "*");
        return Result.success();
    }

    /**
     * 根据分类 id 查询启售饮品列表（套餐编辑下拉框）
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询饮品列表")
    public Result<List<Drink>> list(Long categoryId) {
        log.info("根据分类id查询饮品列表：{}", categoryId);
        List<Drink> drinkList = drinkService.list(categoryId);
        return Result.success(drinkList);
    }

    /**
     * 起售/停售饮品
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "停售/起售饮品")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("停售/起售饮品：status={}，id={}", status, id);
        drinkService.startOrStop(status, id);

        clearCache(DRINK_CACHE_PREFIX + "*");
        return Result.success();
    }

    /**
     * 抽离出清除缓存的方法（课程同款）：keys 通配匹配 → 批量删
     * 教学提示：keys 命令生产环境慎用（阻塞主线程），可改 SCAN 游标遍历。
     */
    private void clearCache(String pattern) {
        Set keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
