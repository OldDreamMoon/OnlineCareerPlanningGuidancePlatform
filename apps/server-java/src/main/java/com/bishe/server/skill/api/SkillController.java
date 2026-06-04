package com.bishe.server.skill.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.security.UserPrincipal;
import com.bishe.server.skill.dto.SkillProgressUpdateRequest;
import com.bishe.server.skill.dto.SkillProgressUpdateResponse;
import com.bishe.server.skill.dto.SkillTreeResponse;
import com.bishe.server.skill.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技能树与进度接口。
 */
@Tag(name = "Skills", description = "技能树与学习进度接口")
@RestController
@RequestMapping(path = "/api/v1/skills", produces = MediaType.APPLICATION_JSON_VALUE)
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @Operation(summary = "获取技能树")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/tree")
    public ApiResponse<SkillTreeResponse> getSkillTree(@AuthenticationPrincipal UserPrincipal principal) {
        // 技能树返回结构节点、学生进度、资源和推荐关系的合成结果。
        return ApiResponse.ok(skillService.getSkillTree(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "更新技能进度")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/progress", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SkillProgressUpdateResponse> updateSkillProgress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SkillProgressUpdateRequest request
    ) {
        // 进度更新会校验前置节点，并触发画像和成长缓存刷新。
        SkillProgressUpdateResponse data = skillService.updateProgress(principal.getUserId(), request);
        return ApiResponse.ok("progress updated", data, TraceId.next());
    }
}
