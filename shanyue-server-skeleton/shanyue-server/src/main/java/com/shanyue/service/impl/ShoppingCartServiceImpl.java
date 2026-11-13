package com.shanyue.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.shanyue.constant.MessageConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.dto.ShoppingCartDTO;
import com.shanyue.entity.Drink;
import com.shanyue.entity.Setmeal;
import com.shanyue.entity.ShoppingCart;
import com.shanyue.entity.SpecOption;
import com.shanyue.exception.ShoppingCartBusinessException;
import com.shanyue.mapper.DrinkMapper;
import com.shanyue.mapper.SetmealMapper;
import com.shanyue.mapper.ShoppingCartMapper;
import com.shanyue.mapper.SpecOptionMapper;
import com.shanyue.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 【本类知识点：C 端购物车（完整范本）—— "同款同规格合并"与服务端重算金额】
 *
 * 一、购物车的行合并规则（山月 v2.0 规格树的落地处）：
 *   "同一款饮品 + 相同规格快照" = 同一行 → 再次加购只 number+1；
 *   规格不同（杯型/糖度差任意一个选项）= 两行，互不合并。
 *   判定键 = user_id + drink_id + spec_selected（三列全等）。
 *   spec_selected 是前端规格弹窗生成的 JSON 数组字符串，如：
 *     [{"groupId":1,"groupName":"杯型","optionId":2,"optionName":"大杯","priceDelta":3}]
 *   注意：无规格商品传 null/空串，查询时走 SQL 的 "spec_selected is null" 分支
 *   （SQL 里 = NULL 永远不成立，所以 Mapper 动态 SQL 专门做了双分支）。
 *
 * 二、金额一律"服务端重算"（本类最重要的一条安全规则，面试高频）：
 *   单价 = drink.price（基础价，读库） + Σ(所选项 price_delta)（读库）。
 *   前端传来的任何金额字段都直接忽略 —— 抓包改包也改不了价格；
 *   加价只认数据库真值：把快照里的 optionId 与"该饮品的合法选项"求交集，
 *   传别人家饮品的选项 id / 伪造 priceDelta 都会被拒绝或忽略。
 *
 * 三、与课程的对照：课程按 drinkId+drinkFlavor 合并（口味是字符串），
 *   山月把口味升级为结构化规格树后，合并键换成 specSelected 快照，
 *   drinkFlavor 字段保留仅作课程对照。
 */
@Slf4j
@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DrinkMapper drinkMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SpecOptionMapper specOptionMapper;

    /**
     * 加入购物车（先查后插/先查后加）
     */
    @Override
    public void add(ShoppingCartDTO shoppingCartDTO) {
        // 用户身份取 ThreadLocal（拦截器解析 token 后放入），绝不取前端参数，防替别人加购
        Long userId = BaseContext.getCurrentId();

        // 1、构造查询条件：同用户 + 同商品 + 同规格快照（specSelected 规范化：空串当 null 处理）
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        shoppingCart.setDrinkId(shoppingCartDTO.getDrinkId());
        shoppingCart.setSetmealId(shoppingCartDTO.getSetmealId());
        shoppingCart.setSpecSelected(normalizeSpec(shoppingCartDTO.getSpecSelected()));
        // 课程对照：课程还把 drinkFlavor 一起作为合并条件；山月的合并键是 specSelected，
        // drinkFlavor 仅保留字段兼容课程写法（Mapper 的 where 未按它过滤）
        shoppingCart.setDrinkFlavor(shoppingCartDTO.getDrinkFlavor());

        // 2、先查购物车中是否已存在"同款同规格"的行（字符串等值比较即 equals 语义）
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        if (list != null && !list.isEmpty()) {
            // 3a、已存在相同规格的行 → 只把数量 +1（金额不变，单价没变）
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cart);
            return;
        }

        // 3b、不存在 → 新增一行：名称/图片/单价都从数据库读，金额由服务端重算
        Long drinkId = shoppingCartDTO.getDrinkId();
        if (drinkId != null) {
            // ---- 加购的是饮品：查库取名称/图片，单价 = 基础价 + Σ加价 ----
            Drink drink = drinkMapper.getById(drinkId);
            if (drink == null) {
                // 饮品不存在（或已删除），拒绝加购
                throw new ShoppingCartBusinessException("饮品不存在，无法加入购物车");
            }
            shoppingCart.setName(drink.getName());
            shoppingCart.setImage(drink.getImage());
            /* ---------- 服务端重算金额（防篡改核心） ---------- */
            shoppingCart.setAmount(calculateDrinkAmount(drink.getPrice(), drinkId, shoppingCart.getSpecSelected()));
        } else {
            // ---- 加购的是套餐：套餐价即单价，无规格加价 ----
            Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            if (setmeal == null) {
                throw new ShoppingCartBusinessException("套餐不存在，无法加入购物车");
            }
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setAmount(setmeal.getPrice());
        }

        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());
        shoppingCartMapper.insert(shoppingCart);
        log.info("购物车新增一行：userId={}，name={}，amount={}", userId, shoppingCart.getName(), shoppingCart.getAmount());
    }

    /**
     * 查询当前用户购物车列表（"购物车"角标与页面列表）
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        // 用 listByUserId（只按 userId 查全部行）：不能复用 list()——
        // list() 在 specSelected 为 null 时会拼 "spec_selected is null"（精确匹配语义），
        // 会漏掉所有带规格的行，查列表必须查全量
        return shoppingCartMapper.listByUserId(BaseContext.getCurrentId());
    }

    /**
     * 清空当前用户购物车（下单成功后 / 手动清空按钮）
     */
    @Override
    public void cleanShoppingCart() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    /**
     * 减少购物车中一个商品（数量 -1；减到 0 删整行）
     * 与 add 的查询条件完全一致：同一行才能精准减（否则会出现"大杯减到中杯头上"的事故）
     */
    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();

        // 1、按"用户 + 商品 + 规格快照"定位购物车行（与 add 的先查同款逻辑一致）
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        shoppingCart.setDrinkId(shoppingCartDTO.getDrinkId());
        shoppingCart.setSetmealId(shoppingCartDTO.getSetmealId());
        shoppingCart.setSpecSelected(normalizeSpec(shoppingCartDTO.getSpecSelected()));
        shoppingCart.setDrinkFlavor(shoppingCartDTO.getDrinkFlavor());

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        // 2、没查到（可能已被清空/从未加过）——静默返回即可，前端角标以 list 接口为准
        if (list == null || list.isEmpty()) {
            log.info("sub 未命中购物车行，userId={}，drinkId={}，setmealId={}",
                    userId, shoppingCartDTO.getDrinkId(), shoppingCartDTO.getSetmealId());
            return;
        }

        // 3、查到了：数量为 1 → 删整行；否则数量 -1
        //    按行主键 id 删除（课程原版按 drinkId 全删会误伤其它规格的行，山月已修正）
        ShoppingCart cart = list.get(0);
        if (cart.getNumber() == 1) {
            shoppingCartMapper.deleteById(cart.getId());
        } else {
            cart.setNumber(cart.getNumber() - 1);
            shoppingCartMapper.updateNumberById(cart);
        }
    }

    /**
     * 【服务端重算金额】饮品单价 = 基础价 + Σ(所选规格选项的加价)
     *
     * 计算步骤（全部以数据库为唯一真值）：
     *   1. 解析前端传来的规格快照 JSON，取出 optionId 集合；
     *   2. 查出"该饮品的全部合法选项"（spec_option JOIN spec_group）；
     *   3. 逐个在合法选项里匹配 optionId：
     *        - 匹配不到 → 说明前端传了别人家饮品的选项 id（越权/篡改），直接抛业务异常；
     *        - 匹配到 → 累加该选项的 priceDelta（数据库里的加价，忽略前端传的 priceDelta 字段）；
     *   4. 基础价 + 加价合计 = 最终单价（BigDecimal 逐项 add，金额禁止 double）。
     *
     * @param basePrice    饮品基础价（读库）
     * @param drinkId       饮品 id（限定合法选项范围）
     * @param specSelected 规格快照 JSON 数组字符串（null/空 = 无规格，加价为 0）
     * @return 最终单价
     */
    private BigDecimal calculateDrinkAmount(BigDecimal basePrice, Long drinkId, String specSelected) {
        // 无规格快照：单价就是基础价
        if (!StringUtils.hasText(specSelected)) {
            return basePrice;
        }

        // 1、解析快照 JSON：[{optionId:2,...}, ...] → 取 optionId 集合
        JSONArray array;
        try {
            array = JSON.parseArray(specSelected);
        } catch (Exception e) {
            // 快照格式被篡改/非法，拒绝加购
            throw new ShoppingCartBusinessException("规格数据格式错误，无法加入购物车");
        }

        // 2、查出该饮品的合法选项并按 id 建索引（一次查询，避免循环查库）
        List<SpecOption> legalOptions = specOptionMapper.getByDrinkId(drinkId);
        Map<Long, SpecOption> legalMap = legalOptions.stream()
                .collect(Collectors.toMap(SpecOption::getId, Function.identity()));

        // 3、逐项校验并累加加价
        BigDecimal deltaTotal = BigDecimal.ZERO;
        for (int i = 0; i < array.size(); i++) {
            Long optionId = array.getJSONObject(i).getLong("optionId");
            SpecOption option = optionId == null ? null : legalMap.get(optionId);
            if (option == null) {
                // 选项不属于该饮品 → 视为非法请求（防越权传 id / 防伪造加价）
                throw new ShoppingCartBusinessException("包含非法规格选项，无法加入购物车");
            }
            // 金额计算用 BigDecimal.add；priceDelta 以数据库为准，不读 JSON 里的 priceDelta
            deltaTotal = deltaTotal.add(option.getPriceDelta() == null ? BigDecimal.ZERO : option.getPriceDelta());
        }

        // 4、最终单价 = 基础价 + 加价合计
        return basePrice.add(deltaTotal);
    }

    /**
     * 规格快照规范化：null / 空串 / 空白 统一转 null
     * 目的：与 Mapper 的动态 SQL 对齐（specSelected==null 走 is null 分支），
     * 否则前端传 "" 会去匹配 spec_selected = '' 永远查不到同一行。
     * 教学备查：若把行匹配改为"先查全部再内存比对"，比较快照请用
     * String.equals / Objects.equals（内容相等），绝不能用 ==（引用比较必错）。
     */
    private String normalizeSpec(String specSelected) {
        return StringUtils.hasText(specSelected) ? specSelected : null;
    }
}
