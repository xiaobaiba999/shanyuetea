package com.shanyue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shanyue.dto.SetmealDTO;
import com.shanyue.dto.SetmealPageQueryDTO;
import com.shanyue.entity.Setmeal;
import com.shanyue.mapper.SetmealDrinkMapper;
import com.shanyue.mapper.SetmealMapper;
import com.shanyue.result.PageResult;
import com.shanyue.service.SetmealService;
import com.shanyue.vo.DrinkItemVO;
import com.shanyue.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 【本类知识点：套餐服务 —— C 端两个查询完整实现，管理端留 TODO（教学练习）】
 *
 * 一、已完整实现（C 端小程序点单页直接可用）：
 *   - getByCategoryId：按分类查起售套餐（Mapper 的 list 动态 where）；
 *   - getDrinkItemById：套餐内饮品明细（联 drink 表带出图片/描述）。
 *
 * 二、TODO 的管理端方法（每个方法体里写了实现步骤提示，动手时可逐行对照课程 SetmealServiceImpl）：
 *   注意：SetmealMapper 里 pageQuery/insert/update/deleteBatch 四个方法只留了签名没绑 SQL，
 *   现在调用会抛 "Invalid bound statement" —— 正好提醒哪些功能还没实现；
 *   本实现类为让 Controller 能编译/启动，先把返回值置 null/空集合并 log 提示。
 *
 * 三、deleteBatch 的校验思路（本类最重要的一段业务规则，动手前先想清楚）：
 *   1. 起售中的套餐不能删（status=ENABLE 抛 DeletionNotAllowedException(SETMEAL_ON_SALE)）；
 *   2. 删套餐要连 setmeal_drink 关联一起删（否则残留脏关联，套餐 id 复用后会"张冠李戴"）；
 *   3. 整体要 @Transactional（两表删除原子性）。
 */
@Slf4j
@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDrinkMapper setmealDrinkMapper;

    /**
     * TODO 管理端：分页查询套餐
     * 实现步骤提示：
     *   1. PageHelper.startPage(page, pageSize)；
     *   2. setmealMapper.pageQuery(dto)（需在 Mapper 补 SQL：动态过滤 name/categoryId/status，
     *      联 category 表带出 categoryName，参照 DrinkMapper.pageQuery 的 <script> 写法）；
     *   3. return new PageResult(page.getTotal(), page.getResult())。
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        // TODO 阶段三动手：先补 SetmealMapper.pageQuery 的 SQL，再放开下面三行
        log.warn("套餐分页查询尚未实现（SetmealMapper.pageQuery 未绑定 SQL）");
        return null;
    }

    /**
     * TODO 管理端：新增套餐（含套餐-饮品关联）
     * 实现步骤提示（照课程 SetmealServiceImpl.save）：
     *   1. SetmealVO → Setmeal 实体（BeanUtils），@Transactional 开事务；
     *   2. setmealMapper.insert(setmeal)（需补 SQL：@AutoFill(INSERT) + useGeneratedKeys 回填 id）；
     *   3. 遍历 setmealVO.getSetmealDrinkes() 回填 setmealId；
     *   4. setmealDrinkMapper.insertBatch(...)（需补 <foreach> 批量插 SQL）。
     */
    @Transactional
    @Override
    public void save(SetmealVO setmealVO) {
        // TODO 阶段三动手
        log.warn("新增套餐尚未实现（需先补 SetmealMapper.insert / SetmealDrinkMapper.insertBatch 的 SQL）");
    }

    /**
     * TODO 管理端：批量删除套餐
     * 【删除校验规则（动手实现时务必带上）】
     *   1. 逐个校验状态：起售中的套餐不能删 → throw new DeletionNotAllowedException(SETMEAL_ON_SALE)
     *      （照课程：ids.forEach 里 setmealMapper.getById(id).getStatus()==ENABLE 即拒绝，
     *       注意 Integer 比较要用 equals）；
     *   2. 校验通过后：setmealMapper.deleteBatch(ids) 删主表；
     *   3. setmealDrinkMapper.deleteBatch(ids) 删套餐-饮品关联（两个 Mapper 方法都需先补 SQL）；
     *   4. 方法加 @Transactional：两表删除要么全成要么全不成。
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        // TODO 阶段三动手（校验规则见方法注释）
        log.warn("批量删除套餐尚未实现（需先补 SetmealMapper.deleteBatch / SetmealDrinkMapper.deleteBatch 的 SQL）");
    }

    /**
     * TODO 管理端：根据 id 查询套餐（含套餐饮品明细）
     * 实现步骤提示（照课程 SetmealServiceImpl.getById）：
     *   1. setmealMapper.getById(id) 查套餐（已绑 SQL，可直接用）；
     *   2. setmealDrinkMapper.getBySetmealId(id) 查关联饮品（已绑 SQL）；
     *   3. SetmealVO 承接：BeanUtils.copyProperties(setmeal, vo) + vo.setSetmealDrinkes(drinkes)。
     */
    @Override
    public SetmealVO getById(Long id) {
        // 这一步不依赖未绑 SQL 的 Mapper，可以直接实现（编辑页回显的套餐主信息）
        Setmeal setmeal = setmealMapper.getById(id);
        if (setmeal == null) {
            return null;
        }
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        // TODO 阶段三动手：套餐-饮品明细本可一并返回（getBySetmealId 已绑 SQL），
        // 因 save/update 尚未实现、这里返回明细意义有限，动手完成后补一行：
        // setmealVO.setSetmealDrinkes(setmealDrinkMapper.getBySetmealId(id));
        return setmealVO;
    }

    /**
     * TODO 管理端：修改套餐
     * 实现步骤提示（照课程 SetmealServiceImpl.update，"先删后插"策略）：
     *   1. SetmealDTO → Setmeal（BeanUtils），setmealMapper.update(setmeal)（需补动态 set SQL）；
     *   2. setmealDrinkMapper.deleteBySetmealId(id) 删旧关联（已绑 SQL）；
     *   3. 遍历 dto.getSetmealDrinkes() 回填 setmealId → insertBatch 重插（需补批量插 SQL）；
     *   4. @Transactional。
     */
    @Transactional
    @Override
    public void update(SetmealDTO setmealDTO) {
        // TODO 阶段三动手
        log.warn("修改套餐尚未实现（需先补 SetmealMapper.update 的 SQL）");
    }

    /**
     * TODO 管理端：起售/停售套餐
     * 实现步骤提示（照课程 SetmealServiceImpl.startOrStop，【校验顺序不能反】）：
     *   1. 先查套餐内饮品：setmealDrinkMapper.getBySetmealId(id) → 逐个 drinkMapper.getById(drinkId)；
     *   2. 只要有任意一款饮品是停售(status=DISABLE) →
     *      throw new SetmealEnableFailedException(SETMEAL_ENABLE_FAILED)（套餐不能单独起售，
     *      否则 C 端会卖出"配料缺席"的套餐）；
     *   3. 校验通过：Setmeal.builder().id(id).status(status).build() → setmealMapper.update(...)
     *      （复用动态 set，需先补 update SQL）。
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        // TODO 阶段三动手（起售前校验套餐内饮品全部启售，见方法注释）
        log.warn("套餐起售/停售尚未实现（需先补 SetmealMapper.update 的 SQL）");
    }

    /**
     * C 端：按分类查询起售套餐列表（已实现）
     */
    @Override
    public List<Setmeal> getByCategoryId(Setmeal setmeal) {
        // Controller 已把 categoryId + status(ENABLE) 装进参数，Mapper 动态 where 过滤
        return setmealMapper.list(setmeal);
    }

    /**
     * C 端：查询套餐内饮品明细（已实现，联 drink 表带出图片与描述）
     */
    @Override
    public List<DrinkItemVO> getDrinkItemById(Long id) {
        // 空套餐返回空列表而不是 null（前端遍历不判空也不会崩——防御式返回值）
        List<DrinkItemVO> list = setmealMapper.getDrinkItemById(id);
        return list == null ? Collections.emptyList() : list;
    }
}
