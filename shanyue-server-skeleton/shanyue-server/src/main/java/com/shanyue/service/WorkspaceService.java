package com.shanyue.service;

import com.shanyue.vo.BusinessDataVO;
import com.shanyue.vo.DrinkOverViewVO;
import com.shanyue.vo.OrderOverViewVO;
import com.shanyue.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

/**
 * 工作台服务接口 —— 4 个方法全部留 TODO（教学练习，可逐行对照课程 WorkspaceServiceImpl）
 */
public interface WorkspaceService {

    /**
     * TODO 今日运营数据（营业额/有效订单/完成率/均价/新增用户）
     */
    BusinessDataVO businessData(LocalDateTime begin, LocalDateTime end);

    /**
     * TODO 订单管理数据总览（各状态订单数量）
     */
    OrderOverViewVO overviewOrders();

    /**
     * TODO 饮品总览（起售/停售数量）
     */
    DrinkOverViewVO overviewDrinkes();

    /**
     * TODO 套餐总览（起售/停售数量）
     */
    SetmealOverViewVO overviewSetmeals();
}
