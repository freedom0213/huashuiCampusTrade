package cn.freedom.huashui.product.storage;

import java.io.InputStream;

/**
 * 文件存储抽象。
 *
 * <p><b>为什么要抽这一层：</b>业务代码只关心「把文件交给你，还我一个能用在 img src 里的路径」，
 * 不关心文件到底落在本地磁盘、MinIO 还是阿里云 OSS。
 * 有了这层接口，将来换成 OSS 只需要新增一个实现类并改配置，
 * Service 与 Controller 一行都不用动。
 *
 * @author freedom0213
 */
public interface FileStorage {

    /**
     * 保存文件。
     *
     * @param inputStream      文件流，由调用方负责关闭
     * @param originalFilename 原始文件名，仅用于取扩展名与展示
     * @return 可直接用于前端 img src 的访问路径
     */
    String store(InputStream inputStream, String originalFilename);
}
