package com.shanyue.context;

/**
 * 【本类知识点：BaseContext 与 ThreadLocal —— 本项目最值得吃透的一处基础设施】
 *
 * 一、解决什么问题？
 *   HTTP 请求是"一次性"的：拦截器校验完 token 拿到用户 id 后，id 如何一路传给
 *   Controller → Service → Mapper（中间可能隔了很多层方法）？
 *   方案 A：给每个方法加一个 Long empId 参数 —— 侵入性太强，签名又丑又容易漏传。
 *   方案 B（本工程）：把 id 存进 ThreadLocal，同一个请求内任何代码都能直接取。
 *
 * 二、ThreadLocal 原理（面试高频）：
 *   1. 每个线程（Thread）内部持有一张 ThreadLocalMap；
 *   2. map 的 key 是 ThreadLocal 对象本身（弱引用），value 就是我们 set 进去的值；
 *   3. 所以 set/get/remove 都是"操作当前线程自己的 map"，天然线程隔离、无需加锁；
 *   4. Tomcat 采用线程池复用线程 —— 线程处理完上一个请求后可能被拿去处理下一个请求，
 *      如果不 remove，下一个请求可能读到上一个用户的 id（"串号"事故）！
 *      因此拦截器的 afterCompletion 里必须调用 BaseContext.removeCurrentId() 清理。
 *
 * 三、本工程的数据流：
 *   JwtTokenAdminInterceptor / JwtTokenUserInterceptor 解析 token 成功
 *     → BaseContext.setCurrentId(id)
 *   → Service / Aspect 里 BaseContext.getCurrentId() 取出（如 AutoFillAspect 填充 create_user）
 *   → 拦截器 afterCompletion 里 removeCurrentId() 清理，防止线程池串号。
 *
 * 四、为什么存 Long 而不是 Object？本工程只有一种"当前登录人 id"，泛型定为 Long 即可；
 *    若一个请求里需要多种上下文，可以定义一个 ContextHolder 对象来承载。
 */
public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /** 存入当前登录人 id（拦截器校验通过后调用） */
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    /** 取出当前登录人 id（Service/切面里随时可取） */
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    /** 清理（拦截器 afterCompletion 里调用，防止线程池复用导致的串号） */
    public static void removeCurrentId() {
        threadLocal.remove();
    }
}
