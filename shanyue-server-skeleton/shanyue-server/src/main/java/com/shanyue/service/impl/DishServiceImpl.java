package com.shanyue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shanyue.constant.MessageConstant;
import com.shanyue.constant.StatusConstant;
import com.shanyue.dto.DishDTO;
import com.shanyue.dto.DishPageQueryDTO;
import com.shanyue.dto.SpecGroupDTO;
import com.shanyue.dto.SpecOptionDTO;
import com.shanyue.entity.Dish;
import com.shanyue.entity.SpecGroup;
import com.shanyue.entity.SpecOption;
import com.shanyue.exception.DeletionNotAllowedException;
import com.shanyue.mapper.DishMapper;
import com.shanyue.mapper.OrdersMapper;
import com.shanyue.mapper.SetmealDishMapper;
import com.shanyue.mapper.SpecGroupMapper;
import com.shanyue.mapper.SpecOptionMapper;
import com.shanyue.result.PageResult;
import com.shanyue.service.DishService;
import com.shanyue.vo.DishSalesVO;
import com.shanyue.vo.DishVO;
import com.shanyue.vo.SpecGroupVO;
import com.shanyue.vo.SpecOptionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【本类知识点：饮品服务（核心范本）—— 规格树的保存/回显与"先删后插"策略】
 *
 * 一、山月 v2.0 的规格模型（替代课程的 dish_flavor 口味表）：
 *   drink(饮品) 1 → N spec_group(规格组：杯型/温度/糖度) 1 → N spec_option(选项：大杯+3元/少糖)
 *   所以"保存一个饮品" = 三张表的联动：插 drink → 插 spec_group → 批量插 spec_option，
 *   必须放在一个事务里（@Transactional），任何一步失败整体回滚，不留"半棵树"。
 *
 * 二、"先删后插"策略（updateWithSpec 的核心思想）：
 *   前端编辑规格树可能"改组名/换选项/加组/删组"——如果逐条 diff 更新，代码复杂且容易漏；
 *   干脆把该饮品的旧规格树整个删掉，把提交内容整体重插：
 *   ✔ 前端零负担（只管提交全量树）  ✔ 逻辑极简  代价：id 变化 + 多两条 delete SQL。
 *   这是处理"主表+子表整体覆盖"的通用手法（订单明细更新、套餐明细同理，面试可讲）。
 *
 * 三、月售 monthSales 为什么不建数据库字段？
 *   方案 A（课程常见）：drink 表加 month_sales 列，订单完成时 +1 —— 读快，但要维护冗余字段，
 *   忘记更新就永远不准；
 *   方案 B（本工程）：实时 SQL 聚合 order_detail JOIN orders(status=5) GROUP BY dish_id，
 *   永远准确；整个列表只发【一条】聚合 SQL（循环外），转成 Map 批量回填，避免 N+1 查询。
 */
@Slf4j
@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SpecGroupMapper specGroupMapper;
    @Autowired
    private SpecOptionMapper specOptionMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private OrdersMapper ordersMapper;

    /**
     * 新增饮品（含规格树）：插 drink → 遍历插 spec_group（回填 dishId）→ 批量插 spec_option
     */
    @Transactional
    @Override
    public void saveWithSpec(DishDTO dishDTO) {
        // 1、DTO → 实体：同名属性拷贝（id/name/categoryId/price/image/description/status）
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        // 2、插 drink：@AutoFill 切面自动填 create_time 等；useGeneratedKeys 回填自增 id
        dishMapper.insert(dish);
        Long dishId = dish.getId();
        log.info("新增饮品成功：id={}，name={}", dishId, dish.getName());

        // 3、保存规格树（组/选项的 dishId/groupId 都在这一步回填）
        saveSpecTree(dishId, dishDTO.getSpecGroups());
    }

    /**
     * 更新饮品（含规格树）：先删旧规格树，再整体重插（"先删后插"）
     */
    @Transactional
    @Override
    public void updateWithSpec(DishDTO dishDTO) {
        // 1、更新饮品主表（动态 set：只更新提交的字段；AutoFill 填 update_time/update_user）
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        /*
         * 2、【先删后插】删旧规格树（注意顺序：先删选项、再删组）——
         *    spec_option.group_id 引用 spec_group.id，先删组会让选项变成"孤儿"（或者因外键报错）；
         *    deleteByDishId 用联表把该饮品所有组下的选项一次删干净，deleteByDishId（组）再删组。
         */
        specOptionMapper.deleteByDishId(dishDTO.getId());
        specGroupMapper.deleteByDishId(dishDTO.getId());

        // 3、重插新规格树（整体覆盖，前端提交什么就是什么，无需 diff）
        saveSpecTree(dishDTO.getId(), dishDTO.getSpecGroups());
        log.info("更新饮品完成（规格树先删后插）：id={}", dishDTO.getId());
    }

    /**
     * 批量删除饮品（校验：起售中不能删、被套餐引用不能删；连规格树一起删）
     */
    @Transactional
    @Override
    public void delete(List<Long> ids) {
        // 校验 1：起售中的饮品不能删（照课程工程：逐个查状态，有起售即拒绝）
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish != null && dish.getStatus().equals(StatusConstant.ENABLE)) {
                // 注意用 equals 比较 Integer：== 只比较引用，Integer 缓存范围外必错（教学坑点）
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        // 校验 2：被套餐引用的饮品不能删（setmeal_dish 里有引用记录）
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 删主表（批量 in 删除）+ 删规格树（选项联组表删，再删组）
        dishMapper.deleteBatch(ids);
        for (Long id : ids) {
            specOptionMapper.deleteByDishId(id);
            specGroupMapper.deleteByDishId(id);
        }
        log.info("批量删除饮品：ids={}", ids);
    }

    /**
     * 根据 id 查询饮品详情（管理端编辑页回显：含规格树）
     */
    @Override
    public DishVO getByIdWithSpec(Long id) {
        // 1、查主表
        Dish dish = dishMapper.getById(id);

        // 2、实体 → VO（同名属性拷贝）
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);

        // 3、挂上规格树（组 → 选项 两层）
        dishVO.setSpecGroups(buildSpecTree(id));
        return dishVO;
    }

    /**
     * 分页查询饮品（管理端列表：动态过滤 + PageHelper + 联表 categoryName）
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        // PageHelper.startPage 必须紧跟第一条查询（ThreadLocal 传参，拦截器拼 limit）
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 管理端：根据分类 id 查询启售饮品列表（套餐编辑下拉框用）
     */
    @Override
    public List<Dish> list(Long categoryId) {
        // 装上 status=ENABLE：下拉框只该出现"起售中"的饮品
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.selectList(dish);
    }

    /**
     * C 端：按分类查询启售饮品（DishVO 含规格树 + 月售 monthSales）
     */
    @Override
    public List<DishVO> getList(Long categoryId) {
        // 1、查该分类下起售饮品（status=1）
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        List<Dish> dishList = dishMapper.getByCategoryId(dish);

        // 2、【一次】聚合查询月售（循环外！避免每个饮品查一次的 N+1 问题），
        //    SQL：order_detail JOIN orders(status=5) GROUP BY dish_id → List{dishId, salesNum}
        List<DishSalesVO> salesList = ordersMapper.getMonthSales();
        Map<Long, Integer> salesMap = salesList.stream()
                .collect(Collectors.toMap(DishSalesVO::getDishId, DishSalesVO::getSalesNum));

        // 3、实体 → VO，逐个挂规格树与月售
        List<DishVO> dishVOList = new ArrayList<>();
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);
            dishVO.setSpecGroups(buildSpecTree(d.getId()));
            // 月售：没销量的饮品取 0（getOrDefault 兜底，不能给 null 让前端显示 NaN）
            dishVO.setMonthSales(salesMap.getOrDefault(d.getId(), 0));
            dishVOList.add(dishVO);
        }
        return dishVOList;
    }

    /**
     * 起售/停售饮品（只改 status，复用动态 set 的 update）
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .status(status)
                .id(id)
                .build();
        dishMapper.update(dish);
    }

    /* -------------------- 私有辅助方法：规格树的读/写 -------------------- */

    /**
     * 保存规格树：遍历 specGroups → 插组（回填 dishId，useGeneratedKeys 回填组 id）
     * → 组内选项转实体（回填 groupId）→ 批量插选项
     *
     * @param dishId     饮品 id（新增时是自增回填的新 id；更新时是已有 id）
     * @param specGroups 前端提交的规格组列表（每组含 options）
     */
    private void saveSpecTree(Long dishId, List<SpecGroupDTO> specGroups) {
        // 允许不传规格（简单商品没有规格），空树直接跳过
        if (specGroups == null || specGroups.isEmpty()) {
            return;
        }
        for (SpecGroupDTO groupDTO : specGroups) {
            // 1、组 DTO → 实体，回填 dishId 后插入（@Options(useGeneratedKeys) 回填组 id）
            SpecGroup group = SpecGroup.builder()
                    .dishId(dishId)
                    .name(groupDTO.getName())
                    .sort(groupDTO.getSort())
                    .build();
            specGroupMapper.insert(group);

            // 2、组内选项 DTO → 实体，回填 groupId
            List<SpecOption> options = new ArrayList<>();
            for (SpecOptionDTO optionDTO : groupDTO.getOptions()) {
                SpecOption option = SpecOption.builder()
                        .groupId(group.getId())
                        .name(optionDTO.getName())
                        .priceDelta(optionDTO.getPriceDelta())
                        .sort(optionDTO.getSort())
                        .build();
                options.add(option);
            }
            // 3、批量插选项（一条 SQL 多行 values，比循环单插快）
            if (!options.isEmpty()) {
                specOptionMapper.insertBatch(options);
            }
        }
    }

    /**
     * 构建规格树 VO：查该饮品全部组 → 每组查选项 → 拼成两层树
     * （教学提示：组数通常 2~3 个，循环查选项即可；要极致优化可一次性查出所有选项内存分组）
     *
     * @param dishId 饮品 id
     * @return 规格组 VO 列表（每组含 options）
     */
    private List<SpecGroupVO> buildSpecTree(Long dishId) {
        List<SpecGroupVO> groupVOList = new ArrayList<>();
        // 1、第一层：该饮品的全部规格组（按 sort 升序）
        List<SpecGroup> groups = specGroupMapper.getByDishId(dishId);
        for (SpecGroup group : groups) {
            // 2、第二层：组内全部选项（按 sort 升序）
            List<SpecOptionVO> optionVOList = new ArrayList<>();
            for (SpecOption option : specOptionMapper.getByGroupId(group.getId())) {
                SpecOptionVO optionVO = SpecOptionVO.builder()
                        .id(option.getId())
                        .name(option.getName())
                        .priceDelta(option.getPriceDelta())
                        .sort(option.getSort())
                        .build();
                optionVOList.add(optionVO);
            }
            // 3、组 VO 挂上选项列表
            SpecGroupVO groupVO = SpecGroupVO.builder()
                    .id(group.getId())
                    .dishId(group.getDishId())
                    .name(group.getName())
                    .sort(group.getSort())
                    .options(optionVOList)
                    .build();
            groupVOList.add(groupVO);
        }
        return groupVOList;
    }
}
