package com.shanyue.enumeration;

/**
 * 【本类知识点：枚举替代魔法值】
 * 数据库操作类型只有"插入/更新"两种，被 @AutoFill 注解用来告知切面该填充哪些字段：
 *   INSERT → 填 create_time + create_user + update_time + update_user
 *   UPDATE → 只填 update_time + update_user
 */
public enum OperationType {

    /** 更新操作 */
    UPDATE,

    /** 插入操作 */
    INSERT
}
