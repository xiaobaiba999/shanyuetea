package com.shanyue.mapper;

import com.github.pagehelper.Page;
import com.shanyue.dto.SetmealPageQueryDTO;
import com.shanyue.entity.Setmeal;
import com.shanyue.vo.DrinkItemVO;
import com.shanyue.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：套餐 Mapper —— 接口零 SQL，管理端部分刻意留白（TODO 教学练习）】
 * 1. 已写好的 3 条（countSetmeal/getById/getDrinkItemById）与 C 端 list 都在
 *    src/main/resources/mapper/SetmealMapper.xml；
 *    管理端 pageQuery/insert/update/deleteBatch 四个方法在 XML 里已留好 TODO 注释模板，
 *    你自己补全 SQL（可逐行对照课程 SetmealMapper.xml——那正是你亲手练过的高频代码）。
 * 2. 注意：没有绑定 SQL 的 Mapper 方法在"启动时"不报错，只有"被调用时"才会抛
 *    Invalid bound statement —— 正好用来提醒哪些功能还没实现。
 * 3. deleteBatch 的 @Param("ids") 已贴好：你补 XML 时 <foreach collection="ids"> 直接能用
 *    （不加 @Param 时单集合参数默认名叫 list，<foreach> 写 ids 会报错，这是高频坑）。
 * 4. insert/update 补全后记得贴 @AutoFill(INSERT/UPDATE)，公共字段才能自动填充。
 */
@Mapper
public interface SetmealMapper {

    /**
     * 查询分类下是否有套餐（删除分类前校验，CategoryServiceImpl 用）
     *
     * @param categoryId 分类 id
     * @return 套餐数量
     */
    int countSetmeal(Long categoryId);

    /**
     * 根据 id 查询套餐
     *
     * @param id 套餐 id
     * @return 套餐实体
     */
    Setmeal getById(Long id);

    /**
     * C 端：按分类 id + 起售状态查询套餐列表（XML 动态 where）
     *
     * @param setmeal categoryId + status(ENABLE)
     * @return 套餐列表
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * C 端：查询套餐包含的饮品明细（联 drink 表带出图片与描述）
     *
     * @param setmealId 套餐 id
     * @return 套餐内饮品列表（DrinkItemVO：name/copies/image/description）
     */
    List<DrinkItemVO> getDrinkItemById(Long setmealId);

    /* -------------------- 以下为管理端方法：SQL 在 SetmealMapper.xml 里补全（TODO） -------------------- */

    /**
     * TODO 管理端：分页查询套餐（XML 里补 pageQuery：联 category 带出 categoryName，参照课程）
     *
     * @param setmealPageQueryDTO page/pageSize/name/categoryId/status
     * @return 分页结果
     */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * TODO 管理端：新增套餐（XML 里补 insert：useGeneratedKeys 回填 id）
     * 注意：接口上的 @AutoFill(INSERT) 也要自己贴上
     *
     * @param setmeal 套餐实体
     */
    void insert(Setmeal setmeal);

    /**
     * TODO 管理端：更新套餐（XML 里补 update：动态 set）
     * 注意：接口上的 @AutoFill(UPDATE) 也要自己贴上
     *
     * @param setmeal 套餐实体
     */
    void update(Setmeal setmeal);

    /**
     * TODO 管理端：批量删除套餐（XML 里补 deleteBatch：<foreach> 拼 in 条件，@Param("ids") 已贴好）
     *
     * @param ids 套餐 id 集合
     */
    void deleteBatch(@Param("ids") List<Long> ids);
}
