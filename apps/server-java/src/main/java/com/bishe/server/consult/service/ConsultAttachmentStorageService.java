package com.bishe.server.consult.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.storage.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 咨询订单材料对象存储服务。
 * 正式环境优先写入 MinIO；测试或显式关闭 MinIO 时退回进程内存，便于前后端联调。
 */
@Service
public class ConsultAttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(ConsultAttachmentStorageService.class);
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "png", "jpg", "jpeg");
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String MEMORY_BUCKET = "consult-attachment-memory-store";

    private final MinioStorageProperties minioStorageProperties;
    private final Map<String, StoredAttachmentContent> memoryStore = new ConcurrentHashMap<>();
    private volatile boolean bucketEnsured;

    public ConsultAttachmentStorageService(MinioStorageProperties minioStorageProperties) {
        this.minioStorageProperties = minioStorageProperties;
    }

    @PostConstruct
    void logStorageMode() {
        if (minioStorageProperties.isEnabled()) {
            log.info("consult attachment storage initialized with MinIO bucket={}", minioStorageProperties.getBucket());
        } else {
            log.warn("consult attachment storage fallback to in-memory mode because MinIO is disabled");
        }
    }

    public StoredAttachment upload(String orderNo, String slotCode, String attachmentType, MultipartFile file) {
        validateFile(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "材料读取失败，请重新上传", HttpStatus.BAD_REQUEST, ex);
        }

        String normalizedContentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        Instant uploadedAt = Instant.now();
        String bucket = resolveBucketName();
        String objectKey = buildObjectKey(orderNo, slotCode, attachmentType, file.getOriginalFilename(), uploadedAt);

        if (minioStorageProperties.isEnabled()) {
            MinioClient minioClient = buildMinioClient();
            ensureBucketExists(minioClient);
            try {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                                .contentType(normalizedContentType)
                                .build()
                );
            } catch (Exception ex) {
                throw new ApiException("BIZ-1001", "材料上传失败，请确认对象存储服务可用", HttpStatus.BAD_GATEWAY);
            }
        } else {
            memoryStore.put(buildMemoryStoreKey(bucket, objectKey), new StoredAttachmentContent(bytes, normalizedContentType));
        }

        return new StoredAttachment(
                bucket,
                objectKey,
                sanitizeFilename(file.getOriginalFilename()),
                normalizedContentType,
                bytes.length,
                uploadedAt.toString()
        );
    }

    public StoredAttachmentContent read(String bucket, String objectKey) {
        String normalizedBucket = StringUtils.hasText(bucket) ? bucket.trim() : resolveBucketName();
        String normalizedObjectKey = StringUtils.hasText(objectKey) ? objectKey.trim() : null;
        if (!StringUtils.hasText(normalizedObjectKey)) {
            throw new ApiException("BIZ-1002", "attachment content not found", HttpStatus.NOT_FOUND);
        }

        if (minioStorageProperties.isEnabled()) {
            MinioClient minioClient = buildMinioClient();
            try (var stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(normalizedBucket)
                            .object(normalizedObjectKey)
                            .build()
            )) {
                return new StoredAttachmentContent(stream.readAllBytes(), null);
            } catch (Exception ex) {
                throw new ApiException("BIZ-1002", "attachment content not found", HttpStatus.NOT_FOUND);
            }
        }

        StoredAttachmentContent content = memoryStore.get(buildMemoryStoreKey(normalizedBucket, normalizedObjectKey));
        if (content == null) {
            throw new ApiException("BIZ-1002", "attachment content not found", HttpStatus.NOT_FOUND);
        }
        return content;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("BIZ-1001", "请先选择要上传的材料", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException("BIZ-1001", "单个材料不能超过 20 MB", HttpStatus.BAD_REQUEST);
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException("BIZ-1001", "当前仅支持 PDF、DOC、DOCX、PNG、JPG、JPEG 材料", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeContentType(String rawContentType, String originalFilename) {
        String normalized = rawContentType == null ? null : rawContentType.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(normalized) && ALLOWED_CONTENT_TYPES.contains(normalized)) {
            return normalized;
        }

        String extension = extractExtension(originalFilename);
        if (extension == null) {
            throw new ApiException("BIZ-1001", "无法识别材料类型，请重新上传", HttpStatus.BAD_REQUEST);
        }

        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> throw new ApiException("BIZ-1001", "当前仅支持 PDF、DOC、DOCX、PNG、JPG、JPEG 材料", HttpStatus.BAD_REQUEST);
        };
    }

    private String buildObjectKey(String orderNo, String slotCode, String attachmentType, String originalFilename, Instant uploadedAt) {
        String extension = extractExtension(originalFilename);
        String safeExtension = extension == null ? "bin" : extension;
        return "consult/orders/%s/%s/%s/%s-%s-%s.%s".formatted(
                DATE_PATH_FORMATTER.format(uploadedAt),
                sanitizePathSegment(orderNo),
                sanitizePathSegment(slotCode),
                sanitizePathSegment(attachmentType),
                TIMESTAMP_FORMATTER.format(uploadedAt),
                UUID.randomUUID().toString().replace("-", ""),
                safeExtension
        );
    }

    private String sanitizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "consult-attachment";
        }
        String trimmed = originalFilename.trim();
        return trimmed.isEmpty() ? "consult-attachment" : trimmed;
    }

    private String sanitizePathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
    }

    private String extractExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String trimmed = originalFilename.trim();
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveBucketName() {
        String configuredBucket = minioStorageProperties.getBucket();
        return StringUtils.hasText(configuredBucket) ? configuredBucket.trim() : MEMORY_BUCKET;
    }

    private MinioClient buildMinioClient() {
        if (!StringUtils.hasText(minioStorageProperties.getEndpoint())) {
            throw new ApiException("BIZ-1001", "对象存储 endpoint 未配置", HttpStatus.BAD_GATEWAY);
        }
        return MinioClient.builder()
                .endpoint(minioStorageProperties.getEndpoint())
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
            String bucket = resolveBucketName();
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(bucket)
                                .build()
                );
                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(bucket)
                                    .build()
                    );
                }
                bucketEnsured = true;
            } catch (Exception ex) {
                throw new ApiException("BIZ-1001", "对象存储 bucket 初始化失败", HttpStatus.BAD_GATEWAY);
            }
        }
    }

    private String buildMemoryStoreKey(String bucket, String objectKey) {
        return bucket + ":" + objectKey;
    }

    public record StoredAttachment(
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String uploadedAt
    ) {
    }

    public record StoredAttachmentContent(
            byte[] bytes,
            String contentType
    ) {
    }
}
