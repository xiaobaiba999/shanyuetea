package com.shanyue.annotation;

import com.shanyue.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【本类知识点：自定义注解 + 元注解】
 * 1. 这是一个"方法级"的自定义注解，本身没有任何逻辑，像一张"标签"贴在 Mapper 方法上；
 *    真正干活的是 AutoFillAspect：拦截带这个标签的方法，在 SQL 执行前填充公共字段。
 * 2. 元注解解释（注解上的注解）：
 *    - @Target(ElementType.METHOD)：只能贴在方法上；
 *    - @Retention(RetentionPolicy.RUNTIME)：保留到运行期，反射可读取（否则切面拿不到它）。
 * 3. value()：操作类型 INSERT/UPDATE，切面据此决定填 4 个字段还是 2 个字段。
 * 使用示例：@AutoFill(value = OperationType.UPDATE) void update(Employee employee);
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {

    /**
     * 数据库操作类型（INSERT 填 4 个公共字段，UPDATE 填 2 个）
     */
    OperationType value();
}
