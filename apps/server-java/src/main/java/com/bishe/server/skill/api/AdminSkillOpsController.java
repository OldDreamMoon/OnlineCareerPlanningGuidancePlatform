package com.bishe.server.skill.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.skill.dto.AdminSkillNodeCreateRequest;
import com.bishe.server.skill.dto.AdminSkillNodeUpdateRequest;
import com.bishe.server.skill.dto.AdminSkillRelationCreateRequest;
import com.bishe.server.skill.dto.AdminSkillRelationUpdateRequest;
import com.bishe.server.skill.dto.AdminSkillResourceCreateRequest;
import com.bishe.server.skill.dto.AdminSkillResourceUpdateRequest;
import com.bishe.server.skill.dto.SkillTreeResponse;
import com.bishe.server.skill.service.AdminSkillOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Skill Ops", description = "管理员技能资源治理接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/skills", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminSkillOpsController {

    private final AdminSkillOpsService adminSkillOpsService;

    public AdminSkillOpsController(AdminSkillOpsService adminSkillOpsService) {
        this.adminSkillOpsService = adminSkillOpsService;
    }

    @Operation(summary = "获取技能资源治理概览")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview")
    public ApiResponse<AdminSkillOpsService.SkillOpsOverviewPayload> getOverview() {
        return ApiResponse.ok(adminSkillOpsService.getOverview(), TraceId.next());
    }

    @Operation(summary = "获取技能节点治理列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/nodes")
    public ApiResponse<AdminSkillOpsService.SkillNodeListPayload> listNodes() {
        return ApiResponse.ok(adminSkillOpsService.listNodes(), TraceId.next());
    }

    @Operation(summary = "获取管理员技能星图预览树")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/preview-tree")
    public ApiResponse<SkillTreeResponse> getPreviewTree() {
        // 管理员预览树不绑定具体学生进度，主要用于验证节点、资源和关系结构。
        return ApiResponse.ok(adminSkillOpsService.getPreviewTree(), TraceId.next());
    }

    @Operation(summary = "创建技能节点")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/nodes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminSkillOpsService.SkillNodeOpsItem> createNode(
            @Valid @RequestBody AdminSkillNodeCreateRequest request
    ) {
        // 节点创建会校验 parentCode 和 nodeCode 唯一性，避免破坏技能树结构。
        return ApiResponse.ok("admin skill node created", adminSkillOpsService.createNode(request), TraceId.next());
    }

    @Operation(summary = "更新技能节点")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/nodes/{nodeCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminSkillOpsService.SkillNodeOpsItem> updateNode(
            @PathVariable String nodeCode,
            @Valid @RequestBody AdminSkillNodeUpdateRequest request
    ) {
        return ApiResponse.ok("admin skill node updated", adminSkillOpsService.updateNode(nodeCode, request), TraceId.next());
    }

    @Operation(summary = "删除技能节点")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/nodes/{nodeCode}")
    public ApiResponse<Void> deleteNode(@PathVariable String nodeCode) {
        // 删除前要求没有子节点、资源和关系，避免学生端进度路径悬空。
        adminSkillOpsService.deleteNode(nodeCode);
        return ApiResponse.ok("admin skill node deleted", null, TraceId.next());
    }

    @Operation(summary = "获取技能资源治理列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/resources")
    public ApiResponse<AdminSkillOpsService.SkillResourceListPayload> listResources() {
        return ApiResponse.ok(adminSkillOpsService.listResources(), TraceId.next());
    }

    @Operation(summary = "创建技能资源")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/resources", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminSkillOpsService.SkillResourceOpsItem> createResource(
            @Valid @RequestBody AdminSkillResourceCreateRequest request
    ) {
        // 资源必须挂到有效节点，学生星图详情页按节点读取推荐资源。
        return ApiResponse.ok("admin skill resource created", adminSkillOpsService.createResource(request), TraceId.next());
    }

    @Operation(summary = "更新技能资源")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/resources/{resourceCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminSkillOpsService.SkillResourceOpsItem> updateResource(
            @PathVariable String resourceCode,
            @Valid @RequestBody AdminSkillResourceUpdateRequest request
    ) {
        return ApiResponse.ok("admin skill resource updated", adminSkillOpsService.updateResource(resourceCode, request), TraceId.next());
    }

    @Operation(summary = "创建技能关联")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/relations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminSkillOpsService.SkillRelationOpsItem> createRelation(
            @Valid @RequestBody AdminSkillRelationCreateRequest request
    ) {
        // 关系边用于星图推荐路径展示，不改变树形父子结构。
        return ApiResponse.ok("admin skill relation created", adminSkillOpsService.createRelation(request), TraceId.next());
    }

    @Operation(summary = "更新技能关联")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/relations/{sourceNodeCode}/{targetNodeCode}/{relationType}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminSkillOpsService.SkillRelationOpsItem> updateRelation(
            @PathVariable String sourceNodeCode,
            @PathVariable String targetNodeCode,
            @PathVariable String relationType,
            @Valid @RequestBody AdminSkillRelationUpdateRequest request
    ) {
        return ApiResponse.ok(
                "admin skill relation updated",
                adminSkillOpsService.updateRelation(sourceNodeCode, targetNodeCode, relationType, request),
                TraceId.next()
        );
    }

    @Operation(summary = "删除技能关联")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/relations/{sourceNodeCode}/{targetNodeCode}/{relationType}")
    public ApiResponse<Void> deleteRelation(
            @PathVariable String sourceNodeCode,
            @PathVariable String targetNodeCode,
            @PathVariable String relationType
    ) {
        adminSkillOpsService.deleteRelation(sourceNodeCode, targetNodeCode, relationType);
        return ApiResponse.ok("admin skill relation deleted", null, TraceId.next());
    }
}
