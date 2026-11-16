package com.shanyue.service;

import com.shanyue.vo.OrderReportVO;
import com.shanyue.vo.SalesTop10ReportVO;
import com.shanyue.vo.TurnoverReportVO;
import com.shanyue.vo.UserReportVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

/**
 * 数据统计（报表）服务接口 —— 5 个方法全部留 TODO（教学练习，可逐行对照课程 ReportServiceImpl）
 * 本骨架已统一到 Spring Boot 4（jakarta.servlet），与课程原工程同口径。
 */
public interface ReportService {

    /**
     * TODO 营业额统计：区间逐日营业额（有效订单 status=5 的 amount 合计）
     */
    TurnoverReportVO statistics(LocalDate begin, LocalDate end);

    /**
     * TODO 用户统计：区间逐日新增用户数 / 累计用户数
     */
    UserReportVO userStatistics(LocalDate begin, LocalDate end);

    /**
     * TODO 订单统计：区间逐日订单数 / 有效订单数 / 完成率
     */
    OrderReportVO ordersStatistics(LocalDate begin, LocalDate end);

    /**
     * TODO 销量 Top10：区间内已完成订单的商品销量排行
     */
    SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end);

    /**
     * TODO 导出近 30 日运营数据 Excel（Apache POI：XSSFWorkbook 逐行写入，response 输出流下载）
     */
    void export(HttpServletResponse response);
}
