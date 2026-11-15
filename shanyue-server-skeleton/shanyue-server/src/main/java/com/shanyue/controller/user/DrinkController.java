package com.shanyue.controller.user;

import com.shanyue.result.Result;
import com.shanyue.service.DrinkService;
import com.shanyue.vo.DrinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【C 端：饮品菜单】（山月带月售版）
 * 命名口径：URL 用 /user/drink、类名 DrinkController（山月 v1.2 起全链统一 Drink 命名）；
 * bean 名显式指定 userDrinkController，避免与管理端 DrinkController 同名冲突。
 * └ GET /user/drink/list?categoryId=xx  按分类查询启售饮品（含规格树 + 月售）
 *
 * 【本类知识点：为什么这个 list 没有像课程那样加 Redis 缓存？（对照学习）】
 * 课程方案：C 端菜单接口先查 redis 缓存（key 为 "drink_分类id"），命中直接返回，
 * 未命中查库再写缓存；管理端每次增删改都 clearCache（见管理端 DrinkController）。
 * 山月的取舍：getList 返回的 monthSales 是【实时统计】（order_detail 聚合 SQL），
 * 缓存后月售会"冻结"在第一次缓存的时刻，管理端清除缓存前一直不准 ——
 * 教学阶段宁可每次多一条聚合 SQL，也要保证月售数字可信。
 * 若要启用缓存：把 monthSales 改成"订单完成时 +1 的冗余字段"（课程方案 A），
 * 然后放开下面的注释即可（key 前缀用山月口径 drink_，与管理端 DRINK_CACHE_PREFIX 对齐）
 * —— 两种方案的取舍见 DrinkServiceImpl 类注释。
 */
@RestController("userDrinkController")
@RequestMapping("/user/drink")
@Tag(name = "C端-饮品接口")
@Slf4j
public class DrinkController {

    @Autowired
    private DrinkService drinkService;

    /**
     * C 端：按分类查询启售饮品列表
     *
     * @param categoryId 分类 id（点单页左侧分类栏点击切换）
     * @return DrinkVO 列表（含 specGroups 规格树 + monthSales 月售）
     */
    @GetMapping("/list")
    @Operation(summary = "C端-按分类查询饮品列表")
    public Result<List<DrinkVO>> list(Long categoryId) {
        log.info("C 端查询饮品列表，categoryId={}", categoryId);
        // Service 内：status=1（只出起售的）→ 查规格树 → 一次聚合 SQL 补月售
        List<DrinkVO> drinkVOList = drinkService.getList(categoryId);
        return Result.success(drinkVOList);
    }

    /* —— 启用缓存后的参考写法（教学备查，需要先解决 monthSales 实时性问题） ——
    @Autowired
    private RedisTemplate redisTemplate;

    public Result<List<DrinkVO>> listWithCache(Long categoryId) {
        String key = "drink_" + categoryId;   // 与管理端 DRINK_CACHE_PREFIX 对齐
        List<DrinkVO> cached = (List<DrinkVO>) redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isEmpty()) {
            return Result.success(cached);        // 命中缓存直接返回
        }
        List<DrinkVO> drinkVOList = drinkService.getList(categoryId);
        redisTemplate.opsForValue().set(key, drinkVOList); // 查库后写缓存
        return Result.success(drinkVOList);
    }
    */
}
