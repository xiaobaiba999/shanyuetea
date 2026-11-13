package com.shanyue.controller.admin;

import com.shanyue.properties.LocalStorageProperties;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 【管理端：通用接口 —— 文件上传（完整本地版，替代课程的 OSS）】
 * └ POST /admin/common/upload  文件上传（multipart/form-data，字段名 file）
 *
 * 【本类知识点：本地上传 vs 课程 OSS 上传（对照学习）】
 * 课程原版：file.getBytes() → AliOssUtil.upload() 传到阿里云 OSS，返回
 *   https://bucket.endpoint/xxx.png 的"公网永久 URL"，任何设备可直接访问。
 * 山月骨架：文件写到服务器本地磁盘（shanyue.local-storage.path 配置的目录），
 *   返回相对路径 "/upload/xxx.png" —— 它不是完整 URL！前端要自己拼接 baseUrl：
 *   完整地址 = http://localhost:8080 + /upload/xxx.png。
 *   为什么这样也能访问？WebMvcConfiguration.addResourceHandlers 把 /upload/** 映射到了
 *   该磁盘目录，Spring 把它当静态资源直接吐给浏览器（等价于 OSS 的公开读）。
 *
 * 【与 OSS 的差异/取舍（面试可讲）】
 *   ✔ 本地：零成本、离线可跑、无 AK/SK 泄露风险；
 *   ✘ 本地：不能水平扩容（多台机器磁盘不共享）、重新部署要迁移文件目录；
 *   升级路径：把本类换成调 OSS SDK 即可，Controller 签名与返回值结构都不用变。
 *
 * 【防重名：UUID 文件名】两个用户都传 avatar.png 会互相覆盖 ——
 *   用 UUID.randomUUID() 重命名并保留原后缀（后缀决定浏览器怎么解析文件类型）。
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

    @Autowired
    private LocalStorageProperties localStorageProperties;

    /**
     * 文件上传（本地磁盘版）
     *
     * @param file Spring MVC 自动把 multipart 里的文件封装成 MultipartFile（字段名必须叫 file）
     * @return Result{data: "/upload/xxxx.png"} —— 相对路径，前端拼接 baseUrl 后展示/存库
     */
    @PostMapping("/upload")
    @Operation(summary = "文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：原始文件名={}", file.getOriginalFilename());

        // 1、取原始文件名，截出后缀（lastIndexOf 找最后一个点；jpg/png/webp 都适用）
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error("文件名非法，缺少扩展名");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

        // 2、UUID 重命名防覆盖（保留原后缀）
        String objectName = UUID.randomUUID() + extension;

        // 3、目标目录：来自配置 shanyue.local-storage.path；不存在则递归创建（mkdirs 支持多级）
        File dir = new File(localStorageProperties.getPath());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 4、落盘：transferTo 把上传的临时文件写到目标位置（临时文件由 Tomcat 自动清理）
        try {
            file.transferTo(new File(dir, objectName));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败：" + e.getMessage());
        }

        // 5、返回相对路径 /upload/文件名 —— 前端拼接 baseUrl（如 http://localhost:8080）
        //    得到完整可访问地址；存库时建议存这个相对路径（换域名/端口不用刷库）
        String filePath = "/upload/" + objectName;
        log.info("文件上传成功：{}", filePath);
        return Result.success(filePath);
    }
}
