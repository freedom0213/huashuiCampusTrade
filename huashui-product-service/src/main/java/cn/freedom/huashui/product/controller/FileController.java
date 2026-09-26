package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.product.config.FileStorageProperties;
import cn.freedom.huashui.product.storage.FileStorage;
import cn.freedom.huashui.product.vo.FileUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传接口。
 *
 * <p>只做「接收 + 校验 + 委托给 FileStorage」，不关心文件最终落在哪。
 * 换成 OSS 时本类不用改。
 *
 * <p>上传后的访问路径形如 {@code /uploads/2026/09/xxx.jpg}，
 * 由本服务的静态资源映射提供，并经网关的 {@code /uploads/**} 路由对外暴露。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件上传", description = "商品图片上传")
public class FileController {

    private final FileStorage fileStorage;
    private final FileStorageProperties properties;

    @PostMapping("/upload")
    @Operation(summary = "上传图片", description = "需要登录。返回可直接用于 img src 的访问路径")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.IMAGE_EMPTY);
        }
        // 框架层也会拦一次（spring.servlet.multipart.max-file-size），
        // 这里再判一次是为了给出中文提示，而不是容器抛出的英文异常
        if (file.getSize() > properties.getMaxSize()) {
            long maxMb = properties.getMaxSize() / 1024 / 1024;
            throw new BizException(ResultCode.IMAGE_TOO_LARGE, "图片大小不能超过 " + maxMb + "MB");
        }

        String url;
        try {
            url = fileStorage.store(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new BizException(ResultCode.IMAGE_STORE_FAILED);
        }

        // 转成 int 再返回：common 把 Long 全局序列化成字符串（为了雪花 ID 精度），
        // 而字节数是数量、必须是数字。图片上限 5MB，远小于 int 上限，不会溢出
        return Result.success("上传成功",
                new FileUploadVO(url, file.getOriginalFilename(), (int) file.getSize()));
    }
}
