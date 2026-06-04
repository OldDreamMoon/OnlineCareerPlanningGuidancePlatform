package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 更新学生隐私矩阵请求。
 */
@Schema(description = "更新学生隐私矩阵请求")
public record StudentProfilePrivacyUpdateRequest(
        @Valid
        @NotNull(message = "privacy is required")
        StudentProfileResponse.PrivacySettingsPayload privacy
) {
}
