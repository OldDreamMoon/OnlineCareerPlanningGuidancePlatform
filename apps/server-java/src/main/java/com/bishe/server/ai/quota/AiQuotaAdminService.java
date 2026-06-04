package com.bishe.server.ai.quota;

import com.bishe.server.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 配额策略后台管理服务。
 */
@Service
public class AiQuotaAdminService {

    private final AiQuotaPolicyRepository quotaPolicyRepository;

    public AiQuotaAdminService(AiQuotaPolicyRepository quotaPolicyRepository) {
        this.quotaPolicyRepository = quotaPolicyRepository;
    }

    public QuotaPolicyListPayload listQuotaPolicies() {
        List<QuotaPolicyItem> records = quotaPolicyRepository.findAllPolicies().stream()
                .map(this::toQuotaPolicyItem)
                .toList();
        return new QuotaPolicyListPayload(records);
    }

    @Transactional
    public QuotaPolicyItem updateQuotaPolicy(long policyId, UpdateQuotaPolicyCommand command) {
        AiQuotaPolicyRepository.QuotaPolicyRow current = quotaPolicyRepository.findPolicyById(policyId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "quota policy not found", HttpStatus.NOT_FOUND));

        int dailyFreeLimit = normalizeLimit(command.dailyFreeLimit(), current.dailyFreeLimit(), "dailyFreeLimit", true);
        int pointsPerCall = normalizeNonNegative(command.pointsPerCall(), current.pointsPerCall(), 100000, "pointsPerCall");
        int dailyMaxLimit = normalizeLimit(command.dailyMaxLimit(), current.dailyMaxLimit(), "dailyMaxLimit", true);
        String modelPreference = normalizeOptionalText(command.modelPreference(), current.modelPreference(), 120);
        int maxInputTokens = normalizeNonNegative(command.maxInputTokens(), current.maxInputTokens(), 200000, "maxInputTokens", 1);

        if (dailyFreeLimit >= 0 && dailyMaxLimit >= 0 && dailyFreeLimit > dailyMaxLimit) {
            throw new ApiException("BIZ-1001", "dailyFreeLimit invalid", HttpStatus.BAD_REQUEST);
        }

        quotaPolicyRepository.updatePolicy(
                policyId,
                dailyFreeLimit,
                pointsPerCall,
                dailyMaxLimit,
                modelPreference,
                maxInputTokens
        );

        return quotaPolicyRepository.findPolicyById(policyId)
                .map(this::toQuotaPolicyItem)
                .orElseThrow(() -> new ApiException("BIZ-1002", "quota policy not found", HttpStatus.NOT_FOUND));
    }

    private QuotaPolicyItem toQuotaPolicyItem(AiQuotaPolicyRepository.QuotaPolicyRow row) {
        return new QuotaPolicyItem(
                row.id(),
                row.tier(),
                row.taskType(),
                row.sceneCode(),
                row.dailyFreeLimit(),
                row.pointsPerCall(),
                row.dailyMaxLimit(),
                safe(row.modelPreference()),
                row.maxInputTokens(),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private int normalizeLimit(Integer rawValue, int fallback, String fieldName, boolean allowUnlimited) {
        if (rawValue == null) {
            return fallback;
        }
        int value = rawValue;
        if (allowUnlimited && value == -1) {
            return value;
        }
        if (value < 0 || value > 100000) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private int normalizeNonNegative(Integer rawValue, int fallback, int max, String fieldName) {
        return normalizeNonNegative(rawValue, fallback, max, fieldName, 0);
    }

    private int normalizeNonNegative(Integer rawValue, int fallback, int max, String fieldName, int min) {
        if (rawValue == null) {
            return fallback;
        }
        int value = rawValue;
        if (value < min || value > max) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalText(String rawValue, String fallback, int maxLength) {
        String candidate = rawValue == null ? fallback : rawValue;
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String normalized = candidate.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException("BIZ-1001", "modelPreference invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    public record UpdateQuotaPolicyCommand(
            Integer dailyFreeLimit,
            Integer pointsPerCall,
            Integer dailyMaxLimit,
            String modelPreference,
            Integer maxInputTokens
    ) {
    }

    public record QuotaPolicyListPayload(List<QuotaPolicyItem> records) {
    }

    public record QuotaPolicyItem(
            long id,
            String tier,
            String taskType,
            String sceneCode,
            int dailyFreeLimit,
            int pointsPerCall,
            int dailyMaxLimit,
            String modelPreference,
            int maxInputTokens,
            Long createdAt,
            Long updatedAt
    ) {
    }
}
