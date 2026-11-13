package com.shanyue.controller.user;

import com.shanyue.entity.AddressBook;
import com.shanyue.result.Result;
import com.shanyue.service.AddressBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【C 端：地址簿】（对照课程 AddressBookController，7 个接口）
 * ┌ POST   /user/addressBook           新增地址
 * ├ GET    /user/addressBook/list      地址列表
 * ├ GET    /user/addressBook/default   查询默认地址（下单页自动带出）
 * ├ GET    /user/addressBook/{id}      按 id 查询（编辑回显）
 * ├ DELETE /user/addressBook?id=       删除地址（注意：id 是 query 参数，苍穹契约）
 * ├ PUT    /user/addressBook           更新地址
 * └ PUT    /user/addressBook/default   设为默认地址（body: {id}）
 *
 * 【本类知识点：苍穹风格的"同路径不同动词"设计】
 * /user/addressBook 一个路径承载 增(POST)/删(DELETE)/改(PUT) 三个动词；
 * default 路径承载 GET(查默认) 与 PUT(设默认) —— RESTful 风格 + 课程契约，
 * 前端联调时最容易踩的坑就是"设默认地址用了 GET"或"删除把 id 拼进了路径"。
 * userId 全部由 Service 从 ThreadLocal 注入，请求体里不用传（传了也会被覆盖）。
 */
@RestController
@RequestMapping("/user/addressBook")
@Tag(name = "C端-地址簿接口")
@Slf4j
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    /**
     * 新增地址
     */
    @PostMapping
    @Operation(summary = "C端-新增地址")
    public Result save(@RequestBody AddressBook addressBook) {
        log.info("新增地址：{}", addressBook);
        addressBookService.save(addressBook);
        return Result.success();
    }

    /**
     * 查询当前用户地址列表
     */
    @GetMapping("/list")
    @Operation(summary = "C端-查询地址列表")
    public Result<List<AddressBook>> list() {
        log.info("查询地址列表");
        List<AddressBook> list = addressBookService.list();
        return Result.success(list);
    }

    /**
     * 查询默认地址（没有默认地址时 data 为 null，前端引导用户先添加）
     */
    @GetMapping("/default")
    @Operation(summary = "C端-查询默认地址")
    public Result<AddressBook> getDefault() {
        log.info("查询默认地址");
        AddressBook addressBook = addressBookService.getDefault();
        return Result.success(addressBook);
    }

    /**
     * 根据 id 查询地址（编辑回显）
     */
    @GetMapping("/{id}")
    @Operation(summary = "C端-根据id查询地址")
    public Result<AddressBook> getById(@PathVariable Long id) {
        log.info("根据id查询地址：{}", id);
        AddressBook addressBook = addressBookService.getById(id);
        return Result.success(addressBook);
    }

    /**
     * 删除地址（id 走 query 参数：DELETE /user/addressBook?id=1）
     */
    @DeleteMapping
    @Operation(summary = "C端-删除地址")
    public Result deleteById(Long id) {
        log.info("删除地址：id={}", id);
        addressBookService.deleteById(id);
        return Result.success();
    }

    /**
     * 更新地址
     */
    @PutMapping
    @Operation(summary = "C端-更新地址")
    public Result update(@RequestBody AddressBook addressBook) {
        log.info("更新地址：{}", addressBook);
        addressBookService.update(addressBook);
        return Result.success();
    }

    /**
     * 设为默认地址（body 只需 {id}；先清旧默认再设新默认）
     */
    @PutMapping("/default")
    @Operation(summary = "C端-设置默认地址")
    public Result updateDefault(@RequestBody AddressBook addressBook) {
        log.info("设置默认地址：{}", addressBook);
        addressBookService.updateDefault(addressBook);
        return Result.success();
    }
}
