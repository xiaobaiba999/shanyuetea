package com.shanyue.controller.admin;

import com.shanyue.result.Result;
import com.shanyue.service.ReportService;
import com.shanyue.vo.OrderReportVO;
import com.shanyue.vo.SalesTop10ReportVO;
import com.shanyue.vo.TurnoverReportVO;
import com.shanyue.vo.UserReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

/**
 * 【管理端：数据统计（报表）】（对照课程 ReportController，5 个接口，实现留 TODO）
 * ┌ GET /admin/report/turnoverStatistics  营业额统计（begin/end，yyyy-MM-dd）
 * ├ GET /admin/report/userStatistics     用户统计（逐日新增/累计）
 * ├ GET /admin/report/ordersStatistics   订单统计（逐日单数/有效单/完成率）
 * ├ GET /admin/report/top10              销量 Top10
 * └ GET /admin/report/export             导出近 30 日运营数据 Excel
 *
 * 【本类知识点：GET 日期参数怎么接？@DateTimeFormat】
 * GET 的 query 参数是字符串 "2026-09-26"，@DateTimeFormat(pattern="yyyy-MM-dd")
 * 告诉 Spring 按此格式转成 LocalDate（JSON 请求体才走 @JsonFormat，两者管不同通道）。
 * begin/end 都必传：缺了 Spring 直接 400，进不到方法体。
 *
 * 【教学现状】五个接口的 Service 实现留 TODO（思路写在 ReportServiceImpl 注释里），
 * export 还需要先在 pom 引入 poi/poi-ooxml 依赖 —— 阶段三动手补全。
 */
@RestController
@RequestMapping("/admin/report")
@Slf4j
@Tag(name = "数据统计接口")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 营业额统计
     */
    @GetMapping("/turnoverStatistics")
    @Operation(summary = "营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("营业额统计，begin={}，end={}", begin, end);
        TurnoverReportVO turnoverReportVO = reportService.statistics(begin, end);
        return Result.success(turnoverReportVO);
    }

    /**
     * 用户统计
     */
    @GetMapping("/userStatistics")
    @Operation(summary = "用户统计")
    public Result<UserReportVO> userStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("用户统计，begin={}，end={}", begin, end);
        UserReportVO userReportVO = reportService.userStatistics(begin, end);
        return Result.success(userReportVO);
    }

    /**
     * 订单统计
     */
    @GetMapping("/ordersStatistics")
    @Operation(summary = "订单统计")
    public Result<OrderReportVO> ordersStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("订单统计，begin={}，end={}", begin, end);
        OrderReportVO orderReportVO = reportService.ordersStatistics(begin, end);
        return Result.success(orderReportVO);
    }

    /**
     * 销量 Top10
     */
    @GetMapping("/top10")
    @Operation(summary = "查询销量Top10")
    public Result<SalesTop10ReportVO> getTop10(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("查询销量Top10，begin={}，end={}", begin, end);
        SalesTop10ReportVO salesTop10ReportVO = reportService.getTop10(begin, end);
        return Result.success(salesTop10ReportVO);
    }

    /**
     * 导出近 30 日运营数据 Excel
     * 注意：真正的 Excel 字节流直接写进 response 输出流（不经过 Result JSON 包装）；
     * 方法返回 Result.success 只在"导出失败提前返回错误信息"时有意义。
     */
    @GetMapping("/export")
    @Operation(summary = "导出运营数据Excel")
    public Result<String> export(HttpServletResponse response) {
        log.info("导出运营数据 Excel");
        reportService.export(response);
        return Result.success();
    }
}
