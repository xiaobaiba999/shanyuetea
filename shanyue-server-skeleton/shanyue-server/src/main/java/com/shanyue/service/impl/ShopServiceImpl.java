package com.shanyue.service.impl;

import com.shanyue.service.ShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 【本类知识点：店铺营业状态服务（完整简版）—— 用 Redis 存"开关型"数据】
 *
 * 一、为什么放 Redis 而不是数据库？
 *   营业状态是"高频读（C 端每次进首页都读）、极少写（商家一天开关几次）、丢了无所谓"的数据，
 *   放 Redis 免去每次查库；即使 Redis 被清了，getStatus 的兜底默认值也能让门店继续营业。
 *
 * 二、key 的设计（山月任务约定）："shanyue:shop:status"
 *   层级命名（模块:对象:属性）是 Redis key 的通用治理规范，便于批量扫描与权限划分。
 *   课程原版用的是常量类 ShopStatusConstant.KEY = "SHOP_STATUS"，本骨架按任务约定改用
 *   层级 key；如需与课程对照，把本常量换成 ShopStatusConstant.KEY 即可（一处改动）。
 *   注意：管理端 PUT /admin/shop/{status} 与 C 端 GET /user/shop/status 都通过本服务读写，
 *   key 只在这一个类出现（改 key 不用到处找）。
 *
 * 三、序列化说明：RedisTemplateConfiguration 把 key 序列化器设为 StringRedisSerializer，
 *   所以 redis-cli 里能看到可读的 key 名；value 保留 JDK 序列化存 Integer。
 */
@Slf4j
@Service
public class ShopServiceImpl implements ShopService {

    /** 营业状态的 Redis key（层级命名规范，见类注释） */
    public static final String KEY = "shanyue:shop:status";

    /** 店铺公告的 Redis key（与状态同款命名规范：模块:对象:属性） */
    public static final String NOTICE_KEY = "shanyue:shop:notice";

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 查询店铺营业状态
     *
     * @return 1 营业中 0 打烊（Redis 无值时默认 1 —— 新环境/Redis 重启后门店自动视为营业中，
     *         避免出现"没设置过状态就全员打烊"的尴尬）
     */
    @Override
    public Integer getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        // 兜底：没设置过 → 默认营业中（课程原版直接返回 null，前端要自己判空，山月更稳一点）
        return status == null ? 1 : status;
    }

    /**
     * 设置店铺营业状态（管理端开关按钮）
     *
     * @param status 1 营业中 0 打烊
     */
    @Override
    public void setStatus(Integer status) {
        log.info("设置店铺营业状态：{}", status == 1 ? "营业中" : "打烊");
        // SET 操作：value 是 Integer，存进去以后 C 端getStatus 取回同一个对象
        redisTemplate.opsForValue().set(KEY, status);
        // （引用备注：常量类 ShopStatusConstant.KEY="SHOP_STATUS" 是课程原名，保留仅作对照）
    }

    /**
     * 查询店铺公告（从未设置过 → 空串，前端展示「暂无公告」）
     *
     * 公告与状态一样是「高频读、极少写、丢了可重建」的轻数据，同款 Redis 方案；
     * 丢了也不影响任何交易链路 —— 这也是敢放 Redis 的判断依据（答辩讲点）。
     * 若将来 C 端（小程序门店条）要展示公告：加 GET /user/shop/notice 并在
     * WebMvcConfiguration 的放行清单补一行即可（免登录的公开数据）。
     */
    @Override
    public String getNotice() {
        String notice = (String) redisTemplate.opsForValue().get(NOTICE_KEY);
        return notice == null ? "" : notice;
    }

    /**
     * 修改店铺公告（管理端「店铺设置」页「保存公告」按钮）
     *
     * @param notice 公告文本（长度校验在 Controller 层）
     */
    @Override
    public void setNotice(String notice) {
        log.info("修改店铺公告：{}", notice);
        redisTemplate.opsForValue().set(NOTICE_KEY, notice);
    }
}
