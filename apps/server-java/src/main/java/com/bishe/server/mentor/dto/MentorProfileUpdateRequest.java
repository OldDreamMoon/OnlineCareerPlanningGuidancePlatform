package com.bishe.server.mentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 导师资料更新请求。
 */
public record MentorProfileUpdateRequest(
        @Size(max = 100, message = "displayName too long")
        String displayName,
        @Size(max = 100, message = "realName too long")
        String realName,
        Boolean showRealName,
        @Size(max = 200, message = "companyName too long")
        String companyName,
        @Size(max = 100, message = "jobTitle too long")
        String jobTitle,
        @Size(max = 255, message = "avatarUrl too long")
        String avatarUrl,
        @Size(max = 8, message = "expertiseTags too many")
        List<@Size(max = 20, message = "expertise tag too long") String> expertiseTags,
        @Size(max = 8, message = "serviceScenes too many")
        List<@Size(max = 30, message = "service scene too long") String> serviceScenes,
        @Size(max = 500, message = "bio too long")
        String bio,
        @Size(max = 500, message = "suitableFor too long")
        String suitableFor,
        @Size(max = 500, message = "notSuitableFor too long")
        String notSuitableFor,
        @Size(max = 1000, message = "prepMaterials too long")
        String prepMaterials,
        @Size(max = 500, message = "replyRhythm too long")
        String replyRhythm,
        Integer priceFen,
        @Size(max = 4, message = "packages too many")
        List<@Valid MentorServicePackageUpdateItem> packages,
        Boolean available
) {
}
