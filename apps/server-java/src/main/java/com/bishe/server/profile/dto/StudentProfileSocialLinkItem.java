package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 学生资料中心的外部主页账号项。
 */
@Schema(description = "学生资料中心外部主页账号项")
public record StudentProfileSocialLinkItem(
        @Schema(description = "平台编码", example = "GITHUB")
        @NotBlank(message = "socialLink.platform is required")
        @Size(max = 40, message = "socialLink.platform too long")
        String platform,
        @Schema(description = "平台账号或主页链接", example = "lin-frontend")
        @NotBlank(message = "socialLink.value is required")
        @Size(max = 255, message = "socialLink.value too long")
        String value
) {
}
