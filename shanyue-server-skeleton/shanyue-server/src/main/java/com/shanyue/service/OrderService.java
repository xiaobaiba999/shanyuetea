package com.shanyue.service;

import com.shanyue.dto.OrdersCancelDTO;
import com.shanyue.dto.OrdersConfirmDTO;
import com.shanyue.dto.OrdersPageQueryDTO;
import com.shanyue.dto.OrdersPaymentDTO;
import com.shanyue.dto.OrdersRejectionDTO;
import com.shanyue.dto.OrdersSubmitDTO;
import com.shanyue.result.PageResult;
import com.shanyue.vo.OrderPaymentVO;
import com.shanyue.vo.OrderStatisticsVO;
import com.shanyue.vo.OrderSubmitVO;
import com.shanyue.vo.OrderVO;

/**
 * 订单服务接口：C 端 8 个方法全部实现（本任务核心范本），管理端 statistics 完整、其余留 TODO。
 */
public interface OrderService {

    /* -------------------- C 端（全部实现） -------------------- */

    /**
     * 提交订单（六步：查购物车→校验地址→服务端重算金额→存订单→存明细→清购物车）
     */
    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付（模拟：生成随机 timeStamp/nonceStr/paySign，教学注释讲真实微信支付流程）
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO);

    /**
     * 支付成功回调（模拟）：校验归属 + payStatus==0 → 置已支付 → 状态 2 待接单 → WebSocket 来单提醒
     */
    void paySuccess(String orderNumber);

    /**
     * C 端：分页查询历史订单（自动填当前用户 id，带订单明细）
     */
    PageResult page(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * C 端：查询订单详情（校验订单归属）
     */
    OrderVO getorderDetailById(Long id);

    /**
     * C 端：取消订单（仅待付款/待接单可取消；已支付则模拟退款 payStatus=REFUND）
     */
    void cancel(Long id);

    /**
     * C 端：再来一单（把订单明细复刻回购物车，含规格快照）
     */
    void repetition(Long id);

    /**
     * C 端：催单（WebSocket 推送 type=2 消息给管理端）
     */
    void reminder(Long id);

    /* -------------------- 管理端（statistics 完整，其余 TODO） -------------------- */

    /**
     * 管理端：各状态订单数量统计（待接单/已接单/派送中·待取餐 三张卡片，已完整实现）
     */
    OrderStatisticsVO statistics();

    /**
     * TODO 管理端：订单条件搜索（参照 page() 的写法，不填 userId 即全量）
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * TODO 管理端：根据订单 id 查询详情（管理端版，无需归属校验）
     */
    OrderVO searchDetail(Long id);

    /**
     * TODO 管理端：接单（status 2 → 3）
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * TODO 管理端：拒单（status 2 → 6，已支付需退款，记录 rejectionReason）
     */
    void reject(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * TODO 管理端：派送订单（status 3 → 4）
     */
    void delivery(Long id);

    /**
     * TODO 管理端：完成订单（status 4 → 5）
     */
    void complete(Long id);

    /**
     * TODO 管理端：取消订单（管理端主动取消，原因由商家填写）
     */
    void cancelByAdmin(OrdersCancelDTO ordersCancelDTO);
}
