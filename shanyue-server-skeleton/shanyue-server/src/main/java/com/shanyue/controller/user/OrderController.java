package com.shanyue.controller.user;

import com.shanyue.dto.OrdersPageQueryDTO;
import com.shanyue.dto.OrdersPaymentDTO;
import com.shanyue.dto.OrdersSubmitDTO;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import com.shanyue.service.OrderService;
import com.shanyue.vo.OrderPaymentVO;
import com.shanyue.vo.OrderSubmitVO;
import com.shanyue.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【C 端：订单】（对照课程 user/OrderController，8 个接口）
 * ┌ POST /user/order/submit            提交订单（返回金额/取餐号/外送费）
 * ├ PUT  /user/order/payment           订单支付第一步：获取支付参数（模拟）
 * ├ PUT  /user/order/paySuccess        订单支付第二步：模拟"支付成功回调"（query 传 orderNumber）
 * ├ GET  /user/order/historyOrders     历史订单分页（?page=&pageSize=&status=）
 * ├ GET  /user/order/orderDetail/{id}  订单详情
 * ├ PUT  /user/order/cancel/{id}       取消订单（待付款/待接单可取消）
 * ├ POST /user/order/repetition/{id}   再来一单（明细复刻回购物车）
 * └ GET  /user/order/reminder/{id}     催单（WebSocket 推管理端 type=2）
 *
 * 【本类知识点：支付为什么拆成两步？（苍穹契约，前端联调最容易懵的点）】
 * 真实微信支付：PUT /payment 拿到 timeStamp/nonceStr/packageStr/paySign →
 * wx.requestPayment(参数) 拉起收银台 → 用户付款 → 微信服务器回调后端 → 订单置已支付。
 * 山月模拟：/payment 只发"占位参数"不改状态；前端"支付成功"页再调
 * PUT /paySuccess?orderNumber=xxx（等价于模拟微信回调入口）→ 服务端置已支付+来单提醒。
 * 两步缺一不可：只调第一步钱没付（状态未变），只调第二步等于跳过收银台。
 * paySuccess 的 orderNumber 用 @RequestParam（query 传参）—— 苍穹原版契约如此。
 */
@RestController("userOrderController")
@RequestMapping("/user/order")
@Tag(name = "C端-订单接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 提交订单（六步法见 OrderServiceImpl.submit；返回服务端重算的金额与取餐号）
     */
    @PostMapping("/submit")
    @Operation(summary = "C端-提交订单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("提交订单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付（第一步：获取支付参数，模拟版不改状态）
     */
    @PutMapping("/payment")
    @Operation(summary = "C端-订单支付（获取支付参数）")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成支付参数：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 支付成功回调（第二步：模拟微信回调；query 传 orderNumber）
     * 真实场景这个请求由微信服务器发起（notify_url），山月由前端支付成功页发起。
     */
    @PutMapping("/paySuccess")
    @Operation(summary = "C端-支付成功回调（模拟）")
    public Result paySuccess(@RequestParam String orderNumber) {
        log.info("支付成功回调，orderNumber={}", orderNumber);
        orderService.paySuccess(orderNumber);
        return Result.success();
    }

    /**
     * 历史订单分页查询（服务端强制只查当前用户自己的订单）
     */
    @GetMapping("/historyOrders")
    @Operation(summary = "C端-查询历史订单")
    public Result<PageResult> historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("查询历史订单：{}", ordersPageQueryDTO);
        PageResult pageResult = orderService.page(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 订单详情（校验归属：不是本人的订单按"不存在"处理）
     */
    @GetMapping("/orderDetail/{id}")
    @Operation(summary = "C端-查询订单详情")
    public Result<OrderVO> orderDetail(@PathVariable Long id) {
        log.info("查询订单详情：{}", id);
        OrderVO orderVO = orderService.getorderDetailById(id);
        return Result.success(orderVO);
    }

    /**
     * 取消订单（待付款/待接单可取消；已支付单模拟原路退款）
     */
    @PutMapping("/cancel/{id}")
    @Operation(summary = "C端-取消订单")
    public Result cancel(@PathVariable Long id) {
        log.info("取消订单：{}", id);
        orderService.cancel(id);
        return Result.success();
    }

    /**
     * 再来一单（订单明细原样复刻回购物车，含规格快照）
     */
    @PostMapping("/repetition/{id}")
    @Operation(summary = "C端-再来一单")
    public Result repetition(@PathVariable Long id) {
        log.info("再来一单：{}", id);
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 用户催单（WebSocket 推送 type=2 给管理端）
     */
    @GetMapping("/reminder/{id}")
    @Operation(summary = "C端-用户催单")
    public Result reminder(@PathVariable Long id) {
        log.info("用户催单：{}", id);
        orderService.reminder(id);
        return Result.success();
    }
}
