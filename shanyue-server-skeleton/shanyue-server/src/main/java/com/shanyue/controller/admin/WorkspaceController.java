package com.shanyue.controller.admin;

import com.shanyue.result.Result;
import com.shanyue.service.WorkspaceService;
import com.shanyue.vo.BusinessDataVO;
import com.shanyue.vo.DrinkOverViewVO;
import com.shanyue.vo.OrderOverViewVO;
import com.shanyue.vo.SetmealOverViewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 【管理端：工作台】（对照课程 WorkspaceController，4 个接口，实现留 TODO）
 * ┌ GET /admin/workspace/businessData    今日运营数据（营业额/有效订单/完成率/均价/新增用户）
 * ├ GET /admin/workspace/overviewOrders 订单管理数据总览（各状态数量）
 * ├ GET /admin/workspace/overviewDrinkes 饮品总览（起售/停售数）
 * └ GET /admin/workspace/overviewSetmeals 套餐总览（起售/停售数）
 *
 * 【本类知识点：时间区间怎么构造？LocalTime.MIN / MAX】
 * "今日数据"的区间 = [今日 00:00:00, 今日 23:59:59.999999999]：
 *   LocalDateTime.of(LocalDate.now(), LocalTime.MIN / LocalTime.MAX)。
 *   LocalTime.MAX 带纳秒，用 >= begin and <= end 的 SQL 条件刚好闭区间覆盖全天；
 *   Controller 把时间算好传给 Service —— 统计逻辑只认区间，明天想统计"本周"换个区间即可复用。
 * （课程原版 businessData 写成了 private —— 本类修正为 public，规范写法。）
 */
@RestController
@RequestMapping("/admin/workspace")
@Slf4j
@Tag(name = "工作台接口")
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 今日运营数据
     */
    @GetMapping("/businessData")
    @Operation(summary = "查询今日运营数据")
    public Result<BusinessDataVO> businessData() {
        log.info("获取今日运营数据");
        // 今日区间：00:00:00 ~ 23:59:59.999999999（闭区间）
        LocalDateTime begin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        BusinessDataVO businessDataVO = workspaceService.businessData(begin, end);
        return Result.success(businessDataVO);
    }

    /**
     * 订单管理数据总览
     */
    @GetMapping("/overviewOrders")
    @Operation(summary = "查询订单管理数据")
    public Result<OrderOverViewVO> overviewOrders() {
        log.info("查询订单管理数据总览");
        OrderOverViewVO orderOverViewVO = workspaceService.overviewOrders();
        return Result.success(orderOverViewVO);
    }

    /**
     * 饮品总览
     */
    @GetMapping("/overviewDrinkes")
    @Operation(summary = "查询饮品总览")
    public Result<DrinkOverViewVO> overviewDrinkes() {
        log.info("查询饮品总览");
        DrinkOverViewVO drinkOverViewVO = workspaceService.overviewDrinkes();
        return Result.success(drinkOverViewVO);
    }

    /**
     * 套餐总览
     */
    @GetMapping("/overviewSetmeals")
    @Operation(summary = "查询套餐总览")
    public Result<SetmealOverViewVO> overviewSetmeals() {
        log.info("查询套餐总览");
        SetmealOverViewVO setmealOverViewVO = workspaceService.overviewSetmeals();
        return Result.success(setmealOverViewVO);
    }
}
