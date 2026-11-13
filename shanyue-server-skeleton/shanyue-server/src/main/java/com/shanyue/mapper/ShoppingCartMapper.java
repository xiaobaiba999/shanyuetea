package com.shanyue.mapper;

import com.shanyue.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 【本类知识点：购物车 Mapper——接口零 SQL，全部语句在 XML】
 * 1. 本接口不写任何 SQL：7 个方法对应的语句全部在
 *    src/main/resources/mapper/ShoppingCartMapper.xml（namespace = 本接口全限定名，
 *    语句 id = 方法名，MyBatis 靠这两个对上号）。
 *    好处：接口只回答"有什么方法"，XML 只回答"SQL 长什么样"，一眼分清职责。
 * 2. "同一饮品不同规格是两行"的判定 = user_id + drink_id + spec_selected 三者全同。
 *    add 与 sub 都先 list 精确查一次再决定加数量还是插新行；传进来的 specSelected
 *    必须是 Service 端按数据库真值重排好的"规范快照"，保证同一组规格不管
 *    前端传参顺序如何都能命中同一行。
 * 3. XML 里 list() 对 spec_selected 做了"等于 / is null"双分支——
 *    SQL 里 spec_selected = NULL 永远不成立（NULL 要用 is null 判断），
 *    而"无规格商品"这行的 spec_selected 存的就是 NULL。
 * 4. @Param("shoppingCartList")：给集合参数起名，XML <foreach collection="shoppingCartList"> 按名取参
 *    （不加 @Param 时单集合参数默认名叫 list/array，容易踩坑，统一加 @Param 最稳）。
 */
@Mapper
public interface ShoppingCartMapper {

    /**
     * 查询购物车（XML 动态条件：用户/饮品/套餐/规格快照，任意组合）
     *
     * @param shoppingCart userId 必传，drinkId/setmealId/specSelected 可选
     * @return 购物车列表
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 新增购物车（单条）
     *
     * @param shoppingCart 含名称/图片/单价（服务端算好）/规格快照
     */
    void insert(ShoppingCart shoppingCart);

    /**
     * 批量新增购物车（XML <foreach>："再来一单"把订单明细整批转回购物车）
     *
     * @param shoppingCartList 购物车集合
     */
    void insertBatch(@Param("shoppingCartList") List<ShoppingCart> shoppingCartList);

    /**
     * 修改数量（number +1 / -1 后整值更新）
     *
     * @param cart 只用 id + number
     */
    void updateNumberById(ShoppingCart cart);

    /**
     * 清空当前用户购物车（下单成功后调用）
     *
     * @param userId 用户 id
     */
    void deleteByUserId(Long userId);

    /**
     * 删除购物车中某一行（数量减到 0 时按行主键删，避免"按 drink_id 全删"误伤其它规格）
     *
     * @param id 购物车行 id
     */
    void deleteById(Long id);

    /**
     * 查询某用户的全部购物车行（不按规格过滤）
     * 为什么不直接复用上面的 list()？—— list() 在 specSelected 为 null 时会强制拼
     * "and spec_selected is null"（为"精确匹配无规格商品"设计），
     * 而"查全部/下单重算金额"需要该用户的【所有】行（含带规格的），所以单独提供本方法。
     *
     * 使用场景：
     *   1. C 端 showShoppingCart：购物车列表页展示全部行；
     *   2. OrderServiceImpl.submit 第 1 步：查购物车非空 + 重算金额（要把每行都算进去）。
     *
     * @param userId 用户 id（来自 BaseContext，必传）
     * @return 该用户的全部购物车行（按加购时间升序，与前端展示顺序一致）
     */
    List<ShoppingCart> listByUserId(Long userId);
}
