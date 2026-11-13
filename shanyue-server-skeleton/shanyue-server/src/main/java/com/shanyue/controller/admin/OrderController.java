package com.shanyue.controller.admin;

import com.shanyue.dto.OrdersCancelDTO;
import com.shanyue.dto.OrdersConfirmDTO;
import com.shanyue.dto.OrdersPageQueryDTO;
import com.shanyue.dto.OrdersRejectionDTO;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import com.shanyue.service.OrderService;
import com.shanyue.vo.OrderStatisticsVO;
import com.shanyue.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【管理端：订单管理】（对照课程 OrderController，8 个接口）
 * ┌ GET  /admin/order/conditionSearch   条件搜索（单号/手机号/状态/下单模式/时间段 + 分页）
 * ├ GET  /admin/order/statistics        状态统计（待接单/已接单/派送中·待取餐 三张卡片）
 * ├ GET  /admin/order/details/{id}      订单详情（明细 + 收货信息）
 * ├ PUT  /admin/order/confirm           接单（body: {id}，status 2→3）
 * ├ PUT  /admin/order/rejection         拒单（body: {id, rejectionReason}，status 2→6）
 * ├ PUT  /admin/order/cancel            取消订单（body: {id, cancelReason}，商家主动取消）
 * ├ PUT  /admin/order/delivery/{id}     派送订单（status 3→4）
 * └ PUT  /admin/order/complete/{id}     完成订单（status 4→5）
 *
 * 【本类知识点：状态流转接口的参数风格（照苍穹约定，前端联调别改）】
 *   confirm/rejection/cancel 用 PUT + JSON body（拒单要带原因，参数多）；
 *   delivery/complete 用 PUT /{id}（只带一个 id，路径参数更 REST）；
 *   status 数字只在 Service/数据库流转，Controller 不参与判断 —— 判断下沉 Service，
 *   保证"不管谁调（管理端页面/定时任务/小程序）规则都一样"。
 */
@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Slf4j
@Tag(name = "订单管理")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 订单条件搜索（管理端订单列表页）
     */
    @GetMapping("/conditionSearch")
    @Operation(summary = "订单条件搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("订单条件搜索：{}", ordersPageQueryDTO);
        PageResult pageResult = orderService.conditionSearch(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 各状态订单数量统计（列表页顶部三张卡片）
     */
    @GetMapping("/statistics")
    @Operation(summary = "订单状态统计")
    public Result<OrderStatisticsVO> statistics() {
        log.info("各状态订单数量统计");
        OrderStatisticsVO orderStatisticsVO = orderService.statistics();
        return Result.success(orderStatisticsVO);
    }

    /**
     * 根据订单 id 查询详情（管理端版：无需归属校验）
     */
    @GetMapping("/details/{id}")
    @Operation(summary = "根据订单id查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        log.info("根据订单id查询订单详情：{}", id);
        OrderVO orderVO = orderService.searchDetail(id);
        return Result.success(orderVO);
    }

    /**
     * 接单（status 2→3）
     */
    @PutMapping("/confirm")
    @Operation(summary = "接单")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单：{}", ordersConfirmDTO);
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 拒单（status 2→6，需填拒单原因；已支付单由 Service 模拟退款）
     */
    @PutMapping("/rejection")
    @Operation(summary = "拒单")
    public Result rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO) {
        log.info("拒单：{}", ordersRejectionDTO);
        orderService.reject(ordersRejectionDTO);
        return Result.success();
    }

    /**
     * 取消订单（商家主动取消，原因商家填写 → Service.cancelByAdmin）
     */
    @PutMapping("/cancel")
    @Operation(summary = "取消订单")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("取消订单：{}", ordersCancelDTO);
        orderService.cancelByAdmin(ordersCancelDTO);
        return Result.success();
    }

    /**
     * 派送订单（status 3→4；外送=开始配送，自取=出餐待取）
     */
    @PutMapping("/delivery/{id}")
    @Operation(summary = "派送订单")
    public Result delivery(@PathVariable Long id) {
        log.info("派送订单：{}", id);
        orderService.delivery(id);
        return Result.success();
    }

    /**
     * 完成订单（status 4→5）
     */
    @PutMapping("/complete/{id}")
    @Operation(summary = "完成订单")
    public Result complete(@PathVariable Long id) {
        log.info("完成订单：{}", id);
        orderService.complete(id);
        return Result.success();
    }
}
