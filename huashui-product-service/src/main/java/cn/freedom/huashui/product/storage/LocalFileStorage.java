package cn.freedom.huashui.product.storage;

import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.product.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现。
 *
 * <p>存储路径形如 {@code {uploadDir}/2026/09/8f3a....jpg}，按年月分目录，
 * 避免所有文件堆在一个目录里（单个目录文件数过多时，文件系统的目录检索会明显变慢）。
 *
 * <p>文件名用 UUID 重命名，既避免同名覆盖，也避免把用户的原始文件名暴露到公网。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final FileStorageProperties properties;

    @Override
    public String store(InputStream inputStream, String originalFilename) {
        String extension = extractExtension(originalFilename);
        if (!isAllowed(extension)) {
            throw new BizException(ResultCode.IMAGE_TYPE_NOT_ALLOWED);
        }

        String datePath = LocalDate.now().format(DATE_PATH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path directory = Paths.get(properties.getUploadDir(), datePath);

        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(filename);
            try (inputStream) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return properties.getUrlPrefix() + "/" + datePath + "/" + filename;
        } catch (IOException e) {
            log.error("图片保存失败 | dir={} | filename={}", directory, filename, e);
            throw new BizException(ResultCode.IMAGE_STORE_FAILED);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowed(String extension) {
        return Arrays.asList(properties.getAllowedExtensions()).contains(extension);
    }
}
