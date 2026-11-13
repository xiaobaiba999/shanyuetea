package com.shanyue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 【本类知识点：本地磁盘存储配置（替代 OSS）】
 * 课程原版把图片传到阿里云 OSS（要花钱、要配 AK/SK），山月骨架改成本地磁盘存储：
 *   1. 文件写到 shanyue.local-storage.path 指定的目录（默认 D:/shanyue/upload，见 application-dev.yml）；
 *   2. WebMvcConfiguration 把 /upload/** 映射到该目录，浏览器即可通过
 *      http://localhost:8080/upload/xxx.png 直接访问；
 *   3. 好处：零成本、离线可跑；代价：图片跟随应用所在机器，不能水平扩容——
 *      简历里可以写"预留 OSS/对象存储升级位"（课程原版的 AliOssProperties/AliOssUtil 即对应升级位）。
 *
 * 【⚠ 大坑实录：prefix 不允许驼峰（本项目真实踩过）】
 * prefix 最初写成了 "shanyue.localStorage"，启动直接炸：
 *   Configuration property name 'shanyue.localStorage' is not valid:
 *       Invalid characters: 'S'
 *   Reason: Canonical names should be kebab-case ('-' separated), lowercase
 *           alpha-numeric characters and must start with a letter
 * 原因：Spring Boot 规定 @ConfigurationProperties 的 prefix 必须是"规范名"——
 *   只能小写字母、数字、中划线（kebab-case 烤肉串命名），不能有大写/下划线。
 *   "localStorage" 里的 S 是大写 → 被校验器点名 Invalid characters: 'S'。
 * 修复：prefix 改为 "shanyue.local-storage"（驼峰 → 中划线）。
 *
 * 【三条绑定规则（记这一段就够了）】
 *   ① prefix：必须全小写 + 中划线（shanyue.local-storage ✔  shanyue.localStorage ✗）；
 *   ② yml 里的 key：松散绑定，local-storage / localStorage / local_storage 都能认，
 *      但团队统一写中划线最不容易困惑；
 *   ③ Java 字段：正常驼峰（path、localStoragePath 都行），绑定器自动对上。
 * 口诀：前缀必须小写带横线，yml 写法宽松，Java 字段用驼峰。
 *
 * prefix = "shanyue.local-storage" + 字段 path → yml 完整 key：shanyue.local-storage.path
 */
@Component
@ConfigurationProperties(prefix = "shanyue.local-storage")
@Data
public class LocalStorageProperties {

    /** 本地图片存储根目录（末尾带不带 / 都可以，WebMvcConfiguration 会兜底补齐） */
    private String path;
}
