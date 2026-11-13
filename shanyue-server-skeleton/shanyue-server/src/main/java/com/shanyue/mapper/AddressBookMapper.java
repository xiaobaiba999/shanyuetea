package com.shanyue.mapper;

import com.shanyue.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：地址簿 Mapper（C 端收货地址）——接口零 SQL，全部语句在 XML】
 * 1. 5 个方法对应的 SQL 全部在 src/main/resources/mapper/AddressBookMapper.xml。
 * 2. update 是"动态 set + 动态 where"双动态：设置默认地址时只传 isDefault + id + userId，
 *    普通编辑地址时传要改的字段 + id —— 一个方法两用（课程同款设计）。
 * 3. deleteById 带 user_id 条件 + @Param 双参数：防止横向越权删别人的地址
 *    （前端只传 id 也删不掉别人的数据，where 里还要求 user_id 对得上）。
 */
@Mapper
public interface AddressBookMapper {

    /**
     * 新增地址（userId 由 Service 从 BaseContext 取出后设置，防止替别人存地址）
     *
     * @param addressBook 地址实体
     */
    void insert(AddressBook addressBook);

    /**
     * 条件查询地址（XML 动态 where：userId 必传；isDefault 可选 → 查默认地址）
     *
     * @param addressBook userId / isDefault / phone
     * @return 地址列表
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 根据 id 查询地址（下单时校验外送地址用）
     *
     * @param id 地址 id
     * @return 地址实体
     */
    AddressBook getById(Long id);

    /**
     * 删除地址（带 user_id 条件：防止横向越权删别人的地址）
     *
     * @param id     地址 id
     * @param userId 当前登录用户 id
     */
    void deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 更新地址（XML：动态 set + 动态 where；编辑资料 / 设置默认地址共用）
     *
     * @param addressBook 只装需要改的字段；where 由 id / userId 组合
     */
    void update(AddressBook addressBook);
}
