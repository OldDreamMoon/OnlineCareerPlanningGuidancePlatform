package com.bishe.server.profile.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.storage.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业 Logo 对象存储服务。
 */
@Service
public class EnterpriseLogoStorageService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseLogoStorageService.class);
    private static final long MAX_SOURCE_FILE_SIZE_BYTES = 6L * 1024L * 1024L;
    private static final int MIN_IMAGE_EDGE = 64;
    private static final int TARGET_MAX_BYTES = 380 * 1024;
    private static final int[] OUTPUT_SIZE_CANDIDATES = {512, 448, 384};
    private static final float[] JPEG_QUALITY_CANDIDATES = {0.9f, 0.84f, 0.78f, 0.72f};
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String MEMORY_BUCKET = "enterprise-logo-memory-store";

    private final MinioStorageProperties minioStorageProperties;
    private final Map<String, StoredLogoContent> memoryStore = new ConcurrentHashMap<>();
    private volatile boolean bucketEnsured;

    public EnterpriseLogoStorageService(MinioStorageProperties minioStorageProperties) {
        this.minioStorageProperties = minioStorageProperties;
    }

    @PostConstruct
    void logStorageMode() {
        if (minioStorageProperties.isEnabled()) {
            log.info("enterprise logo storage initialized with MinIO bucket={}", minioStorageProperties.getBucket());
        } else {
            log.warn("enterprise logo storage fallback to in-memory mode because MinIO is disabled");
        }
    }

    public StoredLogo upload(long enterpriseUserId, MultipartFile file) {
        validateFile(file);
        NormalizedLogo normalizedLogo = normalizeLogo(file);
        Instant uploadedAt = Instant.now();
        String bucket = resolveBucketName();
        String objectKey = buildObjectKey(enterpriseUserId, uploadedAt, normalizedLogo.fileExtension());

        if (minioStorageProperties.isEnabled()) {
            // 生产路径写 MinIO，bucket 首次访问时惰性确认。
            MinioClient minioClient = buildMinioClient();
            ensureBucketExists(minioClient);
            try {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(new ByteArrayInputStream(normalizedLogo.bytes()), normalizedLogo.sizeBytes(), -1)
                                .contentType(normalizedLogo.contentType())
                                .build()
                );
            } catch (Exception ex) {
                throw new ApiException("BIZ-1001", "企业 Logo 上传失败，请确认 MinIO 对象存储服务可用", HttpStatus.BAD_GATEWAY);
            }
        } else {
            // 本地未启 MinIO 时保留内存回退，便于资料页和任务页联调。
            memoryStore.put(buildMemoryStoreKey(bucket, objectKey), new StoredLogoContent(normalizedLogo.bytes(), normalizedLogo.contentType()));
        }

        return new StoredLogo(bucket, objectKey, normalizedLogo.contentType(), normalizedLogo.sizeBytes(), uploadedAt.toString());
    }

    public StoredLogoContent read(String bucket, String objectKey, String contentType) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            throw new ApiException("BIZ-1002", "logo not found", HttpStatus.NOT_FOUND);
        }

        if (minioStorageProperties.isEnabled()) {
            MinioClient minioClient = buildMinioClient();
            ensureBucketExists(minioClient);
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            )) {
                return new StoredLogoContent(inputStream.readAllBytes(), resolveStoredContentType(contentType, objectKey));
            } catch (Exception ex) {
                throw new ApiException("BIZ-1002", "logo not found", HttpStatus.NOT_FOUND);
            }
        }

        StoredLogoContent content = memoryStore.get(buildMemoryStoreKey(bucket, objectKey));
        if (content == null) {
            throw new ApiException("BIZ-1002", "logo not found", HttpStatus.NOT_FOUND);
        }
        return content;
    }

    public boolean deleteQuietly(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            return false;
        }

        if (minioStorageProperties.isEnabled()) {
            try {
                MinioClient minioClient = buildMinioClient();
                ensureBucketExists(minioClient);
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .build()
                );
                return true;
            } catch (Exception ex) {
                log.warn("删除企业 Logo 对象失败 bucket={} objectKey={}", bucket, objectKey, ex);
                return false;
            }
        }

        return memoryStore.remove(buildMemoryStoreKey(bucket, objectKey)) != null;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("BIZ-1001", "请先选择企业 Logo 图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_SOURCE_FILE_SIZE_BYTES) {
            throw new ApiException("BIZ-1001", "企业 Logo 原图过大，请控制在 6 MB 以内", HttpStatus.BAD_REQUEST);
        }
    }

    private NormalizedLogo normalizeLogo(MultipartFile file) {
        BufferedImage sourceImage;
        try (InputStream inputStream = file.getInputStream()) {
            sourceImage = ImageIO.read(inputStream);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "企业 Logo 图片读取失败，请重新上传 JPG 或 PNG", HttpStatus.BAD_REQUEST);
        }

        if (sourceImage == null) {
            throw new ApiException("BIZ-1001", "企业 Logo 仅支持 JPG 或 PNG 图片", HttpStatus.BAD_REQUEST);
        }
        if (sourceImage.getWidth() < MIN_IMAGE_EDGE || sourceImage.getHeight() < MIN_IMAGE_EDGE) {
            throw new ApiException("BIZ-1001", "企业 Logo 尺寸太小，请选择至少 64 x 64 的图片", HttpStatus.BAD_REQUEST);
        }

        boolean preserveAlpha = shouldPreserveAlpha(file, sourceImage);
        String outputContentType = preserveAlpha ? "image/png" : "image/jpeg";
        String outputFileExtension = preserveAlpha ? "png" : "jpg";
        byte[] encodedBytes = null;
        // 先降尺寸再降 JPEG 质量，尽量保留 Logo 清晰度并控制对象大小。
        for (int outputSize : OUTPUT_SIZE_CANDIDATES) {
            BufferedImage resizedImage = createCoverSquareImage(sourceImage, outputSize, preserveAlpha);
            if (preserveAlpha) {
                encodedBytes = encodeAsPng(resizedImage);
                if (encodedBytes.length <= TARGET_MAX_BYTES) {
                    return new NormalizedLogo(encodedBytes, outputContentType, outputFileExtension);
                }
                continue;
            }

            for (float quality : JPEG_QUALITY_CANDIDATES) {
                encodedBytes = encodeAsJpeg(resizedImage, quality);
                if (encodedBytes.length <= TARGET_MAX_BYTES) {
                    return new NormalizedLogo(encodedBytes, outputContentType, outputFileExtension);
                }
            }
        }

        if (encodedBytes == null) {
            BufferedImage fallbackImage = createCoverSquareImage(sourceImage, 384, preserveAlpha);
            encodedBytes = preserveAlpha ? encodeAsPng(fallbackImage) : encodeAsJpeg(fallbackImage, 0.7f);
        }
        return new NormalizedLogo(encodedBytes, outputContentType, outputFileExtension);
    }

    private boolean shouldPreserveAlpha(MultipartFile file, BufferedImage sourceImage) {
        if (sourceImage.getColorModel() != null && sourceImage.getColorModel().hasAlpha()) {
            return true;
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).contains("png")) {
            return true;
        }

        String originalFilename = file.getOriginalFilename();
        return StringUtils.hasText(originalFilename)
                && originalFilename.toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private BufferedImage createCoverSquareImage(BufferedImage sourceImage, int outputSize, boolean preserveAlpha) {
        BufferedImage canvas = new BufferedImage(
                outputSize,
                outputSize,
                preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // cover 裁剪成正方形，任务卡片和企业资料页可以使用稳定比例展示。
            double scale = Math.max(
                    (double) outputSize / sourceImage.getWidth(),
                    (double) outputSize / sourceImage.getHeight()
            );
            int drawWidth = Math.max(outputSize, (int) Math.ceil(sourceImage.getWidth() * scale));
            int drawHeight = Math.max(outputSize, (int) Math.ceil(sourceImage.getHeight() * scale));
            int drawX = (outputSize - drawWidth) / 2;
            int drawY = (outputSize - drawHeight) / 2;
            graphics.drawImage(sourceImage, drawX, drawY, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private byte[] encodeAsPng(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "企业 Logo 压缩失败，请重新上传图片", HttpStatus.BAD_REQUEST);
        }
    }

    private byte[] encodeAsJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("no jpeg writer available");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "企业 Logo 压缩失败，请重新上传图片", HttpStatus.BAD_REQUEST);
        } finally {
            writer.dispose();
        }
    }

    private String buildObjectKey(long enterpriseUserId, Instant uploadedAt, String fileExtension) {
        return "logos/enterprise/%s/u%s/logo-%s-%s.%s".formatted(
                DATE_PATH_FORMATTER.format(uploadedAt),
                enterpriseUserId,
                TIMESTAMP_FORMATTER.format(uploadedAt),
                UUID.randomUUID().toString().replace("-", ""),
                fileExtension
        );
    }

    private String resolveStoredContentType(String contentType, String objectKey) {
        if (StringUtils.hasText(contentType)) {
            return contentType.trim();
        }
        if (StringUtils.hasText(objectKey) && objectKey.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private String resolveBucketName() {
        if (!minioStorageProperties.isEnabled()) {
            return MEMORY_BUCKET;
        }

        String bucket = minioStorageProperties.getBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new ApiException("BIZ-1001", "MinIO bucket 未配置，无法保存企业 Logo", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return bucket.trim();
    }

    private String buildMemoryStoreKey(String bucket, String objectKey) {
        return bucket + "::" + objectKey;
    }

    private MinioClient buildMinioClient() {
        return MinioClient.builder()
                .endpoint(normalizeEndpoint(minioStorageProperties.getEndpoint()))
                .credentials(minioStorageProperties.getAccessKey(), minioStorageProperties.getSecretKey())
                .build();
    }

    private void ensureBucketExists(MinioClient minioClient) {
        if (bucketEnsured) {
            return;
        }

        synchronized (this) {
            // 双重检查避免并发上传时重复请求 MinIO 创建 bucket。
            if (bucketEnsured) {
                return;
            }

            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(resolveBucketName())
                                .build()
                );
                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(resolveBucketName())
                                    .build()
                    );
                }
                bucketEnsured = true;
            } catch (Exception ex) {
                throw new ApiException("BIZ-1001", "无法访问 MinIO bucket，请确认对象存储服务可用", HttpStatus.BAD_GATEWAY);
            }
        }
    }

    private String normalizeEndpoint(String endpoint) {
        String trimmed = endpoint == null ? "" : endpoint.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new ApiException("BIZ-1001", "MinIO endpoint 未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "http://" + trimmed;
    }

    public record StoredLogo(
            String bucket,
            String objectKey,
            String contentType,
            long sizeBytes,
            String uploadedAt
    ) {
    }

    public record StoredLogoContent(
            byte[] bytes,
            String contentType
    ) {
    }

    private record NormalizedLogo(
            byte[] bytes,
            String contentType,
            String fileExtension
    ) {
        long sizeBytes() {
            return bytes.length;
        }
    }
}
