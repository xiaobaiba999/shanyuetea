package com.shanyue.mapper;

import com.shanyue.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【本类知识点：C 端用户表 Mapper（对照课程 UserMapper）——接口零 SQL，全部语句在 XML】
 * user 表是微信小程序用户：核心字段是 openid（微信唯一标识，唯一索引 idx_openid）。
 * 登录流程：jscode2session 换 openid → 按 openid 查库 → 不存在则注册（insert）→ 生成 JWT。
 * 3 个方法的 SQL 全部在 src/main/resources/mapper/UserMapper.xml。
 * 重点：insert 在 XML 里带 useGeneratedKeys="true" keyProperty="id"——
 * 自增 id 回填 user.id，Service 拿它写进 JWT claims；少了它 user.getId() 永远是 null，
 * 令牌里没有 userId，后续所有 /user/** 接口的"获取当前用户"全挂。
 */
@Mapper
public interface UserMapper {

    /**
     * 根据 openid 查询用户（判断"新用户还是老用户"）
     *
     * @param openid 微信用户唯一标识
     * @return 用户实体，不存在返回 null
     */
    User getByOpenId(String openid);

    /**
     * 新增用户（注册；XML：useGeneratedKeys 回填自增 id 供 JWT 使用）
     *
     * @param user 含 openid + createTime
     */
    void insert(User user);

    /**
     * 根据 id 查询用户（C 端"我的"资料页 profile 用）
     *
     * @param userId 用户 id（来自 BaseContext）
     * @return 用户实体
     */
    User getById(Long userId);
}
