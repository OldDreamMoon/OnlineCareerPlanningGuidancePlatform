package com.bishe.server.mentor.schedule.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchCreateRequest;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchCreateResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchDeleteRequest;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchDeleteResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotCreateRequest;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotListResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotResponse;
import com.bishe.server.mentor.schedule.service.MentorScheduleService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导师排期接口。
 */
@Tag(name = "MentorSchedule", description = "导师排期与可预约时段接口")
@RestController
@RequestMapping(path = "/api/v1/mentor/schedule/slots", produces = MediaType.APPLICATION_JSON_VALUE)
public class MentorScheduleController {

    private final MentorScheduleService mentorScheduleService;

    public MentorScheduleController(MentorScheduleService mentorScheduleService) {
        this.mentorScheduleService = mentorScheduleService;
    }

    @Operation(summary = "查询导师可预约时段")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @GetMapping
    public ApiResponse<MentorScheduleSlotListResponse> listPublicSlots(
            @RequestParam long mentorUserId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        return ApiResponse.ok(mentorScheduleService.listPublicSlots(mentorUserId, dateFrom, dateTo), TraceId.next());
    }

    @Operation(summary = "查询我的排期")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping(path = "/me")
    public ApiResponse<MentorScheduleSlotListResponse> listOwnSlots(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        return ApiResponse.ok(mentorScheduleService.listOwnSlots(principal.getUserId(), dateFrom, dateTo), TraceId.next());
    }

    @Operation(summary = "创建可预约时段")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorScheduleSlotResponse> createSlot(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorScheduleSlotCreateRequest request
    ) {
        return ApiResponse.ok(mentorScheduleService.createSlot(principal.getUserId(), request), TraceId.next());
    }

    @Operation(summary = "批量创建导师可预约时段")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorScheduleSlotBatchCreateResponse> createSlotsBatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorScheduleSlotBatchCreateRequest request
    ) {
        return ApiResponse.ok(mentorScheduleService.createSlotsBatch(principal.getUserId(), request), TraceId.next());
    }

    @Operation(summary = "批量清理导师可预约时段")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/batch-delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorScheduleSlotBatchDeleteResponse> deleteSlotsBatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorScheduleSlotBatchDeleteRequest request
    ) {
        return ApiResponse.ok(mentorScheduleService.deleteSlotsBatch(principal.getUserId(), request), TraceId.next());
    }

    @Operation(summary = "删除可预约时段")
    @PreAuthorize("hasRole('MENTOR')")
    @DeleteMapping(path = "/{slotId}")
    public ApiResponse<Void> deleteSlot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long slotId
    ) {
        mentorScheduleService.deleteSlot(principal.getUserId(), slotId);
        return ApiResponse.ok(null, TraceId.next());
    }
}
