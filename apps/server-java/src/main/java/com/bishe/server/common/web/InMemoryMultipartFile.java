package com.bishe.server.common.web;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 基于内存字节数组的 MultipartFile，供异步任务重放上传文件时复用。
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
        this.name = name == null || name.isBlank() ? "file" : name.trim();
        this.originalFilename = originalFilename == null ? "" : originalFilename.trim();
        this.contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        if (dest == null) {
            throw new IOException("destination file required");
        }
        Files.write(dest.toPath(), bytes);
    }
}
