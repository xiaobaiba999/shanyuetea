package com.shanyue.mapper;

import com.github.pagehelper.Page;
import com.shanyue.dto.OrdersPageQueryDTO;
import com.shanyue.entity.Orders;
import com.shanyue.vo.DrinkSalesVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 【本类知识点：订单 Mapper——接口零 SQL，全部语句在 XML】
 * 1. 9 个方法对应的 SQL 全部在 src/main/resources/mapper/OrdersMapper.xml
 *    （update/page 是动态 SQL；insert/getByNumber/getById/countStatus/getMonthSales 也统一放 XML）。
 * 2. 订单状态机（与课程一致，常量定义在 Orders 实体里）：
 *      1 待付款 → 2 待接单 → 3 已接单 → 4 派送中(外送)/待取餐(自取) → 5 已完成
 *                                                        ↘ 6 已取消（任意阶段可取消）
 * 3. insert 在 XML 里带 useGeneratedKeys="true" keyProperty="id"：
 *    自增 id 回填 orders.id——submit 插完订单立刻拿 orders.getId() 给订单明细挂 orderId，
 *    没有回填的话明细的 order_id 全是 null（等价于注解版的 @Options(useGeneratedKeys...)）。
 * 4. v2.0 新增列：delivery_mode（1 自取 2 外送）、pickup_number（自选取餐号）、delivery_fee（外送费）。
 */
@Mapper
public interface OrdersMapper {

    /**
     * 新增订单（XML：useGeneratedKeys 回填自增 id，订单明细插入依赖它）
     *
     * @param orders 订单实体（金额/快照字段已由 Service 端重算填好）
     */
    void insert(Orders orders);

    /**
     * 更新订单（XML：动态 set——支付/接单/取消/派送/完成全部复用这一个方法）
     *
     * @param orders 只装需要改的字段 + id
     */
    void update(Orders orders);

    /**
     * 根据订单号查询订单（支付/回调场景）
     *
     * @param orderNumber 订单号
     * @return 订单实体
     */
    Orders getByNumber(String orderNumber);

    /**
     * 根据 id 查询订单
     *
     * @param id 订单 id
     * @return 订单实体
     */
    Orders getById(Long id);

    /**
     * 统计某状态的订单数量（管理端"订单统计"三张卡片用）
     *
     * @param status 订单状态
     * @return 数量
     */
    Integer countStatus(Integer status);

    /**
     * 分页查询订单（XML：C 端历史订单 / 管理端条件搜索共用，userId 传了就是 C 端）
     * number/phone 用模糊匹配；beginTime/endTime 是 @DateTimeFormat 解析好的 LocalDateTime。
     *
     * @param ordersPageQueryDTO 分页 + 过滤条件
     * @return 分页结果
     */
    Page<Orders> page(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 【山月 v2.0 新增】月售统计（XML）：聚合每款饮品的已完成订单销量
     * SQL 拆解（见 OrdersMapper.xml 同名语句）：
     *   - order_detail od JOIN orders o ON od.order_id = o.id AND o.status = 5
     *     —— 内连接 + 把"已完成(status=5)"放进连接条件，只统计有效完成单（待付款/取消的单不算）；
     *   - IFNULL(SUM(od.number), 0)：没有销量时返回 0 而不是 NULL；
     *   - GROUP BY od.drink_id：按饮品聚合。
     * 教学提示：任务卡允许先不限制 30 天；要加时间窗只需在 where 补一行
     *   "and o.order_time &gt;= date_sub(now(), interval 30 day)"。
     * 返回 DrinkSalesVO{drinkId, salesNum}，Service 转成 Map<drinkId, 月售> 批量填充 DrinkVO.monthSales。
     *
     * @return 每款饮品的累计销量
     */
    List<DrinkSalesVO> getMonthSales();

    // 【TODO 扩展】查询指定时间段内未支付的订单（超时未付款自动取消的定时任务 OrderTask 会用到）
    // 在 OrdersMapper.xml 里补 <select id="getByStatusAndTime">，接口加上方法声明即可：
    // List<Orders> getByStatusAndTime(Integer status, LocalDateTime time);
    // （注意 XML 里 < 要写成 &lt;，比如 "order_time &lt; #{time}"）
}
