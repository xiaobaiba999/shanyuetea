package com.shanyue.service.impl;

import com.shanyue.constant.StatusConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.entity.AddressBook;
import com.shanyue.mapper.AddressBookMapper;
import com.shanyue.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 【本类知识点：C 端地址簿（完整 7 方法，对照课程 AddressBookServiceImpl 1:1）】
 *
 * 一、核心安全规则：userId 一律来自 ThreadLocal（BaseContext），不信任前端：
 *   - save：强制把 userId 设为当前登录人（前端传了也覆盖）——防止"替别人存地址"；
 *   - delete：Mapper 的 SQL 是 "delete ... where id=#{id} and user_id=#{userId}"，
 *     双条件删除天然防横向越权（删别人的 id 影响行数为 0，等于删不动）；
 *   - update / updateDefault：update 的 where 同样带 userId 条件。
 *
 * 二、"设为默认地址"两步走（课程同款）：
 *   1. 先把该用户【所有】地址的 is_default 清 0（不清理的话会出现两个默认地址）；
 *   2. 再把目标地址 is_default 置 1。
 *   两步要在一个事务里（@Transactional），中途失败不至于"全被清了但没有新默认"。
 *
 * 三、Mapper 复用技巧：AddressBookMapper.update 是"动态 set + 动态 where"，
 *   编辑资料（set 全字段 + where id）与设默认（set is_default + where user_id）共用一个方法。
 */
@Slf4j
@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 新增地址（userId 从 ThreadLocal 取，默认地址标记为否）
     */
    @Override
    public void save(AddressBook addressBook) {
        // 强制归属当前登录用户：就算前端恶意传了别人的 userId 也被覆盖
        addressBook.setUserId(BaseContext.getCurrentId());
        // 新地址默认"非默认"（是否默认走 updateDefault 专用接口，语义清晰）
        addressBook.setIsDefault(StatusConstant.DISABLE);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 查询当前用户的地址列表（地址管理页）
     */
    @Override
    public List<AddressBook> list() {
        // 只按 userId 查：每个人只能看到自己的地址
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(BaseContext.getCurrentId());
        return addressBookMapper.list(addressBook);
    }

    /**
     * 查询当前用户的默认地址（下单页自动带出）
     */
    @Override
    public AddressBook getDefault() {
        // 条件：userId + isDefault=1 → 最多一条；查不到返回 null（前端提示"请先添加地址"）
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(StatusConstant.ENABLE);
        List<AddressBook> list = addressBookMapper.list(addressBook);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * 根据 id 查询地址（地址编辑页回显）
     */
    @Override
    public AddressBook getById(Long id) {
        // 课程同款：直接查。若要更严可校验 addressBook.getUserId() == 当前用户（教学进阶）
        return addressBookMapper.getById(id);
    }

    /**
     * 删除地址（Mapper SQL 带 user_id 条件：横向越权删不动别人的地址）
     */
    @Override
    public void deleteById(Long id) {
        addressBookMapper.deleteById(id, BaseContext.getCurrentId());
    }

    /**
     * 更新地址（编辑资料）
     */
    @Override
    public void update(AddressBook addressBook) {
        // 把当前用户塞进参数 → 动态 where 变成 "id=#{id} and user_id=#{userId}"，防越权改别人的
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.update(addressBook);
    }

    /**
     * 设为默认地址（先清旧默认，再设新默认，两步一个事务）
     */
    @Transactional
    @Override
    public void updateDefault(AddressBook addressBook) {
        // -------- 第 1 步：把该用户所有地址的 is_default 清 0 --------
        // 只装 userId + isDefault=0 → 动态 where 按 userId 更新（整表对该用户生效）
        AddressBook cancelDefault = AddressBook.builder()
                .userId(BaseContext.getCurrentId())
                .isDefault(StatusConstant.DISABLE)
                .build();
        addressBookMapper.update(cancelDefault);

        // -------- 第 2 步：把目标地址 is_default 置 1 --------
        // 只装 id + userId + isDefault=1 → where id + user_id，只改这一行
        AddressBook setDefault = AddressBook.builder()
                .id(addressBook.getId())
                .userId(BaseContext.getCurrentId())
                .isDefault(StatusConstant.ENABLE)
                .build();
        addressBookMapper.update(setDefault);
        log.info("已设置默认地址：userId={}，addressId={}", BaseContext.getCurrentId(), addressBook.getId());
    }
}
