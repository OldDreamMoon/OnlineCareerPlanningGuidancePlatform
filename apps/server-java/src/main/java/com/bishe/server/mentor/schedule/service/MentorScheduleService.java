package com.bishe.server.mentor.schedule.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchCreateRequest;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchCreateResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchDeleteRequest;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotBatchDeleteResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotCreateRequest;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotListResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotResponse;
import com.bishe.server.mentor.schedule.repository.MentorScheduleRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 导师排期服务。
 */
@Service
public class MentorScheduleService {

    private final MentorScheduleRepository mentorScheduleRepository;
    private final MentorPublicScheduleCacheService mentorPublicScheduleCacheService;

    public MentorScheduleService(
            MentorScheduleRepository mentorScheduleRepository,
            MentorPublicScheduleCacheService mentorPublicScheduleCacheService
    ) {
        this.mentorScheduleRepository = mentorScheduleRepository;
        this.mentorPublicScheduleCacheService = mentorPublicScheduleCacheService;
    }

    public MentorScheduleSlotListResponse listPublicSlots(long mentorUserId, String dateFrom, String dateTo) {
        Instant startAt = parseOptionalInstant(dateFrom);
        Instant endAt = parseOptionalInstant(dateTo);
        MentorScheduleSlotListResponse response = mentorPublicScheduleCacheService.getPublicSlots(
                mentorUserId,
                startAt,
                endAt,
                () -> new MentorScheduleSlotListResponse(
                        mentorScheduleRepository.findSlotsForMentor(mentorUserId, startAt, endAt, true)
                                .stream()
                                .map(this::toResponse)
                                .toList()
                )
        );
        return response == null ? new MentorScheduleSlotListResponse(List.of()) : response;
    }

    public MentorScheduleSlotListResponse listOwnSlots(long mentorUserId, String dateFrom, String dateTo) {
        return new MentorScheduleSlotListResponse(
                mentorScheduleRepository.findSlotsForMentor(mentorUserId, parseOptionalInstant(dateFrom), parseOptionalInstant(dateTo), false)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @Transactional
    public MentorScheduleSlotResponse createSlot(long mentorUserId, MentorScheduleSlotCreateRequest request) {
        Instant startAt = parseRequiredInstant(request.startAt(), "startAt");
        Instant endAt = parseRequiredInstant(request.endAt(), "endAt");
        validateSlotWindow(startAt, endAt);
        try {
            long slotId = mentorScheduleRepository.createSlot(mentorUserId, startAt, endAt);
            MentorScheduleSlotResponse response = mentorScheduleRepository.findSlotById(slotId)
                    .map(this::toResponse)
                    .orElseThrow(() -> new ApiException("BIZ-1002", "schedule slot not found", HttpStatus.NOT_FOUND));
            evictPublicSlots(mentorUserId);
            return response;
        } catch (DuplicateKeyException ex) {
            throw new ApiException("BIZ-1001", "schedule slot already exists", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public MentorScheduleSlotBatchCreateResponse createSlotsBatch(long mentorUserId, MentorScheduleSlotBatchCreateRequest request) {
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new ApiException("BIZ-1001", "records required", HttpStatus.BAD_REQUEST);
        }

        LinkedHashSet<String> seenWindows = new LinkedHashSet<>();
        java.util.ArrayList<MentorScheduleSlotResponse> created = new java.util.ArrayList<>();
        int skippedCount = 0;

        for (MentorScheduleSlotCreateRequest item : request.records()) {
            Instant startAt = parseRequiredInstant(item.startAt(), "startAt");
            Instant endAt = parseRequiredInstant(item.endAt(), "endAt");
            validateSlotWindow(startAt, endAt);
            String windowKey = startAt + "|" + endAt;
            if (!seenWindows.add(windowKey)) {
                skippedCount++;
                continue;
            }
            try {
                long slotId = mentorScheduleRepository.createSlot(mentorUserId, startAt, endAt);
                MentorScheduleSlotResponse createdSlot = mentorScheduleRepository.findSlotById(slotId)
                        .map(this::toResponse)
                        .orElseThrow(() -> new ApiException("BIZ-1002", "schedule slot not found", HttpStatus.NOT_FOUND));
                created.add(createdSlot);
            } catch (DuplicateKeyException ex) {
                skippedCount++;
            }
        }

        if (!created.isEmpty()) {
            evictPublicSlots(mentorUserId);
        }
        return new MentorScheduleSlotBatchCreateResponse(List.copyOf(created), created.size(), skippedCount);
    }

    @Transactional
    public MentorScheduleSlotBatchDeleteResponse deleteSlotsBatch(long mentorUserId, MentorScheduleSlotBatchDeleteRequest request) {
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new ApiException("BIZ-1001", "records required", HttpStatus.BAD_REQUEST);
        }

        List<TimeWindow> windows = parseDeleteWindows(request.records());
        Instant now = Instant.now();
        Instant minStart = windows.stream()
                .map(TimeWindow::startAt)
                .min(Instant::compareTo)
                .orElseThrow(() -> new ApiException("BIZ-1001", "records required", HttpStatus.BAD_REQUEST));
        Instant maxEnd = windows.stream()
                .map(TimeWindow::endAt)
                .max(Instant::compareTo)
                .orElseThrow(() -> new ApiException("BIZ-1001", "records required", HttpStatus.BAD_REQUEST));

        List<MentorScheduleRepository.ScheduleSlotRow> matchedSlots = mentorScheduleRepository
                .findSlotsForMentor(mentorUserId, minStart, maxEnd, false)
                .stream()
                .filter(slot -> slot.endAt().isAfter(now))
                .filter(slot -> isInsideAnyWindow(slot, windows))
                .toList();

        int deletedCount = 0;
        int lockedCount = 0;
        for (MentorScheduleRepository.ScheduleSlotRow slot : matchedSlots) {
            if (!"AVAILABLE".equals(slot.status())) {
                lockedCount++;
                continue;
            }
            if (mentorScheduleRepository.deleteAvailableSlot(slot.id(), mentorUserId)) {
                deletedCount++;
            }
        }

        if (deletedCount > 0) {
            evictPublicSlots(mentorUserId);
        }
        return new MentorScheduleSlotBatchDeleteResponse(matchedSlots.size(), deletedCount, lockedCount);
    }

    @Transactional
    public void deleteSlot(long mentorUserId, long slotId) {
        MentorScheduleRepository.ScheduleSlotRow slot = mentorScheduleRepository.findSlotById(slotId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "schedule slot not found", HttpStatus.NOT_FOUND));
        if (slot.mentorUserId() != mentorUserId) {
            throw new ApiException("AUTH-1004", "access denied", HttpStatus.FORBIDDEN);
        }
        if (!mentorScheduleRepository.deleteAvailableSlot(slotId, mentorUserId)) {
            throw new ApiException("BIZ-1001", "only available slot can be deleted", HttpStatus.BAD_REQUEST);
        }
        evictPublicSlots(mentorUserId);
    }

    @Transactional
    public ReservedSlot reserveSlot(long mentorUserId, Long slotId, Long orderId, String orderNo) {
        if (slotId == null) {
            return null;
        }
        MentorScheduleRepository.ScheduleSlotRow slot = mentorScheduleRepository.findSlotById(slotId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "schedule slot not found", HttpStatus.NOT_FOUND));
        if (slot.mentorUserId() != mentorUserId) {
            throw new ApiException("BIZ-1001", "schedule slot mentor mismatch", HttpStatus.BAD_REQUEST);
        }
        if (slot.startAt().isBefore(Instant.now())) {
            throw new ApiException("BIZ-1001", "schedule slot expired", HttpStatus.BAD_REQUEST);
        }
        if (!mentorScheduleRepository.reserveSlot(slotId, mentorUserId, orderId, orderNo)) {
            throw new ApiException("BIZ-1001", "schedule slot unavailable", HttpStatus.BAD_REQUEST);
        }
        evictPublicSlots(mentorUserId);
        return new ReservedSlot(slotId, slot.startAt(), slot.endAt());
    }

    @Transactional
    public ReservedSlot reserveSlot(long mentorUserId, Long slotId, String orderNo) {
        return reserveSlot(mentorUserId, slotId, null, orderNo);
    }

    @Transactional
    public void bindReservedOrder(long orderId, String orderNo) {
        if (orderId <= 0 || orderNo == null || orderNo.isBlank()) {
            return;
        }
        if (mentorScheduleRepository.bindReservedOrder(orderId, orderNo) <= 0) {
            throw new IllegalStateException("reserved slot not found for orderNo=" + orderNo);
        }
    }

    @Transactional
    public void releaseSlotForOrder(Long orderId, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        List<Long> mentorUserIds = mentorScheduleRepository.findBookedMentorUserIds(orderId, orderNo);
        if (mentorScheduleRepository.releaseSlot(orderId, orderNo) <= 0) {
            return;
        }
        mentorUserIds.forEach(this::evictPublicSlotsIfPresent);
    }

    @Transactional
    public void releaseSlotForOrder(String orderNo) {
        releaseSlotForOrder(null, orderNo);
    }

    @Transactional
    public boolean releaseUpcomingSlotForOrder(Long orderId, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return false;
        }
        List<Long> mentorUserIds = mentorScheduleRepository.findBookedMentorUserIds(orderId, orderNo);
        boolean released = mentorScheduleRepository.releaseUpcomingSlot(orderId, orderNo, Instant.now()) > 0;
        if (released) {
            mentorUserIds.forEach(this::evictPublicSlotsIfPresent);
        }
        return released;
    }

    @Transactional
    public boolean releaseUpcomingSlotForOrder(String orderNo) {
        return releaseUpcomingSlotForOrder(null, orderNo);
    }

    private MentorScheduleSlotResponse toResponse(MentorScheduleRepository.ScheduleSlotRow row) {
        return new MentorScheduleSlotResponse(
                row.id(),
                row.mentorUserId(),
                TimePayloads.toEpochMillis(row.startAt()),
                TimePayloads.toEpochMillis(row.endAt()),
                row.status(),
                row.bookedOrderNo()
        );
    }

    private void validateSlotWindow(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new ApiException("BIZ-1001", "schedule slot time invalid", HttpStatus.BAD_REQUEST);
        }
        if (startAt.isBefore(Instant.now())) {
            throw new ApiException("BIZ-1001", "schedule slot must be in future", HttpStatus.BAD_REQUEST);
        }
    }

    private Instant parseOptionalInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredInstant(value, "date");
    }

    private Instant parseRequiredInstant(String value, String fieldName) {
        try {
            return Instant.parse(value.trim());
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private void evictPublicSlots(long mentorUserId) {
        mentorPublicScheduleCacheService.evictNow(mentorUserId);
        mentorPublicScheduleCacheService.evictAfterCommit(mentorUserId);
    }

    private void evictPublicSlotsIfPresent(Long mentorUserId) {
        if (mentorUserId != null) {
            evictPublicSlots(mentorUserId.longValue());
        }
    }

    private List<TimeWindow> parseDeleteWindows(List<MentorScheduleSlotCreateRequest> records) {
        LinkedHashSet<String> seenWindows = new LinkedHashSet<>();
        ArrayList<TimeWindow> windows = new ArrayList<>();
        for (MentorScheduleSlotCreateRequest item : records) {
            Instant startAt = parseRequiredInstant(item.startAt(), "startAt");
            Instant endAt = parseRequiredInstant(item.endAt(), "endAt");
            if (!endAt.isAfter(startAt)) {
                throw new ApiException("BIZ-1001", "schedule slot time invalid", HttpStatus.BAD_REQUEST);
            }
            String windowKey = startAt + "|" + endAt;
            if (!seenWindows.add(windowKey)) {
                continue;
            }
            windows.add(new TimeWindow(startAt, endAt));
        }
        if (windows.isEmpty()) {
            throw new ApiException("BIZ-1001", "records required", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(windows);
    }

    private boolean isInsideAnyWindow(MentorScheduleRepository.ScheduleSlotRow slot, List<TimeWindow> windows) {
        for (TimeWindow window : windows) {
            if (!slot.startAt().isBefore(window.startAt()) && !slot.endAt().isAfter(window.endAt())) {
                return true;
            }
        }
        return false;
    }

    public record ReservedSlot(
            long slotId,
            Instant startAt,
            Instant endAt
    ) {
    }

    private record TimeWindow(
            Instant startAt,
            Instant endAt
    ) {
    }
}
