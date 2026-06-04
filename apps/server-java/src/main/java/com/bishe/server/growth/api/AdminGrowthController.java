package com.bishe.server.growth.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.growth.dto.AdminGrantPointsRequest;
import com.bishe.server.growth.dto.AdminGrantPointsResponse;
import com.bishe.server.growth.service.GrowthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员增长工具接口：当前仅提供测试积分补充能力。
 */
@Tag(name = "AdminGrowth", description = "管理员积分测试工具接口")
@RestController
@RequestMapping(path = "/api/v1/admin/growth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminGrowthController {

    private final GrowthService growthService;

    public AdminGrowthController(GrowthService growthService) {
        this.growthService = growthService;
    }

    @Operation(summary = "临时补充学生测试积分")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/points/grant", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminGrantPointsResponse> grantPoints(@Valid @RequestBody AdminGrantPointsRequest request) {
        AdminGrantPointsResponse data = growthService.grantPointsForTesting(request.userId(), request.points(), request.reasonCode());
        return ApiResponse.ok("points granted", data, TraceId.next());
    }
}
