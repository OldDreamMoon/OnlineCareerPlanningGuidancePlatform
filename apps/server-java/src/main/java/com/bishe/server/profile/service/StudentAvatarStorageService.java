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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学生头像对象存储服务。
 * 正式环境优先写入 MinIO；测试或显式关闭 MinIO 时退回进程内存，便于联调与集成测试。
 */
@Service
public class StudentAvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(StudentAvatarStorageService.class);
    private static final long MAX_SOURCE_FILE_SIZE_BYTES = 6L * 1024L * 1024L;
    private static final int MIN_IMAGE_EDGE = 120;
    private static final int TARGET_MAX_BYTES = 380 * 1024;
    private static final int[] OUTPUT_SIZE_CANDIDATES = {512, 448, 384};
    private static final float[] JPEG_QUALITY_CANDIDATES = {0.9f, 0.84f, 0.78f, 0.72f};
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String MEMORY_BUCKET = "avatar-memory-store";

    private final MinioStorageProperties minioStorageProperties;
    private final Map<String, StoredAvatarContent> memoryStore = new ConcurrentHashMap<>();
    private volatile boolean bucketEnsured;

    public StudentAvatarStorageService(MinioStorageProperties minioStorageProperties) {
        this.minioStorageProperties = minioStorageProperties;
    }

    @PostConstruct
    void logStorageMode() {
        if (minioStorageProperties.isEnabled()) {
            log.info("student avatar storage initialized with MinIO bucket={}", minioStorageProperties.getBucket());
        } else {
            log.warn("student avatar storage fallback to in-memory mode because MinIO is disabled");
        }
    }

    public StoredAvatar upload(long userId, MultipartFile file) {
        validateFile(file);
        NormalizedAvatar normalizedAvatar = normalizeAvatar(file);
        Instant uploadedAt = Instant.now();
        String bucket = resolveBucketName();
        String objectKey = buildObjectKey(userId, uploadedAt);

        if (minioStorageProperties.isEnabled()) {
            MinioClient minioClient = buildMinioClient();
            ensureBucketExists(minioClient);
            try {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(new ByteArrayInputStream(normalizedAvatar.bytes()), normalizedAvatar.sizeBytes(), -1)
                                .contentType(normalizedAvatar.contentType())
                                .build()
                );
            } catch (Exception ex) {
                throw new ApiException("BIZ-1001", "头像上传失败，请确认 MinIO 对象存储服务可用", HttpStatus.BAD_GATEWAY);
            }
        } else {
            memoryStore.put(buildMemoryStoreKey(bucket, objectKey), new StoredAvatarContent(normalizedAvatar.bytes(), normalizedAvatar.contentType()));
        }

        return new StoredAvatar(bucket, objectKey, normalizedAvatar.contentType(), normalizedAvatar.sizeBytes(), uploadedAt.toString());
    }

    public StoredAvatarContent read(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            throw new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND);
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
                return new StoredAvatarContent(inputStream.readAllBytes(), "image/jpeg");
            } catch (Exception ex) {
                throw new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND);
            }
        }

        StoredAvatarContent content = memoryStore.get(buildMemoryStoreKey(bucket, objectKey));
        if (content == null) {
            throw new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND);
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
                log.warn("删除头像对象失败 bucket={} objectKey={}", bucket, objectKey, ex);
                return false;
            }
        }

        return memoryStore.remove(buildMemoryStoreKey(bucket, objectKey)) != null;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("BIZ-1001", "请先选择头像图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_SOURCE_FILE_SIZE_BYTES) {
            throw new ApiException("BIZ-1001", "头像原图过大，请控制在 6 MB 以内", HttpStatus.BAD_REQUEST);
        }
    }

    private NormalizedAvatar normalizeAvatar(MultipartFile file) {
        BufferedImage sourceImage;
        try (InputStream inputStream = file.getInputStream()) {
            sourceImage = ImageIO.read(inputStream);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "头像图片读取失败，请重新上传 JPG 或 PNG", HttpStatus.BAD_REQUEST);
        }

        if (sourceImage == null) {
            throw new ApiException("BIZ-1001", "头像仅支持 JPG 或 PNG 图片", HttpStatus.BAD_REQUEST);
        }
        if (sourceImage.getWidth() < MIN_IMAGE_EDGE || sourceImage.getHeight() < MIN_IMAGE_EDGE) {
            throw new ApiException("BIZ-1001", "头像尺寸太小，请选择至少 120 x 120 的图片", HttpStatus.BAD_REQUEST);
        }

        BufferedImage squareImage = cropToSquare(sourceImage);
        byte[] encodedBytes = null;

        for (int outputSize : OUTPUT_SIZE_CANDIDATES) {
            BufferedImage resizedImage = resizeSquareImage(squareImage, outputSize);
            for (float quality : JPEG_QUALITY_CANDIDATES) {
                encodedBytes = encodeAsJpeg(resizedImage, quality);
                if (encodedBytes.length <= TARGET_MAX_BYTES) {
                    return new NormalizedAvatar(encodedBytes, "image/jpeg");
                }
            }
        }

        if (encodedBytes == null) {
            encodedBytes = encodeAsJpeg(resizeSquareImage(squareImage, 384), 0.7f);
        }
        return new NormalizedAvatar(encodedBytes, "image/jpeg");
    }

    private BufferedImage cropToSquare(BufferedImage sourceImage) {
        int squareSize = Math.min(sourceImage.getWidth(), sourceImage.getHeight());
        int startX = Math.max((sourceImage.getWidth() - squareSize) / 2, 0);
        int startY = Math.max((sourceImage.getHeight() - squareSize) / 2, 0);
        BufferedImage croppedImage = sourceImage.getSubimage(startX, startY, squareSize, squareSize);

        BufferedImage rgbImage = new BufferedImage(squareSize, squareSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, squareSize, squareSize);
            graphics.drawImage(croppedImage, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    private BufferedImage resizeSquareImage(BufferedImage sourceImage, int outputSize) {
        BufferedImage resizedImage = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, outputSize, outputSize);
            graphics.drawImage(sourceImage, 0, 0, outputSize, outputSize, null);
        } finally {
            graphics.dispose();
        }
        return resizedImage;
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
            throw new ApiException("BIZ-1001", "头像压缩失败，请重新上传图片", HttpStatus.BAD_REQUEST);
        } finally {
            writer.dispose();
        }
    }

    private String buildObjectKey(long userId, Instant uploadedAt) {
        return "avatars/student/%s/u%s/avatar-%s-%s.jpg".formatted(
                DATE_PATH_FORMATTER.format(uploadedAt),
                userId,
                TIMESTAMP_FORMATTER.format(uploadedAt),
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    private String resolveBucketName() {
        if (!minioStorageProperties.isEnabled()) {
            return MEMORY_BUCKET;
        }

        String bucket = minioStorageProperties.getBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new ApiException("BIZ-1001", "MinIO bucket 未配置，无法保存头像", HttpStatus.SERVICE_UNAVAILABLE);
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

    public record StoredAvatar(
            String bucket,
            String objectKey,
            String contentType,
            long sizeBytes,
            String uploadedAt
    ) {
    }

    public record StoredAvatarContent(
            byte[] bytes,
            String contentType
    ) {
    }

    private record NormalizedAvatar(
            byte[] bytes,
            String contentType
    ) {
        long sizeBytes() {
            return bytes.length;
        }
    }
}
