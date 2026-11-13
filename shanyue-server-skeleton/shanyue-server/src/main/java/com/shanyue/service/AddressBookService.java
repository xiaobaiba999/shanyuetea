package com.shanyue.service;

import com.shanyue.entity.AddressBook;

import java.util.List;

/**
 * C 端地址簿服务接口（对照课程 AddressBookService，7 个方法 1:1）
 */
public interface AddressBookService {

    /**
     * 新增地址（userId 从 BaseContext 取，默认地址标记为否）
     *
     * @param addressBook 地址实体
     */
    void save(AddressBook addressBook);

    /**
     * 查询当前用户地址列表
     *
     * @return 地址列表
     */
    List<AddressBook> list();

    /**
     * 查询当前用户默认地址（下单页自动带出）
     *
     * @return 默认地址，没有返回 null
     */
    AddressBook getDefault();

    /**
     * 根据 id 查询地址
     *
     * @param id 地址 id
     * @return 地址实体
     */
    AddressBook getById(Long id);

    /**
     * 删除地址（带 userId 越权防护）
     *
     * @param id 地址 id
     */
    void deleteById(Long id);

    /**
     * 更新地址
     *
     * @param addressBook 地址实体（含 id）
     */
    void update(AddressBook addressBook);

    /**
     * 设为默认地址（先把该用户所有地址 isDefault 清零，再置目标地址为默认）
     *
     * @param addressBook 只需 id 字段
     */
    void updateDefault(AddressBook addressBook);
}
