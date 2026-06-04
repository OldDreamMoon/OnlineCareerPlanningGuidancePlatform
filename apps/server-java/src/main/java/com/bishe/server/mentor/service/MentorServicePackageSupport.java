package com.bishe.server.mentor.service;

import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.mentor.dto.MentorServicePackageResponse;
import com.bishe.server.mentor.repository.MentorServicePackageRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 导师服务套餐公共规则与组装。
 */
public final class MentorServicePackageSupport {

    public static final String DELIVERY_MODE_TEXT_ASYNC = "TEXT_ASYNC";
    public static final String DELIVERY_MODE_APPOINTMENT = "APPOINTMENT";
    public static final Map<String, String> SCENE_CODE_MAP = Map.of(
            "简历诊断", "RESUME_DIAGNOSIS",
            "项目表达", "PROJECT_STORYTELLING",
            "模拟面试复盘", "MOCK_INTERVIEW_REVIEW",
            "岗位方向选择", "CAREER_DIRECTION",
            "校招投递策略", "CAMPUS_RECRUITMENT_STRATEGY",
            "转行 / 跨专业求职", "CAREER_TRANSITION",
            "Offer 对比与决策", "OFFER_DECISION"
    );
    private static final String DEFAULT_SCENE_LABEL = "简历诊断";
    private static final String DEFAULT_PACKAGE_NAME = "标准图文咨询";
    private static final String DEFAULT_PACKAGE_DESCRIPTION = "适合先梳理问题与材料，由导师给出正式文字建议和下一步行动方向。";

    private MentorServicePackageSupport() {
    }

    public static MentorServicePackageResponse toResponse(MentorServicePackageRepository.MentorServicePackageRow row) {
        return new MentorServicePackageResponse(
                row.id(),
                row.packageName(),
                row.sceneCode(),
                row.sceneLabel(),
                row.deliveryMode(),
                row.durationMinutes(),
                row.priceFen(),
                row.description(),
                row.enabled(),
                row.sortNo()
        );
    }

    public static List<MentorServicePackageResponse> toResponses(List<MentorServicePackageRepository.MentorServicePackageRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(MentorServicePackageSupport::toResponse)
                .toList();
    }

    public static List<MentorServicePackageResponse> ensurePackages(
            List<MentorServicePackageResponse> packages,
            int fallbackPriceFen,
            List<String> serviceScenes
    ) {
        if (packages != null && !packages.isEmpty()) {
            return packages;
        }
        return List.of(buildFallbackPackage(fallbackPriceFen, serviceScenes));
    }

    public static MentorServicePackageResponse buildFallbackPackage(int fallbackPriceFen, List<String> serviceScenes) {
        String sceneLabel = pickFallbackSceneLabel(serviceScenes);
        return new MentorServicePackageResponse(
                0L,
                DEFAULT_PACKAGE_NAME,
                resolveSceneCode(sceneLabel),
                sceneLabel,
                DELIVERY_MODE_TEXT_ASYNC,
                null,
                Math.max(fallbackPriceFen, 0),
                DEFAULT_PACKAGE_DESCRIPTION,
                true,
                1
        );
    }

    public static int computeStartingPriceFen(List<MentorServicePackageResponse> packages, int fallbackPriceFen) {
        return packages == null || packages.isEmpty()
                ? Math.max(fallbackPriceFen, 0)
                : packages.stream()
                .filter(MentorServicePackageResponse::enabled)
                .mapToInt(MentorServicePackageResponse::priceFen)
                .min()
                .orElse(Math.max(fallbackPriceFen, 0));
    }

    public static String pickFallbackSceneLabel(List<String> serviceScenes) {
        if (serviceScenes != null) {
            for (String scene : serviceScenes) {
                String normalized = TextListCodec.normalizeText(scene);
                if (normalized != null && SCENE_CODE_MAP.containsKey(normalized)) {
                    return normalized;
                }
            }
        }
        return DEFAULT_SCENE_LABEL;
    }

    public static List<String> mergeServiceScenes(List<String> baseScenes, List<MentorServicePackageResponse> packages) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (baseScenes != null) {
            baseScenes.stream()
                    .map(TextListCodec::normalizeText)
                    .filter(value -> value != null && SCENE_CODE_MAP.containsKey(value))
                    .forEach(merged::add);
        }
        if (packages != null) {
            packages.stream()
                    .filter(MentorServicePackageResponse::enabled)
                    .map(MentorServicePackageResponse::sceneLabel)
                    .map(TextListCodec::normalizeText)
                    .filter(value -> value != null && SCENE_CODE_MAP.containsKey(value))
                    .forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    public static String resolveSceneCode(String sceneLabel) {
        return SCENE_CODE_MAP.getOrDefault(TextListCodec.normalizeText(sceneLabel), SCENE_CODE_MAP.get(DEFAULT_SCENE_LABEL));
    }
}
