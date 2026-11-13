package com.shanyue.exception;

/**
 * 场景：文件上传失败（CommonController.upload）。
 * 例如：上传目录不存在且创建失败、文件读写 IO 异常、文件名非法等。
 * 注意与 MessageConstant.UPLOAD_FAILED 常量配合使用：
 *   throw new FileUploadException(MessageConstant.UPLOAD_FAILED);
 */
public class FileUploadException extends BaseException {

    public FileUploadException() {
    }

    public FileUploadException(String msg) {
        super(msg);
    }
}
