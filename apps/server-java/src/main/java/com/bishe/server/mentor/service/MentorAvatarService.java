package com.bishe.server.mentor.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.mentor.dto.MentorProfileAvatarUploadResponse;
import com.bishe.server.mentor.repository.MentorRepository;
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导师头像存储与公开访问服务。
 */
@Service
public class MentorAvatarService {

    private static final Logger log = LoggerFactory.getLogger(MentorAvatarService.class);
    private static final long MAX_SOURCE_FILE_SIZE_BYTES = 6L * 1024L * 1024L;
    private static final int MIN_IMAGE_EDGE = 120;
    private static final int TARGET_MAX_BYTES = 380 * 1024;
    private static final int[] OUTPUT_SIZE_CANDIDATES = {512, 448, 384};
    private static final float[] JPEG_QUALITY_CANDIDATES = {0.9f, 0.84f, 0.78f, 0.72f};
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String MEMORY_BUCKET = "mentor-avatar-memory-store";
    private static final String AVATAR_VERSION_NAMESPACE = "mentor-avatar-v2";
    private static final Duration REMOTE_AVATAR_FETCH_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REMOTE_AVATAR_CACHE_TTL = Duration.ofHours(12);
    private static final List<String> PROXYABLE_REMOTE_AVATAR_HOSTS = List.of(
            "i.pravatar.cc",
            "api.dicebear.com"
    );
    private static final List<String> PRESET_AVATAR_URLS = List.of(
            "https://api.dicebear.com/7.x/notionists/svg?seed=DrWang",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Lin",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Zhang",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Sarah",
            "https://api.dicebear.com/7.x/notionists/svg?seed=David",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Liu",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Chen",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Zhao",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Lily",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Zhou",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Zheng",
            "https://api.dicebear.com/7.x/notionists/svg?seed=Mentor"
    );

    private final MentorRepository mentorRepository;
    private final MinioStorageProperties minioStorageProperties;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final HttpClient httpClient;
    private final Map<String, StoredAvatarContent> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, CachedRemoteAvatarContent> remoteAvatarCache = new ConcurrentHashMap<>();
    private volatile boolean bucketEnsured;

    public MentorAvatarService(
            MentorRepository mentorRepository,
            MinioStorageProperties minioStorageProperties,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService
    ) {
        this.mentorRepository = mentorRepository;
        this.minioStorageProperties = minioStorageProperties;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(REMOTE_AVATAR_FETCH_TIMEOUT)
                .build();
    }

    @PostConstruct
    void logStorageMode() {
        if (minioStorageProperties.isEnabled()) {
            log.info("mentor avatar storage initialized with MinIO bucket={}", minioStorageProperties.getBucket());
        } else {
            log.warn("mentor avatar storage fallback to in-memory mode because MinIO is disabled");
        }
    }

    public MentorProfileAvatarUploadResponse uploadOwnAvatar(long mentorUserId, MultipartFile file) {
        mentorRepository.findOwnProfile(mentorUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "mentor profile not found", HttpStatus.NOT_FOUND));

        MentorRepository.AvatarAssetRow previousAvatar = mentorRepository.findAvatarAssetByUserId(mentorUserId).orElse(null);
        StoredAvatar storedAvatar = upload(mentorUserId, file);
        Instant uploadedAt = Instant.parse(storedAvatar.uploadedAt());
        mentorRepository.saveOrUpdateAvatar(
                mentorUserId,
                storedAvatar.bucket(),
                storedAvatar.objectKey(),
                storedAvatar.contentType(),
                uploadedAt
        );
        mentorPublicDetailCacheService.evictNow(mentorUserId);
        mentorPublicDetailCacheService.evictAfterCommit(mentorUserId);
        mentorPublicListCacheService.evictAllNow();
        mentorPublicListCacheService.evictAllAfterCommit();

        if (previousAvatar != null && hasText(previousAvatar.bucket()) && hasText(previousAvatar.objectKey())) {
            boolean avatarChanged = !storedAvatar.bucket().equals(previousAvatar.bucket())
                    || !storedAvatar.objectKey().equals(previousAvatar.objectKey());
            if (avatarChanged) {
                deleteQuietly(previousAvatar.bucket(), previousAvatar.objectKey());
            }
        }

        return new MentorProfileAvatarUploadResponse(
                true,
                storedAvatar.contentType(),
                storedAvatar.sizeBytes(),
                uploadedAt.toEpochMilli(),
                buildPublicAvatarUrl(mentorUserId, storedAvatar.uploadedAt())
        );
    }

    public StoredAvatarContent readPublicAvatar(long mentorUserId) {
        MentorRepository.PublicAvatarSourceRow avatarSource = mentorRepository.findPublicAvatarSourceByUserId(mentorUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND));
        if (hasText(avatarSource.bucket()) && hasText(avatarSource.objectKey())) {
            return read(avatarSource.bucket(), avatarSource.objectKey());
        }

        String normalizedRemoteAvatarUrl = TextListCodec.normalizeText(avatarSource.avatarUrl());
        if (isProxyableRemoteAvatarUrl(normalizedRemoteAvatarUrl)) {
            try {
                return readRemoteAvatar(normalizedRemoteAvatarUrl);
            } catch (ApiException ex) {
                log.warn("读取导师远程头像失败，降级到预置头像 mentorUserId={} avatarUrl={}", mentorUserId, normalizedRemoteAvatarUrl);
            }
        }
        return readRemoteAvatar(resolvePresetAvatarUrl(mentorUserId));
    }

    public String resolveAvatarUrl(
            long mentorUserId,
            String rawAvatarUrl,
            String displayName,
            String avatarObjectKey,
            Instant avatarUpdatedAt
    ) {
        if (hasText(avatarObjectKey)) {
            return buildPublicAvatarUrl(mentorUserId, avatarUpdatedAt == null ? null : avatarUpdatedAt.toString());
        }

        String normalized = TextListCodec.normalizeText(rawAvatarUrl);
        if (isProxyableRemoteAvatarUrl(normalized)) {
            return buildPublicAvatarUrl(mentorUserId, buildVersionToken(normalized));
        }
        if (normalized != null) {
            return normalized;
        }
        return buildPublicAvatarUrl(mentorUserId, buildVersionToken(resolvePresetAvatarUrl(mentorUserId)));
    }

    private StoredAvatar upload(long mentorUserId, MultipartFile file) {
        validateFile(file);
        NormalizedAvatar normalizedAvatar = normalizeAvatar(file);
        Instant uploadedAt = Instant.now();
        String bucket = resolveBucketName();
        String objectKey = buildObjectKey(mentorUserId, uploadedAt);

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
                throw new ApiException("BIZ-1001", "导师头像上传失败，请确认 MinIO 对象存储服务可用", HttpStatus.BAD_GATEWAY);
            }
        } else {
            memoryStore.put(buildMemoryStoreKey(bucket, objectKey), new StoredAvatarContent(normalizedAvatar.bytes(), normalizedAvatar.contentType()));
        }

        return new StoredAvatar(bucket, objectKey, normalizedAvatar.contentType(), normalizedAvatar.sizeBytes(), uploadedAt.toString());
    }

    private StoredAvatarContent read(String bucket, String objectKey) {
        if (!hasText(bucket) || !hasText(objectKey)) {
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

    private boolean deleteQuietly(String bucket, String objectKey) {
        if (!hasText(bucket) || !hasText(objectKey)) {
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
                log.warn("删除导师头像对象失败 bucket={} objectKey={}", bucket, objectKey, ex);
                return false;
            }
        }

        return memoryStore.remove(buildMemoryStoreKey(bucket, objectKey)) != null;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("BIZ-1001", "请先选择导师头像图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_SOURCE_FILE_SIZE_BYTES) {
            throw new ApiException("BIZ-1001", "导师头像原图过大，请控制在 6 MB 以内", HttpStatus.BAD_REQUEST);
        }
    }

    private NormalizedAvatar normalizeAvatar(MultipartFile file) {
        BufferedImage sourceImage;
        try (InputStream inputStream = file.getInputStream()) {
            sourceImage = ImageIO.read(inputStream);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "导师头像图片读取失败，请重新上传 JPG 或 PNG", HttpStatus.BAD_REQUEST);
        }

        if (sourceImage == null) {
            throw new ApiException("BIZ-1001", "导师头像仅支持 JPG 或 PNG 图片", HttpStatus.BAD_REQUEST);
        }
        if (sourceImage.getWidth() < MIN_IMAGE_EDGE || sourceImage.getHeight() < MIN_IMAGE_EDGE) {
            throw new ApiException("BIZ-1001", "导师头像尺寸太小，请选择至少 120 x 120 的图片", HttpStatus.BAD_REQUEST);
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
            throw new ApiException("BIZ-1001", "导师头像压缩失败，请重新上传图片", HttpStatus.BAD_REQUEST);
        } finally {
            writer.dispose();
        }
    }

    private StoredAvatarContent readRemoteAvatar(String avatarUrl) {
        CachedRemoteAvatarContent cachedContent = remoteAvatarCache.get(avatarUrl);
        Instant now = Instant.now();
        if (cachedContent != null && !cachedContent.isExpiredAt(now)) {
            return cachedContent.content();
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(avatarUrl))
                    .timeout(REMOTE_AVATAR_FETCH_TIMEOUT)
                    .header("Accept", "image/*")
                    .GET()
                    .build();
        } catch (Exception ex) {
            throw new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND);
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes == null || bytes.length == 0) {
                if (cachedContent != null) {
                    return cachedContent.content();
                }
                throw new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND);
            }
            StoredAvatarContent content = new StoredAvatarContent(bytes, normalizeRemoteContentType(response.headers().firstValue("Content-Type").orElse(null)));
            remoteAvatarCache.put(avatarUrl, new CachedRemoteAvatarContent(content, now.plus(REMOTE_AVATAR_CACHE_TTL)));
            return content;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            if (cachedContent != null) {
                return cachedContent.content();
            }
            throw new ApiException("BIZ-1002", "avatar not found", HttpStatus.NOT_FOUND);
        }
    }

    private String normalizeRemoteContentType(String contentType) {
        String normalized = TextListCodec.normalizeText(contentType);
        if (normalized == null) {
            return "image/jpeg";
        }
        int separatorIndex = normalized.indexOf(';');
        String mediaType = separatorIndex >= 0 ? normalized.substring(0, separatorIndex).trim() : normalized;
        return mediaType.startsWith("image/") ? mediaType : "image/jpeg";
    }

    private String buildPublicAvatarUrl(long mentorUserId, String updatedAt) {
        String baseUrl = "/api/v1/mentors/" + mentorUserId + "/avatar";
        if (!hasText(updatedAt)) {
            return baseUrl;
        }
        return baseUrl + "?v=" + URLEncoder.encode(updatedAt, StandardCharsets.UTF_8);
    }

    private String buildObjectKey(long mentorUserId, Instant uploadedAt) {
        return "avatars/mentor/%s/u%s/avatar-%s-%s.jpg".formatted(
                DATE_PATH_FORMATTER.format(uploadedAt),
                mentorUserId,
                TIMESTAMP_FORMATTER.format(uploadedAt),
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    private String resolveBucketName() {
        String configuredBucket = minioStorageProperties.getBucket();
        return hasText(configuredBucket) ? configuredBucket : MEMORY_BUCKET;
    }

    private String buildMemoryStoreKey(String bucket, String objectKey) {
        return bucket + ":" + objectKey;
    }

    private String resolvePresetAvatarUrl(long mentorUserId) {
        int index = Math.floorMod(Long.hashCode(mentorUserId), PRESET_AVATAR_URLS.size());
        return PRESET_AVATAR_URLS.get(index);
    }

    private String buildVersionToken(String seed) {
        String namespacedSeed = AVATAR_VERSION_NAMESPACE + "|" + (seed == null ? "" : seed);
        return Integer.toUnsignedString(namespacedSeed.hashCode(), 16);
    }

    private boolean isProxyableRemoteAvatarUrl(String avatarUrl) {
        if (!hasText(avatarUrl)) {
            return false;
        }
        try {
            URI uri = URI.create(avatarUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!hasText(scheme) || !hasText(host)) {
                return false;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return PROXYABLE_REMOTE_AVATAR_HOSTS.contains(normalizedHost);
        } catch (Exception ex) {
            return false;
        }
    }

    private MinioClient buildMinioClient() {
        return MinioClient.builder()
                .endpoint(normalizeEndpoint(minioStorageProperties.getEndpoint()))
                .credentials(minioStorageProperties.getAccessKey(), minioStorageProperties.getSecretKey())
                .build();
    }

    private String normalizeEndpoint(String endpoint) {
        String trimmed = endpoint == null ? "" : endpoint.trim();
        if (!hasText(trimmed)) {
            throw new ApiException("BIZ-1001", "MinIO endpoint 未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "http://" + trimmed;
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
                throw new ApiException("BIZ-1001", "导师头像对象存储初始化失败", HttpStatus.BAD_GATEWAY);
            }
        }
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
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

    private record CachedRemoteAvatarContent(
            StoredAvatarContent content,
            Instant expiresAt
    ) {
        boolean isExpiredAt(Instant now) {
            return now == null || expiresAt == null || !expiresAt.isAfter(now);
        }
    }
}
