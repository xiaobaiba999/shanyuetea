package com.shanyue.controller.user;

import com.shanyue.result.Result;
import com.shanyue.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【C 端：店铺状态】（对照课程 user/ShopController）
 * └ GET /user/shop/status  查询营业状态（1 营业中 0 打烊）
 *
 * 【本类知识点：为什么这个接口免登录？】
 * 小程序首页一进来就要显示"营业中/打烊"，此时用户可能还没登录 ——
 * WebMvcConfiguration 对 /user/shop/status 放行（excludePathPatterns），
 * 拦截器不校验 token。放行清单要慎用：只放"公开数据"，别的都要过安检门。
 * 与管理端 SET 状态共用 ShopService —— key 与默认值只维护一份。
 */
@RestController("userShopController")
@RequestMapping("/user/shop")
@Tag(name = "C端-店铺接口")
@Slf4j
public class ShopController {

    @Autowired
    private ShopService shopService;

    /**
     * 查询店铺营业状态（免登录接口）
     */
    @GetMapping("/status")
    @Operation(summary = "C端-查询店铺营业状态")
    public Result<Integer> getStatus() {
        Integer status = shopService.getStatus();
        log.info("查询店铺营业状态：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
