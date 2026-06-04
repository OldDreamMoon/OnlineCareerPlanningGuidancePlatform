package com.bishe.server.ai.quota;

import com.bishe.server.ai.dto.AiPingRequest;
import com.bishe.server.ai.dto.AiPingResponse;
import com.bishe.server.ai.dto.AiQuotaRemainingResponse;
import com.bishe.server.ai.gateway.AiGatewayService;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.growth.repository.GrowthRepository;
import com.bishe.server.growth.service.GrowthCenterCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * AI 配额检查、积分联动与调用日志服务。
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);
    private static final String INTERVIEW_SESSION_REASON_CODE = "AI_INTERVIEW_SESSION";

    private final UserRepository userRepository;
    private final AiQuotaPolicyRepository aiQuotaPolicyRepository;
    private final AiQuotaRepository aiQuotaRepository;
    private final GrowthRepository growthRepository;
    private final GrowthCenterCacheService growthCenterCacheService;
    private final AiGatewayService aiGatewayService;
    private final AiQuotaUsageCounterService aiQuotaUsageCounterService;
    private final AiMinuteRateLimitService aiMinuteRateLimitService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;

    public AiQuotaService(
            UserRepository userRepository,
            AiQuotaPolicyRepository aiQuotaPolicyRepository,
            AiQuotaRepository aiQuotaRepository,
            GrowthRepository growthRepository,
            GrowthCenterCacheService growthCenterCacheService,
            AiGatewayService aiGatewayService,
            AiQuotaUsageCounterService aiQuotaUsageCounterService,
            AiMinuteRateLimitService aiMinuteRateLimitService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService
    ) {
        this.userRepository = userRepository;
        this.aiQuotaPolicyRepository = aiQuotaPolicyRepository;
        this.aiQuotaRepository = aiQuotaRepository;
        this.growthRepository = growthRepository;
        this.growthCenterCacheService = growthCenterCacheService;
        this.aiGatewayService = aiGatewayService;
        this.aiQuotaUsageCounterService = aiQuotaUsageCounterService;
        this.aiMinuteRateLimitService = aiMinuteRateLimitService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
    }

    public AiQuotaRemainingResponse getRemainingQuota(long userId) {
        AppUser user = loadStudentUser(userId);
        LocalDate today = LocalDate.now();
        String userScope = buildUserScope(user);
        List<AiQuotaRemainingResponse.QuotaItem> quotas = aiQuotaPolicyRepository.findTaskFallbackPoliciesByTier(user.tier()).stream()
                .map(policy -> {
                    int usedToday = getUsedToday(userScope, userId, policy.taskType(), policy.sceneCode(), today);
                    int remaining = policy.dailyFreeLimit() < 0 ? -1 : Math.max(policy.dailyFreeLimit() - usedToday, 0);
                    return new AiQuotaRemainingResponse.QuotaItem(policy.taskType(), policy.dailyFreeLimit(), usedToday, remaining);
                })
                .toList();
        return new AiQuotaRemainingResponse(user.tier(), quotas, growthRepository.getCurrentBalance(userId));
    }

    @Transactional
    public AiPingResponse ping(long userId, String traceId, AiPingRequest request) {
        AiTaskType taskType = parseTaskType(request.taskType());
        return executeStudentTask(userId, traceId, taskType, context -> {
            AiGatewayService.AiGatewayResult gatewayResult = aiGatewayService.ping(
                    taskType.name(),
                    request.scene(),
                    context.modelPreference(),
                    context.tier()
            );
            return new AiTaskExecution<>(
                    new AiPingResponse(
                            gatewayResult.service(),
                            gatewayResult.module(),
                            gatewayResult.time(),
                            taskType.name(),
                            context.tier(),
                            context.freeCall(),
                            context.chargedPoints(),
                            context.pointsBalance(),
                            context.usedTodayAfterSuccess(),
                            gatewayResult.provider(),
                            gatewayResult.model(),
                            request.scene()
                    ),
                    gatewayResult
            );
        });
    }

    /**
     * 统一执行学生侧 AI 任务：先做配额/积分校验，再由调用方提供具体业务处理。
     */
    @Transactional
    public <T> T executeStudentTask(long userId, String traceId, AiTaskType taskType, AiTaskExecutor<T> taskExecutor) {
        return executeStudentTask(userId, traceId, taskType, null, taskExecutor, null);
    }

    @Transactional
    public <T> T executeStudentTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            String sceneCode,
            AiTaskExecutor<T> taskExecutor
    ) {
        return executeStudentTask(userId, traceId, taskType, sceneCode, taskExecutor, null);
    }

    /**
     * 执行学生侧 AI 任务，并在成功落日志后把调用日志 ID 回填给调用方。
     */
    @Transactional
    public <T> T executeStudentTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            AiTaskExecutor<T> taskExecutor,
            BiFunction<T, Long, T> successBinder
    ) {
        return executeStudentTask(userId, traceId, taskType, null, taskExecutor, successBinder);
    }

    @Transactional
    public <T> T executeStudentTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            String sceneCode,
            AiTaskExecutor<T> taskExecutor,
            BiFunction<T, Long, T> successBinder
    ) {
        return executeTask(
                userId,
                traceId,
                taskType,
                sceneCode,
                EnumSet.of(UserRole.STUDENT),
                "student not found",
                taskExecutor,
                successBinder
        );
    }

    /**
     * 统一执行指定角色集合可访问的 AI 任务：先做配额/积分校验，再由调用方提供具体业务处理。
     */
    @Transactional
    public <T> T executeTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            Set<UserRole> allowedRoles,
            String notFoundMessage,
            AiTaskExecutor<T> taskExecutor
    ) {
        return executeTask(userId, traceId, taskType, null, allowedRoles, notFoundMessage, taskExecutor, null);
    }

    @Transactional
    public <T> T executeTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            String sceneCode,
            Set<UserRole> allowedRoles,
            String notFoundMessage,
            AiTaskExecutor<T> taskExecutor
    ) {
        return executeTask(userId, traceId, taskType, sceneCode, allowedRoles, notFoundMessage, taskExecutor, null);
    }

    @Transactional
    public <T> T executeTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            Set<UserRole> allowedRoles,
            String notFoundMessage,
            AiTaskExecutor<T> taskExecutor,
            BiFunction<T, Long, T> successBinder
    ) {
        return executeTask(userId, traceId, taskType, null, allowedRoles, notFoundMessage, taskExecutor, successBinder);
    }

    @Transactional
    public <T> T executeTask(
            long userId,
            String traceId,
            AiTaskType taskType,
            String sceneCode,
            Set<UserRole> allowedRoles,
            String notFoundMessage,
            AiTaskExecutor<T> taskExecutor,
            BiFunction<T, Long, T> successBinder
    ) {
        String requestedSceneCode = normalizeSceneCode(sceneCode);
        ExecutionPreparation preparation = prepareExecution(userId, traceId, taskType, requestedSceneCode, allowedRoles, notFoundMessage);
        int pointsBalance = growthRepository.getCurrentBalance(userId);
        String effectiveModelPreference = aiGatewayService.resolveModelPreference(preparation.policy().modelPreference());
        AiTaskExecutionContext context = new AiTaskExecutionContext(
                userId,
                preparation.user().tier(),
                effectiveModelPreference,
                preparation.freeCall(),
                preparation.chargedPoints(),
                pointsBalance,
                preparation.usedToday() + 1,
                1
        );
        log.info(
                "ai task start traceId={}, userId={}, taskType={}, requestedSceneCode={}, billedSceneCode={}, tier={}, quotaModelPreference={}, freeCall={}, chargedPoints={}, usedToday={}",
                traceId,
                userId,
                taskType.name(),
                requestedSceneCode,
                preparation.policy().sceneCode(),
                preparation.user().tier(),
                effectiveModelPreference,
                preparation.freeCall(),
                preparation.chargedPoints(),
                preparation.usedToday()
        );
        AiTaskExecution<T> execution = taskExecutor.execute(context);
        long callLogId = recordTaskSuccess(
                traceId,
                userId,
                taskType,
                preparation.user().tier(),
                preparation.chargedPoints(),
                execution.gatewayResult(),
                preparation.policy().sceneCode(),
                execution.quotaWeight(),
                execution.resultSummary(),
                execution.resultPayloadJson()
        );
        if (successBinder == null) {
            return execution.payload();
        }
        return successBinder.apply(execution.payload(), callLogId);
    }

    /**
     * 为文本面试在创建阶段预留整场会话所需额度：开始前固定预扣，后续回复/总结不再逐次扣分。
     */
    public AiTaskExecutionContext reserveInterviewSession(long userId, String traceId, int reservedQuotaWeight) {
        ExecutionPreparation preparation = prepareInterviewSessionReservation(userId, traceId, reservedQuotaWeight);
        int pointsBalance = growthRepository.getCurrentBalance(userId);
        String effectiveModelPreference = aiGatewayService.resolveModelPreference(preparation.policy().modelPreference());
        log.info(
                "ai interview session reserved traceId={}, userId={}, tier={}, quotaModelPreference={}, reservedQuotaWeight={}, chargedPoints={}, pointsBalance={}, usedToday={}",
                traceId,
                userId,
                preparation.user().tier(),
                effectiveModelPreference,
                reservedQuotaWeight,
                preparation.chargedPoints(),
                pointsBalance,
                preparation.usedToday()
        );
        return new AiTaskExecutionContext(
                userId,
                preparation.user().tier(),
                effectiveModelPreference,
                preparation.freeCall(),
                preparation.chargedPoints(),
                pointsBalance,
                preparation.usedToday() + reservedQuotaWeight,
                reservedQuotaWeight
        );
    }

    /**
     * 获取已预付面试会话后续调用所需的运行上下文，不再重复扣分，也不再继续占用配额单位。
     */
    public AiTaskExecutionContext getTaskRuntimeContext(long userId, String traceId, AiTaskType taskType) {
        AppUser user = loadStudentUser(userId);
        AiQuotaPolicyRepository.QuotaPolicyRow policy = findPolicy(user.tier(), taskType);
        enforceMinuteRateLimit(traceId, user, taskType.name(), null, policy.modelPreference());
        int pointsBalance = growthRepository.getCurrentBalance(userId);
        int usedToday = getUsedToday(buildUserScope(user), userId, taskType.name(), policy.sceneCode(), LocalDate.now());
        String effectiveModelPreference = aiGatewayService.resolveModelPreference(policy.modelPreference());
        return new AiTaskExecutionContext(
                userId,
                user.tier(),
                effectiveModelPreference,
                true,
                0,
                pointsBalance,
                usedToday,
                0
        );
    }

    public long recordTaskSuccess(
            String traceId,
            long userId,
            AiTaskType taskType,
            String tier,
            int chargedPoints,
            AiGatewayService.AiGatewayResult gatewayResult
    ) {
        return recordTaskSuccess(traceId, userId, taskType, tier, chargedPoints, gatewayResult, null, 1, null, null);
    }

    public long recordTaskSuccess(
            String traceId,
            long userId,
            AiTaskType taskType,
            String tier,
            int chargedPoints,
            AiGatewayService.AiGatewayResult gatewayResult,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson
    ) {
        return recordTaskSuccess(traceId, userId, taskType, tier, chargedPoints, gatewayResult, null, quotaWeight, resultSummary, resultPayloadJson);
    }

    private long recordTaskSuccess(
            String traceId,
            long userId,
            AiTaskType taskType,
            String tier,
            int chargedPoints,
            AiGatewayService.AiGatewayResult gatewayResult,
            String billedSceneCode,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson
    ) {
        long callLogId = aiQuotaRepository.insertCallLog(
                traceId,
                userId,
                taskType.name(),
                gatewayResult.sceneCode(),
                gatewayResult.provider(),
                gatewayResult.model(),
                gatewayResult.routeCode(),
                gatewayResult.routePolicyCode(),
                gatewayResult.latencyMs(),
                "SUCCESS",
                null,
                gatewayResult.requestTokens(),
                gatewayResult.responseTokens(),
                gatewayResult.totalTokens(),
                gatewayResult.thoughtsTokens(),
                gatewayResult.reasoningEffort(),
                gatewayResult.thinkingBudget(),
                gatewayResult.thinkingLevel(),
                gatewayResult.estimatedCost(),
                chargedPoints,
                quotaWeight,
                resultSummary,
                resultPayloadJson,
                tier
        );
        log.info(
                "ai task success traceId={}, userId={}, taskType={}, provider={}, model={}, latencyMs={}, requestTokens={}, responseTokens={}, totalTokens={}, thoughtsTokens={}, reasoningEffort={}, thinkingBudget={}, thinkingLevel={}, estimatedCost={}, quotaWeight={}, callLogId={}",
                traceId,
                userId,
                taskType.name(),
                gatewayResult.provider(),
                gatewayResult.model(),
                gatewayResult.latencyMs(),
                gatewayResult.requestTokens(),
                gatewayResult.responseTokens(),
                gatewayResult.totalTokens(),
                gatewayResult.thoughtsTokens(),
                gatewayResult.reasoningEffort(),
                gatewayResult.thinkingBudget(),
                gatewayResult.thinkingLevel(),
                gatewayResult.estimatedCost().toPlainString(),
                quotaWeight,
                callLogId
        );
        aiQuotaUsageCounterService.recordSuccess(resolveUserScope(userId), taskType.name(), billedSceneCode, LocalDate.now(), quotaWeight);
        evictOperationsDashboardCache();
        return callLogId;
    }

    private ExecutionPreparation prepareExecution(
            long userId,
            String traceId,
            AiTaskType taskType,
            String sceneCode,
            Set<UserRole> allowedRoles,
            String notFoundMessage
    ) {
        AppUser user = loadUser(userId, allowedRoles, notFoundMessage);
        AiQuotaPolicyRepository.QuotaPolicyRow policy = findPolicy(user.tier(), taskType, sceneCode);
        enforceMinuteRateLimit(traceId, user, taskType.name(), sceneCode, policy.modelPreference());

        int usedToday = getUsedToday(buildUserScope(user), userId, taskType.name(), policy.sceneCode(), LocalDate.now());
        int chargedPoints = 0;
        boolean freeCall = true;

        if (!"PREMIUM".equalsIgnoreCase(user.tier())) {
            if (policy.dailyMaxLimit() >= 0 && usedToday >= policy.dailyMaxLimit()) {
                recordRejectedCall(
                        traceId,
                        userId,
                        taskType.name(),
                        sceneCode,
                        user.tier(),
                        aiGatewayService.resolveModelPreference(policy.modelPreference()),
                        "AI-2201",
                        "quota_exhausted"
                );
                throw new ApiException("AI-2201", "额度不足，完成任务赚取积分", HttpStatus.BAD_REQUEST);
            }
            if (policy.dailyFreeLimit() >= 0 && usedToday >= policy.dailyFreeLimit()) {
                int currentBalance = growthRepository.getCurrentBalance(userId);
                if (currentBalance < policy.pointsPerCall()) {
                    recordRejectedCall(
                            traceId,
                            userId,
                            taskType.name(),
                            sceneCode,
                            user.tier(),
                            aiGatewayService.resolveModelPreference(policy.modelPreference()),
                            "AI-2201",
                            "quota_exhausted"
                    );
                    throw new ApiException("AI-2201", "额度不足，完成任务赚取积分", HttpStatus.BAD_REQUEST);
                }
                chargedPoints = policy.pointsPerCall();
                freeCall = false;
                growthRepository.appendPointsLedger(userId, -chargedPoints, "AI_" + taskType.name());
                evictGrowthPointsLedgerSummary(userId);
            }
        }
        return new ExecutionPreparation(user, policy, usedToday, freeCall, chargedPoints);
    }

    private ExecutionPreparation prepareInterviewSessionReservation(long userId, String traceId, int reservedQuotaWeight) {
        AppUser user = loadStudentUser(userId);
        AiQuotaPolicyRepository.QuotaPolicyRow policy = findPolicy(user.tier(), AiTaskType.INTERVIEW_TEXT);
        enforceMinuteRateLimit(traceId, user, AiTaskType.INTERVIEW_TEXT.name(), null, policy.modelPreference());
        int usedToday = getUsedToday(buildUserScope(user), userId, AiTaskType.INTERVIEW_TEXT.name(), policy.sceneCode(), LocalDate.now());

        if (policy.dailyMaxLimit() >= 0 && usedToday + reservedQuotaWeight > policy.dailyMaxLimit()) {
            recordRejectedCall(
                    traceId,
                    userId,
                    AiTaskType.INTERVIEW_TEXT.name(),
                    null,
                    user.tier(),
                    aiGatewayService.resolveModelPreference(policy.modelPreference()),
                    "AI-2201",
                    "quota_exhausted"
            );
            throw new ApiException("AI-2201", "当日额度不足以开启完整面试会话", HttpStatus.BAD_REQUEST);
        }

        int chargedPoints = 0;
        boolean freeCall = true;
        if (!"PREMIUM".equalsIgnoreCase(user.tier()) && policy.dailyFreeLimit() >= 0) {
            int freeRemaining = Math.max(policy.dailyFreeLimit() - usedToday, 0);
            int billableUnits = Math.max(reservedQuotaWeight - freeRemaining, 0);
            chargedPoints = billableUnits * policy.pointsPerCall();
            freeCall = chargedPoints == 0;
            int currentBalance = growthRepository.getCurrentBalance(userId);
            if (currentBalance < chargedPoints) {
                recordRejectedCall(
                        traceId,
                        userId,
                        AiTaskType.INTERVIEW_TEXT.name(),
                        null,
                        user.tier(),
                        aiGatewayService.resolveModelPreference(policy.modelPreference()),
                        "AI-2201",
                        "quota_exhausted"
                );
                throw new ApiException("AI-2201", "积分不足以开启完整面试会话", HttpStatus.BAD_REQUEST);
            }
            if (chargedPoints > 0) {
                growthRepository.appendPointsLedger(userId, -chargedPoints, INTERVIEW_SESSION_REASON_CODE);
                evictGrowthPointsLedgerSummary(userId);
            }
        }

        return new ExecutionPreparation(user, policy, usedToday, freeCall, chargedPoints);
    }

    private AppUser loadStudentUser(long userId) {
        return loadUser(userId, EnumSet.of(UserRole.STUDENT), "student not found");
    }

    private AppUser loadUser(long userId, Set<UserRole> allowedRoles, String notFoundMessage) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", notFoundMessage, HttpStatus.NOT_FOUND));
        if (user.role() == null || allowedRoles == null || !allowedRoles.contains(user.role())) {
            throw new ApiException("BIZ-1002", notFoundMessage, HttpStatus.NOT_FOUND);
        }
        return user;
    }

    private AiQuotaPolicyRepository.QuotaPolicyRow findPolicy(String tier, AiTaskType taskType) {
        return findPolicy(tier, taskType, null);
    }

    private AiQuotaPolicyRepository.QuotaPolicyRow findPolicy(String tier, AiTaskType taskType, String sceneCode) {
        return aiQuotaPolicyRepository.findPolicy(tier, taskType.name(), sceneCode)
                .orElseThrow(() -> new ApiException("BIZ-1002", "quota policy not found", HttpStatus.NOT_FOUND));
    }

    private AiTaskType parseTaskType(String rawValue) {
        try {
            return AiTaskType.from(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "taskType invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private int getUsedToday(String userScope, long userId, String taskType, String sceneCode, LocalDate targetDate) {
        return aiQuotaUsageCounterService.getUsedToday(
                userScope,
                taskType,
                sceneCode,
                targetDate,
                () -> aiQuotaRepository.countSuccessfulCallsToday(userId, taskType, sceneCode, targetDate)
        );
    }

    private String resolveUserScope(long userId) {
        return userRepository.findById(userId)
                .map(this::buildUserScope)
                .orElse("user-" + userId);
    }

    private String buildUserScope(AppUser user) {
        long createdAtEpochMs = user.createdAt() == null ? 0L : user.createdAt().toEpochMilli();
        return "user-" + user.id() + "-" + createdAtEpochMs;
    }

    private void evictGrowthPointsLedgerSummary(long userId) {
        growthCenterCacheService.evictPointsLedgerSummaryNow(userId);
        growthCenterCacheService.evictPointsLedgerSummaryAfterCommit(userId);
    }

    private void enforceMinuteRateLimit(String traceId, AppUser user, String taskType, String sceneCode, String modelPreference) {
        AiMinuteRateLimitService.RateLimitDecision decision = aiMinuteRateLimitService.acquire(
                buildUserScope(user),
                user.tier(),
                taskType,
                traceId
        );
        if (decision.allowed()) {
            return;
        }
        String resolvedModelPreference = aiGatewayService.resolveModelPreference(modelPreference);
        recordRejectedCall(
                traceId,
                user.id(),
                taskType,
                sceneCode,
                user.tier(),
                resolvedModelPreference,
                "AI-2202",
                "minute_rate_limited"
        );
        throw new ApiException(
                "AI-2202",
                "AI 调用过于频繁，请稍后重试",
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of(
                        "retryAfterSeconds", decision.retryAfterSeconds(),
                        "limitPerMinute", decision.limit(),
                        "windowSeconds", 60
                ),
                traceId
        );
    }

    private void recordRejectedCall(
            String traceId,
            long userId,
            String taskType,
            String sceneCode,
            String tier,
            String modelPreference,
            String errorCode,
            String reason
    ) {
        log.warn(
                "ai task rejected traceId={}, userId={}, taskType={}, requestedSceneCode={}, tier={}, quotaModelPreference={}, reason={}, errorCode={}",
                traceId,
                userId,
                taskType,
                normalizeSceneCode(sceneCode),
                tier,
                modelPreference,
                reason,
                errorCode
        );
        aiQuotaRepository.insertCallLog(
                traceId,
                userId,
                taskType,
                normalizeSceneCode(sceneCode),
                "mock-provider",
                modelPreference,
                null,
                null,
                0,
                "REJECTED",
                errorCode,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                BigDecimal.ZERO,
                0,
                0,
                null,
                null,
                tier
        );
        evictOperationsDashboardCache();
    }

    private String normalizeSceneCode(String sceneCode) {
        if (!StringUtils.hasText(sceneCode)) {
            return null;
        }
        return sceneCode.trim().toUpperCase();
    }

    @FunctionalInterface
    public interface AiTaskExecutor<T> {
        AiTaskExecution<T> execute(AiTaskExecutionContext context);
    }

    public record AiTaskExecutionContext(
            long userId,
            String tier,
            String modelPreference,
            boolean freeCall,
            int chargedPoints,
            int pointsBalance,
            int usedTodayAfterSuccess,
            int quotaWeight
    ) {
    }

    public record AiTaskExecution<T>(
            T payload,
            AiGatewayService.AiGatewayResult gatewayResult,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson
    ) {
        public AiTaskExecution(T payload, AiGatewayService.AiGatewayResult gatewayResult) {
            this(payload, gatewayResult, 1, null, null);
        }

        public AiTaskExecution(T payload, AiGatewayService.AiGatewayResult gatewayResult, String resultSummary, String resultPayloadJson) {
            this(payload, gatewayResult, 1, resultSummary, resultPayloadJson);
        }
    }

    private record ExecutionPreparation(
            AppUser user,
            AiQuotaPolicyRepository.QuotaPolicyRow policy,
            int usedToday,
            boolean freeCall,
            int chargedPoints
    ) {
    }

    private void evictOperationsDashboardCache() {
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
    }
}
