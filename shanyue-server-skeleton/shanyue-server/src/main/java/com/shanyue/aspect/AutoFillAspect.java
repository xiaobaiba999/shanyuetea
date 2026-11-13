package com.shanyue.aspect;

import com.shanyue.annotation.AutoFill;
import com.shanyue.constant.AutoFillConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 【本类知识点：AOP 切面 —— 公共字段自动填充（课程同款模式）】
 *
 * 一、要解决的问题：每张表都有 create_time/update_time/create_user/update_user，
 *    如果每个 Service 方法都手动 set 四个字段，重复代码满天飞。切面把这件事"横切"出来统一做。
 *
 * 二、切点表达式逐词拆解：
 *    execution(* com.shanyue.mapper.*.*(..)) && @annotation(com.shanyue.annotation.AutoFill)
 *    - execution(返回类型 包名.类名.方法名(参数))：
 *        *          → 任意返回类型
 *        com.shanyue.mapper.*.*(..) → com.shanyue.mapper 包下任意接口/类的任意方法，参数任意(..)
 *    - &&：同时满足
 *    - @annotation(注解全类名)：方法上标注了 @AutoFill 注解
 *    两个条件取交集 → "mapper 里带 @AutoFill 的方法"在执行前会被本切面拦截。
 *
 * 三、执行流程（@Before 前置通知）：
 *    拿到方法签名 → 读注解判断 INSERT/UPDATE → 拿第一个参数（实体）→
 *    反射找到 setCreateTime/setCreateUser/... 方法 → 填充"当前时间 + 当前登录人 id"。
 *
 * 四、为什么用反射？被拦截的实体类型各不相同（Employee/Category/Drink...），
 *    编译期无法确定具体类型，只能运行期按方法名字符串找 setter（名字来自 AutoFillConstant）。
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切点：com.shanyue.mapper 包下任意类的任意方法，且方法上标注了 @AutoFill 注解
     */
    @Pointcut("execution(* com.shanyue.mapper.*.*(..)) && @annotation(com.shanyue.annotation.AutoFill)")
    public void autoFillPointcut() {
    }

    /**
     * 前置通知：在目标 Mapper 方法执行【之前】填充公共字段（SQL 里要用，所以必须在此之前）
     */
    @Before("autoFillPointcut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("公共字段自动填充开始...");

        // 获取方法签名对象（可拿到方法名、参数类型、注解等元信息）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取方法上的 @AutoFill 注解对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        // 获取操作类型（INSERT / UPDATE）
        OperationType operationType = autoFill.value();

        // 获取被拦截方法的实参列表
        Object[] args = joinPoint.getArgs();
        // 没有参数就无法填充，直接返回
        if (args == null || args.length == 0) {
            return;
        }
        // 约定：实体对象作为第一个参数（本工程所有 Mapper 均遵守该约定）
        Object entity = args[0];

        // 准备填充的值：当前时间 + 当前登录人 id（来自拦截器放入的 ThreadLocal）
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        if (operationType == OperationType.INSERT) {
            // 插入：填 4 个字段
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 通过反射调用 setter 赋值
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                throw new RuntimeException("公共字段自动填充失败", e);
            }
        } else if (operationType == OperationType.UPDATE) {
            // 更新：只填 2 个字段（创建时间/创建人是历史的，不能动）
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setUpdateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
            } catch (Exception e) {
                throw new RuntimeException("公共字段自动填充失败", e);
            }
        }
    }
}
