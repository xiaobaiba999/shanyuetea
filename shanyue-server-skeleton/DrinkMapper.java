package com.shanyue.mapper;

import com.github.pagehelper.Page;
import com.shanyue.annotation.AutoFill;
import com.shanyue.dto.DrinkPageQueryDTO;
import com.shanyue.entity.Drink;
import com.shanyue.enumeration.OperationType;
import com.shanyue.vo.DrinkVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：饮品 Mapper——接口零 SQL，全部语句在 XML】
 * 1. 9 个方法对应的 SQL 全部在 src/main/resources/mapper/DrinkMapper.xml
 *    （与课程 DrinkMapper.xml 一一对应，含 useGeneratedKeys 的 insert）。
 * 2. deleteBatch 的 @Param("ids")：XML <foreach collection="ids"> 按这个名字取参
 *    （课程原版同款；不加 @Param 时单参数集合默认名叫 list，容易踩坑，统一加 @Param 最稳）。
 * 3. @AutoFill(INSERT/UPDATE)：切面自动填 create_time/update_time/create_user/update_user。
 * 4. pageQuery 的返回 DrinkVO 含 categoryName（联 category 表带出）——
 *    VO 不在 type-aliases 扫描范围，XML 里 resultType 要写全限定名。
 */
@Mapper
public interface DrinkMapper {

    /**
     * 查询分类下是否有饮品（删除分类前的校验，CategoryServiceImpl 用）
     *
     * @param categoryId 分类 id
     * @return 该分类下的饮品数量
     */
    int countDrink(Long categoryId);

    /**
     * 新增饮品（XML：useGeneratedKeys 回填自增 id，供 saveWithSpec 继续插规格组）
     *
     * @param drink 由切面填好公共字段
     */
    @AutoFill(OperationType.INSERT)
    void insert(Drink drink);

    /**
     * 分页查询饮品（XML：联 category 带出分类名 + name/categoryId/status 动态过滤）
     *
     * @param drinkPageQueryDTO page/pageSize/name/categoryId/status
     * @return 分页结果（DrinkVO 含 categoryName）
     */
    Page<DrinkVO> pageQuery(DrinkPageQueryDTO drinkPageQueryDTO);

    /**
     * 批量删除饮品（XML：<foreach> 拼 in 条件）
     *
     * @param ids 饮品 id 集合
     */
    void deleteBatch(@Param("ids") List<Long> ids);

    /**
     * 根据 id 查询饮品
     *
     * @param id 饮品 id
     * @return 饮品实体
     */
    Drink getById(Long id);

    /**
     * 更新饮品（XML：动态 set，起售/停售与资料修改共用）
     *
     * @param drink 只装需要改的字段 + id
     */
    @AutoFill(OperationType.UPDATE)
    void update(Drink drink);

    /**
     * 管理端：按条件查询饮品列表（XML 动态 where：下拉选择/搜索用）
     *
     * @param drink name/categoryId/status
     * @return 饮品列表
     */
    List<Drink> selectList(Drink drink);

    /**
     * C 端：根据分类 id 查询启售饮品列表（XML 动态 where，与 selectList 同款只是语义不同，
     * 课程原版也是两个方法各管一端，方便你对照时一一对应）
     *
     * @param drink categoryId + status(ENABLE)
     * @return 饮品列表
     */
    List<Drink> getByCategoryId(Drink drink);
}
