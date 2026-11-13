package com.shanyue.controller.admin;

import com.shanyue.result.Result;
import com.shanyue.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【管理端：店铺管理】（对照课程 ShopController，2 个接口）
 * ┌ PUT /admin/shop/{status}  设置营业状态（1 营业中 0 打烊）
 * └ GET /admin/shop/status   查询营业状态
 *
 * 【本类知识点：与课程的差异 —— 通过 ShopService 读写而非直连 RedisTemplate】
 * 课程在 Controller 里直接注入 RedisTemplate 读写 SHOP_STATUS；山月把 get/set
 * 收进 ShopService（key 只在一处出现、C 端复用同一方法、默认值兜底集中管理）——
 * 这是"业务语义收口到 Service"的小重构，Controller 依旧只做 log + 调用 + 包装。
 *
 * 【状态值语义】1 营业中 / 0 打烊。打烊后 C 端下单会被前端提示拦截
 * （MessageConstant.SHOP_CLOSE 预留给下单入口校验）。
 */
@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Tag(name = "店铺管理")
@Slf4j
public class ShopController {

    @Autowired
    private ShopService shopService;

    /**
     * 设置店铺营业状态
     */
    @PutMapping("/{status}")
    @Operation(summary = "设置店铺营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置店铺营业状态：{}", status == 1 ? "营业中" : "打烊中");
        shopService.setStatus(status);
        return Result.success();
    }

    /**
     * 查询店铺营业状态
     */
    @GetMapping("/status")
    @Operation(summary = "查询店铺营业状态")
    public Result<Integer> getStatus() {
        Integer status = shopService.getStatus();
        log.info("查询店铺营业状态：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
