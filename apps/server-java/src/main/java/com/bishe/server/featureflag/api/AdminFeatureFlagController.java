package com.bishe.server.featureflag.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员功能开关接口。
 */
@Tag(name = "AdminFeatureFlags", description = "管理员维护支付、语音与社区 AI 开关")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/feature-flags", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public AdminFeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Operation(summary = "查询功能开关列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<FeatureFlagService.FeatureFlagListPayload> listFlags() {
        return ApiResponse.ok(featureFlagService.listFlags(), TraceId.next());
    }

    @Operation(summary = "更新功能开关")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<FeatureFlagService.FeatureFlagItem> updateFlag(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminFeatureFlagUpsertRequest request
    ) {
        String traceId = TraceId.next();
        FeatureFlagService.FeatureFlagItem data = featureFlagService.updateFlag(principal.getUserId(), request.toCommand());
        return ApiResponse.ok("feature flag updated", data, traceId);
    }
}
