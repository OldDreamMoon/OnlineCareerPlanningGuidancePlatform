package com.bishe.server.mentor.service;

import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.community.repository.CommunityRepository;
import com.bishe.server.community.service.CommunityPostCommentListCacheService;
import com.bishe.server.community.service.CommunityPostDetailCacheService;
import com.bishe.server.community.service.CommunityPostListCacheService;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.mentor.dto.MentorDashboardResponse;
import com.bishe.server.mentor.dto.MentorOwnProfileResponse;
import com.bishe.server.mentor.dto.MentorProfileUpdateRequest;
import com.bishe.server.mentor.dto.MentorServicePackageResponse;
import com.bishe.server.mentor.dto.MentorServicePackageUpdateItem;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.mentor.repository.MentorServicePackageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 导师工作台服务：主页概览与资料编辑。
 */
@Service
public class MentorWorkspaceService {

    private static final Set<String> ALLOWED_SERVICE_SCENES = Set.of(
            "简历诊断",
            "项目表达",
            "模拟面试复盘",
            "岗位方向选择",
            "校招投递策略",
            "转行 / 跨专业求职",
            "Offer 对比与决策"
    );

    private final MentorRepository mentorRepository;
    private final MentorServicePackageRepository mentorServicePackageRepository;
    private final ConsultRepository consultRepository;
    private final UserRepository userRepository;
    private final MentorAvatarService mentorAvatarService;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final MentorDashboardCacheService mentorDashboardCacheService;
    private final CommunityRepository communityRepository;
    private final CommunityPostListCacheService communityPostListCacheService;
    private final CommunityPostDetailCacheService communityPostDetailCacheService;
    private final CommunityPostCommentListCacheService communityPostCommentListCacheService;

    public MentorWorkspaceService(
            MentorRepository mentorRepository,
            MentorServicePackageRepository mentorServicePackageRepository,
            ConsultRepository consultRepository,
            UserRepository userRepository,
            MentorAvatarService mentorAvatarService,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService,
            MentorDashboardCacheService mentorDashboardCacheService,
            CommunityRepository communityRepository,
            CommunityPostListCacheService communityPostListCacheService,
            CommunityPostDetailCacheService communityPostDetailCacheService,
            CommunityPostCommentListCacheService communityPostCommentListCacheService
    ) {
        this.mentorRepository = mentorRepository;
        this.mentorServicePackageRepository = mentorServicePackageRepository;
        this.consultRepository = consultRepository;
        this.userRepository = userRepository;
        this.mentorAvatarService = mentorAvatarService;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.mentorDashboardCacheService = mentorDashboardCacheService;
        this.communityRepository = communityRepository;
        this.communityPostListCacheService = communityPostListCacheService;
        this.communityPostDetailCacheService = communityPostDetailCacheService;
        this.communityPostCommentListCacheService = communityPostCommentListCacheService;
    }

    public MentorDashboardResponse getDashboard(long mentorUserId) {
        // 首页摘要允许短缓存，订单中心和履约工作区仍读实时订单明细。
        MentorDashboardResponse dashboard = mentorDashboardCacheService.getDashboard(
                mentorUserId,
                () -> loadDashboardFromDatabase(mentorUserId)
        );
        if (dashboard == null) {
            throw new ApiException("BIZ-1002", "mentor profile not found", HttpStatus.NOT_FOUND);
        }
        return dashboard;
    }

    public MentorOwnProfileResponse getOwnProfile(long mentorUserId) {
        MentorRepository.MentorOwnProfileRow profile = mentorRepository.findOwnProfile(mentorUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "mentor profile not found", HttpStatus.NOT_FOUND));
        return toOwnProfileResponse(profile, loadOwnPackages(profile));
    }

    @Transactional
    public MentorOwnProfileResponse updateOwnProfile(long mentorUserId, MentorProfileUpdateRequest request) {
        MentorRepository.MentorOwnProfileRow currentProfile = mentorRepository.findOwnProfile(mentorUserId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "mentor profile not found", HttpStatus.NOT_FOUND));
        List<MentorServicePackageResponse> currentPackages = loadOwnPackages(currentProfile);

        String normalizedDisplayName = TextListCodec.normalizeText(request.displayName());
        if (request.displayName() != null && normalizedDisplayName == null) {
            throw new ApiException("BIZ-1001", "displayName required", HttpStatus.BAD_REQUEST);
        }
        if (normalizedDisplayName != null) {
            userRepository.updateDisplayName(mentorUserId, normalizedDisplayName);
        }

        String normalizedRealName = TextListCodec.normalizeText(request.realName());
        String normalizedCurrentRealName = TextListCodec.normalizeText(currentProfile.realName());
        if (request.realName() != null && !Objects.equals(normalizedRealName, normalizedCurrentRealName)) {
            // 认证后的真实姓名不允许在资料页被绕过审核修改。
            throw new ApiException("BIZ-1001", "realName immutable", HttpStatus.BAD_REQUEST);
        }
        String normalizedCompanyName = TextListCodec.normalizeText(request.companyName());
        String normalizedCurrentCompanyName = TextListCodec.normalizeText(currentProfile.companyName());
        if (request.companyName() != null && !Objects.equals(normalizedCompanyName, normalizedCurrentCompanyName)) {
            throw new ApiException("BIZ-1001", "companyName immutable", HttpStatus.BAD_REQUEST);
        }
        String effectiveDisplayName = normalizedDisplayName == null ? currentProfile.displayName() : normalizedDisplayName;
        boolean nextShowRealName = request.showRealName() == null ? currentProfile.showRealName() : request.showRealName();
        if (nextShowRealName && normalizedCurrentRealName == null) {
            throw new ApiException("BIZ-1001", "realName required when showRealName enabled", HttpStatus.BAD_REQUEST);
        }

        List<MentorServicePackageResponse> nextPackages = resolveNextPackages(currentProfile, currentPackages, request);
        int nextPriceFen = MentorServicePackageSupport.computeStartingPriceFen(nextPackages, currentProfile.priceFen());
        List<String> nextServiceSceneValues = request.serviceScenes() == null
                ? currentProfile.serviceScenes()
                : TextListCodec.normalize(request.serviceScenes());
        List<String> mergedServiceScenes = mergeAndValidateServiceScenes(nextServiceSceneValues, nextPackages);

        // 展示名和真名展示开关会影响社区作者卡片，因此单独判断是否清社区缓存。
        boolean publicIdentityChanged = !Objects.equals(currentProfile.displayName(), effectiveDisplayName)
                || currentProfile.showRealName() != nextShowRealName;

        mentorRepository.updateOwnProfile(
                mentorUserId,
                currentProfile.companyName(),
                TextListCodec.normalizeText(request.jobTitle()) == null ? currentProfile.jobTitle() : TextListCodec.normalizeText(request.jobTitle()),
                nextShowRealName,
                TextListCodec.normalizeText(request.avatarUrl()) == null ? currentProfile.avatarUrl() : TextListCodec.normalizeText(request.avatarUrl()),
                request.expertiseTags() == null ? TextListCodec.join(currentProfile.expertiseTags()) : TextListCodec.join(request.expertiseTags()),
                TextListCodec.join(mergedServiceScenes),
                TextListCodec.normalizeText(request.bio()) == null ? currentProfile.bio() : TextListCodec.normalizeText(request.bio()),
                TextListCodec.normalizeText(request.suitableFor()) == null ? currentProfile.suitableFor() : TextListCodec.normalizeText(request.suitableFor()),
                TextListCodec.normalizeText(request.notSuitableFor()) == null ? currentProfile.notSuitableFor() : TextListCodec.normalizeText(request.notSuitableFor()),
                TextListCodec.normalizeText(request.prepMaterials()) == null ? currentProfile.prepMaterials() : TextListCodec.normalizeText(request.prepMaterials()),
                TextListCodec.normalizeText(request.replyRhythm()) == null ? currentProfile.replyRhythm() : TextListCodec.normalizeText(request.replyRhythm()),
                nextPriceFen,
                request.available() == null ? currentProfile.available() : request.available()
        );
        if (request.packages() != null) {
            // 套餐按整组替换，前端负责排序，后端负责重新校验和落库。
            mentorServicePackageRepository.replacePackagesForMentor(
                    mentorUserId,
                    nextPackages.stream()
                            .map(item -> new MentorServicePackageRepository.CreatePackageCommand(
                                    item.packageName(),
                                    item.sceneCode(),
                                    item.sceneLabel(),
                                    item.deliveryMode(),
                                    item.durationMinutes(),
                                    item.priceFen(),
                                    item.description(),
                                    item.enabled(),
                                    item.sortNo()
                            ))
                            .toList()
            );
        }
        mentorPublicDetailCacheService.evictNow(mentorUserId);
        mentorPublicDetailCacheService.evictAfterCommit(mentorUserId);
        mentorPublicListCacheService.evictAllNow();
        mentorPublicListCacheService.evictAllAfterCommit();
        if (publicIdentityChanged) {
            evictMentorCommunityCaches(mentorUserId);
        }
        return getOwnProfile(mentorUserId);
    }

    private MentorOwnProfileResponse toOwnProfileResponse(
            MentorRepository.MentorOwnProfileRow profile,
            List<MentorServicePackageResponse> packages
    ) {
        return new MentorOwnProfileResponse(
                profile.userId(),
                profile.displayName(),
                profile.realName(),
                profile.showRealName(),
                profile.companyName(),
                profile.jobTitle(),
                mentorAvatarService.resolveAvatarUrl(
                        profile.userId(),
                        profile.avatarUrl(),
                        profile.displayName(),
                        profile.avatarObjectKey(),
                        profile.avatarUpdatedAt()
                ),
                profile.avatarObjectKey() != null || TextListCodec.normalizeText(profile.avatarUrl()) != null,
                profile.avatarContentType(),
                toIso(profile.avatarUpdatedAt()),
                profile.expertiseTags(),
                profile.serviceScenes(),
                profile.bio(),
                profile.suitableFor(),
                profile.notSuitableFor(),
                profile.prepMaterials(),
                profile.replyRhythm(),
                profile.priceFen(),
                packages,
                normalizeRating(profile.avgRating()),
                profile.totalOrders(),
                profile.available(),
                profile.approvalStatus()
        );
    }

    private MentorDashboardResponse loadDashboardFromDatabase(long mentorUserId) {
        MentorRepository.MentorOwnProfileRow profile = mentorRepository.findOwnProfile(mentorUserId).orElse(null);
        if (profile == null) {
            return null;
        }
        // 首页只取关键状态计数、收入和最近订单，重明细查询留给订单中心。
        long pendingPaidCount = consultRepository.countOrdersForMentor(mentorUserId, ConsultOrderStatus.PAID);
        long answeredCount = consultRepository.countOrdersForMentor(mentorUserId, ConsultOrderStatus.ANSWERED);
        long closedCount = consultRepository.countOrdersForMentor(mentorUserId, ConsultOrderStatus.CLOSED);
        int totalRevenueFen = consultRepository.sumPaidRevenueForMentor(mentorUserId);
        return new MentorDashboardResponse(
                pendingPaidCount,
                answeredCount,
                closedCount,
                totalRevenueFen,
                profile.totalOrders(),
                normalizeRating(profile.avgRating()),
                consultRepository.findRecentOrdersForMentor(mentorUserId, 5).stream()
                        .map(item -> new MentorDashboardResponse.RecentOrderItem(
                                item.orderNo(),
                                item.counterpartUserId(),
                                item.counterpartDisplayName(),
                                item.amountFen(),
                                item.status().name(),
                                item.questionText(),
                                toIso(item.createdAt()),
                                toIso(item.paidAt()),
                                toIso(item.closedAt())
                        ))
                        .toList()
        );
    }

    private BigDecimal normalizeRating(BigDecimal rating) {
        return rating == null ? BigDecimal.ZERO.setScale(2) : rating.setScale(2, RoundingMode.HALF_UP);
    }

    private Long toIso(java.time.Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private List<MentorServicePackageResponse> loadOwnPackages(MentorRepository.MentorOwnProfileRow profile) {
        return MentorServicePackageSupport.ensurePackages(
                MentorServicePackageSupport.toResponses(mentorServicePackageRepository.findPackagesByMentorUserId(profile.userId(), false)),
                profile.priceFen(),
                profile.serviceScenes()
        );
    }

    private List<MentorServicePackageResponse> resolveNextPackages(
            MentorRepository.MentorOwnProfileRow currentProfile,
            List<MentorServicePackageResponse> currentPackages,
            MentorProfileUpdateRequest request
    ) {
        if (request.packages() == null) {
            if (request.priceFen() == null) {
                return currentPackages;
            }
            int nextPriceFen = request.priceFen();
            if (nextPriceFen < 0) {
                throw new ApiException("BIZ-1001", "priceFen invalid", HttpStatus.BAD_REQUEST);
            }
            if (currentPackages.size() != 1) {
                throw new ApiException("BIZ-1001", "packages required when multiple packages configured", HttpStatus.BAD_REQUEST);
            }
            MentorServicePackageResponse current = currentPackages.get(0);
            return List.of(new MentorServicePackageResponse(
                    current.id(),
                    current.packageName(),
                    current.sceneCode(),
                    current.sceneLabel(),
                    current.deliveryMode(),
                    current.durationMinutes(),
                    nextPriceFen,
                    current.description(),
                    current.enabled(),
                    current.sortNo()
            ));
        }
        return normalizePackages(request.packages(), currentProfile.priceFen(), currentProfile.serviceScenes());
    }

    private List<MentorServicePackageResponse> normalizePackages(
            List<MentorServicePackageUpdateItem> packages,
            int fallbackPriceFen,
            List<String> serviceScenes
    ) {
        if (packages == null || packages.isEmpty()) {
            throw new ApiException("BIZ-1001", "packages required", HttpStatus.BAD_REQUEST);
        }
        if (packages.size() > 4) {
            throw new ApiException("BIZ-1001", "packages too many", HttpStatus.BAD_REQUEST);
        }

        int enabledCount = 0;
        int appointmentCount = 0;
        java.util.ArrayList<MentorServicePackageResponse> normalized = new java.util.ArrayList<>();
        for (int index = 0; index < packages.size(); index++) {
            MentorServicePackageUpdateItem item = packages.get(index);
            String packageName = TextListCodec.normalizeText(item.packageName());
            String sceneLabel = TextListCodec.normalizeText(item.sceneLabel());
            String deliveryMode = TextListCodec.normalizeText(item.deliveryMode());
            String description = TextListCodec.normalizeText(item.description());
            boolean enabled = item.enabled() == null || item.enabled();
            int priceFen = item.priceFen() == null ? fallbackPriceFen : item.priceFen();

            if (packageName == null) {
                throw new ApiException("BIZ-1001", "packageName required", HttpStatus.BAD_REQUEST);
            }
            if (sceneLabel == null || !ALLOWED_SERVICE_SCENES.contains(sceneLabel)) {
                throw new ApiException("BIZ-1001", "package scene invalid", HttpStatus.BAD_REQUEST);
            }
            if (priceFen < 0) {
                throw new ApiException("BIZ-1001", "package price invalid", HttpStatus.BAD_REQUEST);
            }
            if (!MentorServicePackageSupport.DELIVERY_MODE_TEXT_ASYNC.equals(deliveryMode)
                    && !MentorServicePackageSupport.DELIVERY_MODE_APPOINTMENT.equals(deliveryMode)) {
                throw new ApiException("BIZ-1001", "deliveryMode invalid", HttpStatus.BAD_REQUEST);
            }

            Integer durationMinutes = item.durationMinutes();
            if (MentorServicePackageSupport.DELIVERY_MODE_APPOINTMENT.equals(deliveryMode)) {
                appointmentCount++;
                if (durationMinutes == null) {
                    throw new ApiException("BIZ-1001", "durationMinutes required", HttpStatus.BAD_REQUEST);
                }
            } else {
                durationMinutes = null;
            }

            if (enabled) {
                enabledCount++;
            }

            String sceneCode = MentorServicePackageSupport.resolveSceneCode(sceneLabel);
            String requestedSceneCode = TextListCodec.normalizeText(item.sceneCode());
            if (requestedSceneCode != null && !sceneCode.equals(requestedSceneCode)) {
                throw new ApiException("BIZ-1001", "sceneCode mismatch", HttpStatus.BAD_REQUEST);
            }

            normalized.add(new MentorServicePackageResponse(
                    index + 1L,
                    packageName,
                    sceneCode,
                    sceneLabel,
                    deliveryMode,
                    durationMinutes,
                    priceFen,
                    description,
                    enabled,
                    index + 1
            ));
        }

        if (enabledCount <= 0) {
            throw new ApiException("BIZ-1001", "at least one enabled package required", HttpStatus.BAD_REQUEST);
        }
        if (appointmentCount > 1) {
            throw new ApiException("BIZ-1001", "only one appointment package allowed", HttpStatus.BAD_REQUEST);
        }
        return normalized.isEmpty()
                ? List.of(MentorServicePackageSupport.buildFallbackPackage(fallbackPriceFen, serviceScenes))
                : List.copyOf(normalized);
    }

    private List<String> mergeAndValidateServiceScenes(List<String> serviceScenes, List<MentorServicePackageResponse> packages) {
        List<String> normalized = MentorServicePackageSupport.mergeServiceScenes(serviceScenes, packages);
        for (String scene : normalized) {
            if (!ALLOWED_SERVICE_SCENES.contains(scene)) {
                throw new ApiException("BIZ-1001", "serviceScenes invalid", HttpStatus.BAD_REQUEST);
            }
        }
        return normalized;
    }

    private void evictMentorCommunityCaches(long mentorUserId) {
        LinkedHashSet<Long> affectedPostIds = new LinkedHashSet<>();
        affectedPostIds.addAll(communityRepository.findVisiblePostIdsByAuthorUserId(mentorUserId));
        affectedPostIds.addAll(communityRepository.findVisibleCommentedPostIdsByUserId(mentorUserId));

        communityPostListCacheService.evictAllNow();
        communityPostListCacheService.evictAllAfterCommit();
        affectedPostIds.forEach((postId) -> {
            communityPostDetailCacheService.evictNow(postId);
            communityPostDetailCacheService.evictAfterCommit(postId);
            communityPostCommentListCacheService.evictNow(postId);
            communityPostCommentListCacheService.evictAfterCommit(postId);
        });
    }
}
