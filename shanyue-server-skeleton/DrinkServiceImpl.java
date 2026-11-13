package com.shanyue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shanyue.constant.MessageConstant;
import com.shanyue.constant.StatusConstant;
import com.shanyue.dto.DrinkDTO;
import com.shanyue.dto.DrinkPageQueryDTO;
import com.shanyue.dto.SpecGroupDTO;
import com.shanyue.dto.SpecOptionDTO;
import com.shanyue.entity.Drink;
import com.shanyue.entity.SpecGroup;
import com.shanyue.entity.SpecOption;
import com.shanyue.exception.DeletionNotAllowedException;
import com.shanyue.mapper.DrinkMapper;
import com.shanyue.mapper.OrdersMapper;
import com.shanyue.mapper.SetmealDrinkMapper;
import com.shanyue.mapper.SpecGroupMapper;
import com.shanyue.mapper.SpecOptionMapper;
import com.shanyue.result.PageResult;
import com.shanyue.service.DrinkService;
import com.shanyue.vo.DrinkSalesVO;
import com.shanyue.vo.DrinkVO;
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
 * 一、山月 v2.0 的规格模型（替代课程的 drink_flavor 口味表）：
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
 *   方案 B（本工程）：实时 SQL 聚合 order_detail JOIN orders(status=5) GROUP BY drink_id，
 *   永远准确；整个列表只发【一条】聚合 SQL（循环外），转成 Map 批量回填，避免 N+1 查询。
 */
@Slf4j
@Service
public class DrinkServiceImpl implements DrinkService {

    @Autowired
    private DrinkMapper drinkMapper;
    @Autowired
    private SpecGroupMapper specGroupMapper;
    @Autowired
    private SpecOptionMapper specOptionMapper;
    @Autowired
    private SetmealDrinkMapper setmealDrinkMapper;
    @Autowired
    private OrdersMapper ordersMapper;

    /**
     * 新增饮品（含规格树）：插 drink → 遍历插 spec_group（回填 drinkId）→ 批量插 spec_option
     */
    @Transactional
    @Override
    public void saveWithSpec(DrinkDTO drinkDTO) {
        // 1、DTO → 实体：同名属性拷贝（id/name/categoryId/price/image/description/status）
        Drink drink = new Drink();
        BeanUtils.copyProperties(drinkDTO, drink);

        // 2、插 drink：@AutoFill 切面自动填 create_time 等；useGeneratedKeys 回填自增 id
        drinkMapper.insert(drink);
        Long drinkId = drink.getId();
        log.info("新增饮品成功：id={}，name={}", drinkId, drink.getName());

        // 3、保存规格树（组/选项的 drinkId/groupId 都在这一步回填）
        saveSpecTree(drinkId, drinkDTO.getSpecGroups());
    }

    /**
     * 更新饮品（含规格树）：先删旧规格树，再整体重插（"先删后插"）
     */
    @Transactional
    @Override
    public void updateWithSpec(DrinkDTO drinkDTO) {
        // 1、更新饮品主表（动态 set：只更新提交的字段；AutoFill 填 update_time/update_user）
        Drink drink = new Drink();
        BeanUtils.copyProperties(drinkDTO, drink);
        drinkMapper.update(drink);

        /*
         * 2、【先删后插】删旧规格树（注意顺序：先删选项、再删组）——
         *    spec_option.group_id 引用 spec_group.id，先删组会让选项变成"孤儿"（或者因外键报错）；
         *    deleteByDrinkId 用联表把该饮品所有组下的选项一次删干净，deleteByDrinkId（组）再删组。
         */
        specOptionMapper.deleteByDrinkId(drinkDTO.getId());
        specGroupMapper.deleteByDrinkId(drinkDTO.getId());

        // 3、重插新规格树（整体覆盖，前端提交什么就是什么，无需 diff）
        saveSpecTree(drinkDTO.getId(), drinkDTO.getSpecGroups());
        log.info("更新饮品完成（规格树先删后插）：id={}", drinkDTO.getId());
    }

    /**
     * 批量删除饮品（校验：起售中不能删、被套餐引用不能删；连规格树一起删）
     */
    @Transactional
    @Override
    public void delete(List<Long> ids) {
        // 校验 1：起售中的饮品不能删（照课程工程：逐个查状态，有起售即拒绝）
        for (Long id : ids) {
            Drink drink = drinkMapper.getById(id);
            if (drink != null && drink.getStatus().equals(StatusConstant.ENABLE)) {
                // 注意用 equals 比较 Integer：== 只比较引用，Integer 缓存范围外必错（教学坑点）
                throw new DeletionNotAllowedException(MessageConstant.DRINK_ON_SALE);
            }
        }

        // 校验 2：被套餐引用的饮品不能删（setmeal_drink 里有引用记录）
        List<Long> setmealIds = setmealDrinkMapper.getSetmealIdsByDrinkIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DRINK_BE_RELATED_BY_SETMEAL);
        }

        // 删主表（批量 in 删除）+ 删规格树（选项联组表删，再删组）
        drinkMapper.deleteBatch(ids);
        for (Long id : ids) {
            specOptionMapper.deleteByDrinkId(id);
            specGroupMapper.deleteByDrinkId(id);
        }
        log.info("批量删除饮品：ids={}", ids);
    }

    /**
     * 根据 id 查询饮品详情（管理端编辑页回显：含规格树）
     */
    @Override
    public DrinkVO getByIdWithSpec(Long id) {
        // 1、查主表
        Drink drink = drinkMapper.getById(id);

        // 2、实体 → VO（同名属性拷贝）
        DrinkVO drinkVO = new DrinkVO();
        BeanUtils.copyProperties(drink, drinkVO);

        // 3、挂上规格树（组 → 选项 两层）
        drinkVO.setSpecGroups(buildSpecTree(id));
        return drinkVO;
    }

    /**
     * 分页查询饮品（管理端列表：动态过滤 + PageHelper + 联表 categoryName
     *               + 13.7.5 加餐：月售聚合 + 规格树，让列表两列有真值）
     */
    @Override
    public PageResult pageQuery(DrinkPageQueryDTO drinkPageQueryDTO) {
        // 1、PageHelper.startPage 必须紧跟【本方法的第 1 条 SQL】——下面 drinkMapper.pageQuery
        //    就是那条"第 1 条"；月售聚合 SQL 特意放在它后面执行（13.7.5 拆解：放前面会吃掉分页参数）
        PageHelper.startPage(drinkPageQueryDTO.getPage(), drinkPageQueryDTO.getPageSize());

        // 2、分页查饮品列表（联表 categoryName）——PageHelper 分页拦截的就是这一条
        Page<DrinkVO> page = drinkMapper.pageQuery(drinkPageQueryDTO);

        // 3、【一次】聚合查全店月售（循环外，手法同 getList；此时分页参数已被第 2 步消费掉）
        List<DrinkSalesVO> salesList = ordersMapper.getMonthSales();
        Map<Long, Integer> salesMap = salesList.stream()
                .collect(Collectors.toMap(DrinkSalesVO::getDrinkId, DrinkSalesVO::getSalesNum));

        // 4、逐个挂规格树（复用 buildSpecTree）+ 回填月售（没销量取 0 兜底，不给 null）
        for (DrinkVO drinkVO : page.getResult()) {
            drinkVO.setSpecGroups(buildSpecTree(drinkVO.getId()));
            drinkVO.setMonthSales(salesMap.getOrDefault(drinkVO.getId(), 0));
        }

        // 5、page.getResult() 就是原列表对象，字段已挂好，原样装进 PageResult 返回
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 管理端：根据分类 id 查询启售饮品列表（套餐编辑下拉框用）
     */
    @Override
    public List<Drink> list(Long categoryId) {
        // 装上 status=ENABLE：下拉框只该出现"起售中"的饮品
        Drink drink = Drink.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return drinkMapper.selectList(drink);
    }

    /**
     * C 端：按分类查询启售饮品（DrinkVO 含规格树 + 月售 monthSales）
     */
    @Override
    public List<DrinkVO> getList(Long categoryId) {
        // 1、查该分类下起售饮品（status=1）
        Drink drink = Drink.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        List<Drink> drinkList = drinkMapper.getByCategoryId(drink);

        // 2、【一次】聚合查询月售（循环外！避免每个饮品查一次的 N+1 问题），
        //    SQL：order_detail JOIN orders(status=5) GROUP BY drink_id → List{drinkId, salesNum}
        List<DrinkSalesVO> salesList = ordersMapper.getMonthSales();
        Map<Long, Integer> salesMap = salesList.stream()
                .collect(Collectors.toMap(DrinkSalesVO::getDrinkId, DrinkSalesVO::getSalesNum));

        // 3、实体 → VO，逐个挂规格树与月售
        List<DrinkVO> drinkVOList = new ArrayList<>();
        for (Drink d : drinkList) {
            DrinkVO drinkVO = new DrinkVO();
            BeanUtils.copyProperties(d, drinkVO);
            drinkVO.setSpecGroups(buildSpecTree(d.getId()));
            // 月售：没销量的饮品取 0（getOrDefault 兜底，不能给 null 让前端显示 NaN）
            drinkVO.setMonthSales(salesMap.getOrDefault(d.getId(), 0));
            drinkVOList.add(drinkVO);
        }
        return drinkVOList;
    }

    /**
     * 起售/停售饮品（只改 status，复用动态 set 的 update）
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Drink drink = Drink.builder()
                .status(status)
                .id(id)
                .build();
        drinkMapper.update(drink);
    }

    /* -------------------- 私有辅助方法：规格树的读/写 -------------------- */

    /**
     * 保存规格树：遍历 specGroups → 插组（回填 drinkId，useGeneratedKeys 回填组 id）
     * → 组内选项转实体（回填 groupId）→ 批量插选项
     *
     * @param drinkId     饮品 id（新增时是自增回填的新 id；更新时是已有 id）
     * @param specGroups 前端提交的规格组列表（每组含 options）
     */
    private void saveSpecTree(Long drinkId, List<SpecGroupDTO> specGroups) {
        // 允许不传规格（简单商品没有规格），空树直接跳过
        if (specGroups == null || specGroups.isEmpty()) {
            return;
        }
        for (SpecGroupDTO groupDTO : specGroups) {
            // 1、组 DTO → 实体，回填 drinkId 后插入（@Options(useGeneratedKeys) 回填组 id）
            SpecGroup group = SpecGroup.builder()
                    .drinkId(drinkId)
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
     * @param drinkId 饮品 id
     * @return 规格组 VO 列表（每组含 options）
     */
    private List<SpecGroupVO> buildSpecTree(Long drinkId) {
        List<SpecGroupVO> groupVOList = new ArrayList<>();
        // 1、第一层：该饮品的全部规格组（按 sort 升序）
        List<SpecGroup> groups = specGroupMapper.getByDrinkId(drinkId);
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
                    .drinkId(group.getDrinkId())
                    .name(group.getName())
                    .sort(group.getSort())
                    .options(optionVOList)
                    .build();
            groupVOList.add(groupVO);
        }
        return groupVOList;
    }
}
