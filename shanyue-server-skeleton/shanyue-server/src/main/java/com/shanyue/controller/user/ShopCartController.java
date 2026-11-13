package com.shanyue.controller.user;

import com.shanyue.dto.ShoppingCartDTO;
import com.shanyue.entity.ShoppingCart;
import com.shanyue.result.Result;
import com.shanyue.service.ShoppingCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【C 端：购物车】（对照课程 ShopCartController，4 个接口）
 * ┌ POST   /user/shoppingCart/add    加入购物车（同款同规格只加数量）
 * ├ POST   /user/shoppingCart/sub    减一个（减到 0 删行；body 里要带 specSelected 定位到"哪一行"）
 * ├ GET    /user/shoppingCart/list   查询购物车列表（角标数字与页面列表）
 * └ DELETE /user/shoppingCart/clean  清空购物车
 *
 * 【本类知识点：sub 为什么要带规格快照？】
 * 购物车按"商品+规格"分行（大杯拿铁与中杯拿铁是两行）——只传 drinkId 服务端不知道
 * 该减哪一行，所以 sub 的 body 必须带上加购时的 specSelected 快照原样回传。
 * 注意：add 与 sub 都是 POST + JSON body（课程同款），不要想当然用 DELETE。
 * userId 全部来自 ThreadLocal（拦截器），接口参数里根本没有 userId —— 防越权。
 */
@RestController
@RequestMapping("/user/shoppingCart")
@Tag(name = "C端-购物车接口")
@Slf4j
public class ShopCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 加入购物车（先查后改：命中同行 number+1，否则新增一行）
     */
    @PostMapping("/add")
    @Operation(summary = "C端-加入购物车")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("加入购物车：{}", shoppingCartDTO);
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查询购物车列表
     */
    @GetMapping("/list")
    @Operation(summary = "C端-查询购物车列表")
    public Result<List<ShoppingCart>> list() {
        log.info("查询购物车列表");
        List<ShoppingCart> list = shoppingCartService.showShoppingCart();
        return Result.success(list);
    }

    /**
     * 清空购物车
     */
    @DeleteMapping("/clean")
    @Operation(summary = "C端-清空购物车")
    public Result clean() {
        log.info("清空购物车");
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }

    /**
     * 减少购物车中一个商品（数量 -1；减到 0 删整行）
     */
    @PostMapping("/sub")
    @Operation(summary = "C端-减少购物车中一个商品")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("减少购物车商品：{}", shoppingCartDTO);
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
}
