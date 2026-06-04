package com.bishe.server.certification;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.storage.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 正式认证资料对象存储服务。
 */
@Service
public class CertificationStorageService {

    private static final Logger log = LoggerFactory.getLogger(CertificationStorageService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("MENTOR", "ENTERPRISE");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final CertificationProperties certificationProperties;
    private final MinioStorageProperties minioStorageProperties;
    private volatile boolean bucketEnsured;

    public CertificationStorageService(CertificationProperties certificationProperties, MinioStorageProperties minioStorageProperties) {
        this.certificationProperties = certificationProperties;
        this.minioStorageProperties = minioStorageProperties;
    }

    public StoredObject upload(String role, String identifier, String flowSegment, MultipartFile file) {
        ensureStorageReady();

        String normalizedRole = normalizeRole(role);
        validateFile(file);
        String contentType = resolveContentType(file);
        Instant uploadedAt = Instant.now();
        String objectKey = buildObjectKey(normalizedRole, identifier, flowSegment, file.getOriginalFilename(), uploadedAt);
        MinioClient minioClient = buildMinioClient();
        ensureBucketExists(minioClient);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioStorageProperties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "认证资料上传失败，请确认对象存储服务可用", HttpStatus.BAD_GATEWAY);
        }

        return new StoredObject(
                minioStorageProperties.getBucket(),
                objectKey,
                safeOriginalFilename(file.getOriginalFilename()),
                contentType,
                file.getSize(),
                uploadedAt.toString()
        );
    }

    public boolean deleteQuietly(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey) || !minioStorageProperties.isEnabled()) {
            return false;
        }

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
            log.warn("删除认证资料对象失败 bucket={} objectKey={}", bucket, objectKey, ex);
            return false;
        }
    }

    public StoredContent read(String bucket, String objectKey) {
        ensureStorageReady();

        try {
            MinioClient minioClient = buildMinioClient();
            ensureBucketExists(minioClient);
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            )) {
                return new StoredContent(inputStream.readAllBytes());
            }
        } catch (Exception ex) {
            throw new ApiException("BIZ-1002", "certification asset not found", HttpStatus.NOT_FOUND);
        }
    }

    private void ensureStorageReady() {
        if (!minioStorageProperties.isEnabled()) {
            throw new ApiException("BIZ-1001", "当前环境未开启 MinIO 对象存储", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!StringUtils.hasText(minioStorageProperties.getBucket())) {
            throw new ApiException("BIZ-1001", "MinIO bucket 未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String normalizeRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new ApiException("BIZ-1001", "当前只允许导师或企业提交认证资料", HttpStatus.BAD_REQUEST);
        }
        return normalizedRole;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("BIZ-1001", "请先选择认证资料文件", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > certificationProperties.getMaxFileSizeBytes()) {
            throw new ApiException("BIZ-1001", "认证资料文件过大，请控制在 10 MB 以内", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String normalized = contentType.trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_CONTENT_TYPES.contains(normalized)) {
                return normalized;
            }
        }

        String filename = safeOriginalFilename(file.getOriginalFilename());
        int dotIndex = filename.lastIndexOf('.');
        String extension = dotIndex >= 0 ? filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            default -> throw new ApiException("BIZ-1001", "认证资料仅支持 JPG、PNG 或 PDF", HttpStatus.BAD_REQUEST);
        };
    }

    private String buildObjectKey(String role, String identifier, String flowSegment, String originalFilename, Instant uploadedAt) {
        String identitySegment = sanitizeSegment(identifier);
        String flow = sanitizeSegment(flowSegment);
        String filename = safeOriginalFilename(originalFilename);
        String extension = "";
        String filenameBody = filename;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
            filenameBody = filename.substring(0, dotIndex);
        }
        String normalizedFilename = sanitizeSegment(filenameBody);
        String randomSuffix = UUID.randomUUID().toString().replace("-", "");
        return "certification/%s/%s/%s-%s-%s-%s%s".formatted(
                role.toLowerCase(Locale.ROOT),
                DATE_PATH_FORMATTER.format(uploadedAt),
                TIMESTAMP_FORMATTER.format(uploadedAt),
                flow,
                identitySegment,
                randomSuffix + "-" + normalizedFilename,
                extension
        );
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
                                .bucket(minioStorageProperties.getBucket())
                                .build()
                );
                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(minioStorageProperties.getBucket())
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

    private String safeOriginalFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "certification-upload";
        filename = filename.replace("\\", "/");
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }
        return filename.isBlank() ? "certification-upload" : filename;
    }

    private String sanitizeSegment(String raw) {
        String sanitized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-").replaceAll("^-+", "").replaceAll("-+$", "");
        return sanitized.isBlank() ? "anonymous" : sanitized;
    }

    public record StoredObject(
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String uploadedAt
    ) {
    }

    public record StoredContent(byte[] bytes) {
    }
}
