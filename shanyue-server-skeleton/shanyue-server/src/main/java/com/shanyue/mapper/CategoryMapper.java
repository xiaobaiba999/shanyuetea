package com.shanyue.mapper;

import com.github.pagehelper.Page;
import com.shanyue.annotation.AutoFill;
import com.shanyue.dto.CategoryPageQueryDTO;
import com.shanyue.entity.Category;
import com.shanyue.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 【本类知识点：分类 Mapper——接口零 SQL，全部语句在 XML】
 * 1. 6 个方法对应的 SQL 全部在 src/main/resources/mapper/CategoryMapper.xml
 *    （namespace = 本接口全限定名，语句 id = 方法名）。
 *    page/update/getByCategoryType 是带 <if>/<where>/<set> 的动态 SQL；
 *    getByType/insert/deleteById 是简单 SQL——同样统一放 XML，
 *    接口里一条 SQL 都看不见，与课程 XML 章节形态完全一致。
 * 2. @AutoFill 不是 SQL：贴在 insert/update 上的切面标记，AutoFillAspect 会在
 *    执行前把 create_time/update_time/create_user/update_user 填进实体参数。
 *    它是方法级注解，必须留在接口上，XML 里找不到也不需要。
 * 3. 返回 Page<Category>：配合 PageHelper.startPage(...) 使用，
 *    Page 既是 List 又带 total/总页数，Service 里转成 PageResult。
 */
@Mapper
public interface CategoryMapper {

    /**
     * 分页查询分类（XML：按 name 模糊 + type 精确过滤，按 sort 倒序）
     *
     * @param categoryPageQueryDTO page/pageSize/name/type
     * @return 分页结果
     */
    Page<Category> page(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 更新分类（XML：动态 set，只更新非空字段；startOrStop 也复用它）
     *
     * @param category 只装需要改的字段 + id
     */
    @AutoFill(OperationType.UPDATE)
    void update(Category category);

    /**
     * 根据类型查询分类（管理端下拉框用；type=1 饮品分类 2 套餐分类）
     *
     * @param type 分类类型
     * @return 分类列表
     */
    List<Category> getByType(Integer type);

    /**
     * 新增分类
     *
     * @param category 由切面填好公共字段
     */
    @AutoFill(OperationType.INSERT)
    void insert(Category category);

    /**
     * 删除分类
     *
     * @param id 分类 id
     */
    void deleteById(Long id);

    /**
     * C 端查询分类列表（XML：只按 type 过滤；type 为 null 时查全部）
     *
     * @param category type（可空）
     * @return 分类列表
     */
    List<Category> getByCategoryType(Category category);
}
