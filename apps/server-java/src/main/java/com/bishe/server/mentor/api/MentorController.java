package com.bishe.server.mentor.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.mentor.dto.MentorDetailResponse;
import com.bishe.server.mentor.dto.MentorFavoriteToggleResponse;
import com.bishe.server.mentor.dto.MentorFavoritesResponse;
import com.bishe.server.mentor.dto.MentorListResponse;
import com.bishe.server.mentor.dto.MentorPrepSheetGenerateRequest;
import com.bishe.server.mentor.dto.MentorPrepSheetGenerateResponse;
import com.bishe.server.mentor.dto.MentorRecommendationsResponse;
import com.bishe.server.mentor.service.MentorAvatarService;
import com.bishe.server.mentor.service.MentorService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导师浏览接口：学生端列表与详情。
 */
@Tag(name = "Mentor", description = "导师列表与详情接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/mentors", produces = MediaType.APPLICATION_JSON_VALUE)
public class MentorController {

    private final MentorService mentorService;
    private final MentorAvatarService mentorAvatarService;

    public MentorController(MentorService mentorService, MentorAvatarService mentorAvatarService) {
        this.mentorService = mentorService;
        this.mentorAvatarService = mentorAvatarService;
    }

    @Operation(summary = "获取导师列表")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public ApiResponse<MentorListResponse> listMentors(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String expertise,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Boolean favorited
    ) {
        // 导师广场列表先按公开资料筛选，再合成当前学生收藏态。
        return ApiResponse.ok(
                mentorService.listMentors(
                        principal.getUserId(),
                        page,
                        size,
                        keyword,
                        expertise,
                        scene,
                        minPrice,
                        maxPrice,
                        available,
                        favorited
                ),
                TraceId.next()
        );
    }

    @Operation(summary = "获取导师推荐结果")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/recommendations")
    public ApiResponse<MentorRecommendationsResponse> getRecommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String expertise,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Boolean favorited
    ) {
        // 推荐结果额外引入学生画像、向量召回和可解释理由，和普通列表分开查询。
        return ApiResponse.ok(
                mentorService.getRecommendations(
                        principal.getUserId(),
                        keyword,
                        expertise,
                        scene,
                        minPrice,
                        maxPrice,
                        available,
                        favorited
                ),
                TraceId.next()
        );
    }

    @Operation(summary = "获取我的收藏导师摘要")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/favorites")
    public ApiResponse<MentorFavoritesResponse> getFavorites(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(mentorService.getFavorites(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "获取导师详情")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/{mentorUserId}")
    public ApiResponse<MentorDetailResponse> getMentorDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long mentorUserId
    ) {
        // 导师详情用于广场右侧面板和创单页承接，保留学生 viewer 态。
        return ApiResponse.ok(mentorService.getMentorDetail(principal.getUserId(), mentorUserId), TraceId.next());
    }

    @Operation(summary = "读取导师公开头像")
    @GetMapping(path = "/{mentorUserId}/avatar", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> getPublicAvatar(@PathVariable long mentorUserId) {
        MentorAvatarService.StoredAvatarContent content = mentorAvatarService.readPublicAvatar(mentorUserId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }

    @Operation(summary = "收藏导师")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{mentorUserId}/favorite")
    public ApiResponse<MentorFavoriteToggleResponse> favoriteMentor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long mentorUserId
    ) {
        return ApiResponse.ok("favorited", mentorService.favoriteMentor(principal.getUserId(), mentorUserId), TraceId.next());
    }

    @Operation(summary = "取消收藏导师")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping(path = "/{mentorUserId}/favorite")
    public ApiResponse<MentorFavoriteToggleResponse> unfavoriteMentor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long mentorUserId
    ) {
        return ApiResponse.ok("unfavorited", mentorService.unfavoriteMentor(principal.getUserId(), mentorUserId), TraceId.next());
    }

    @Operation(summary = "生成咨询准备单草稿")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/prep-sheet/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorPrepSheetGenerateResponse> generatePrepSheet(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorPrepSheetGenerateRequest request
    ) {
        // 准备单只生成可编辑草稿，下单时仍以学生最终表单为准。
        return ApiResponse.ok("generated", mentorService.generatePrepSheet(principal.getUserId(), request), TraceId.next());
    }
}
