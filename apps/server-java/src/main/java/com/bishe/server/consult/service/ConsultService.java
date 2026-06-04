package com.bishe.server.consult.service;

import com.bishe.server.ai.service.AiPracticeService;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.common.TraceId;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.consult.AlipayPagePayUrlBuilder;
import com.bishe.server.consult.AlipaySignatureVerifier;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.PaymentMode;
import com.bishe.server.consult.PaymentProperties;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestCreateRequest;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestCreateResponse;
import com.bishe.server.consult.dto.ConsultOrderAttachmentBatchUploadResponse;
import com.bishe.server.consult.dto.ConsultOrderAttachmentDeleteResponse;
import com.bishe.server.consult.dto.ConsultOrderAttachmentItem;
import com.bishe.server.consult.dto.ConsultOrderAttachmentListResponse;
import com.bishe.server.consult.dto.ConsultMessageCreateRequest;
import com.bishe.server.consult.dto.ConsultMessageCreateResponse;
import com.bishe.server.consult.dto.ConsultMessageListResponse;
import com.bishe.server.consult.dto.ConsultMentorReplyDraftRequest;
import com.bishe.server.consult.dto.ConsultMentorReplyDraftResponse;
import com.bishe.server.consult.dto.ConsultMentorWorkbenchResponse;
import com.bishe.server.consult.dto.ConsultPaymentCloseResponse;
import com.bishe.server.consult.dto.ConsultPaymentQueryResponse;
import com.bishe.server.consult.dto.ConsultOrderCreateRequest;
import com.bishe.server.consult.dto.ConsultOrderCancelResponse;
import com.bishe.server.consult.dto.ConsultOrderCloseResponse;
import com.bishe.server.consult.dto.ConsultOrderCreateResponse;
import com.bishe.server.consult.dto.ConsultOrderDetailResponse;
import com.bishe.server.consult.dto.ConsultOrderListResponse;
import com.bishe.server.consult.dto.ConsultReviewCreateRequest;
import com.bishe.server.consult.dto.ConsultReviewResponse;
import com.bishe.server.consult.dto.PaymentCreateResponse;
import com.bishe.server.consult.dto.PaymentSuccessResponse;
import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.mentor.dto.MentorDetailResponse;
import com.bishe.server.mentor.dto.MentorServicePackageResponse;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.mentor.repository.MentorServicePackageRepository;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.bishe.server.mentor.service.MentorDashboardCacheService;
import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.bishe.server.mentor.service.MentorServicePackageSupport;
import com.bishe.server.mentor.service.MentorService;
import com.bishe.server.mentor.schedule.service.MentorScheduleService;
import com.bishe.server.profile.repository.StudentProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 咨询、支付与评价服务：打通导师浏览后的商业闭环最小链路。
 */
@Service
public class ConsultService {

    private static final DateTimeFormatter ORDER_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    // 附件类型和来源阶段在服务层收口，避免前端传入任意 slot 破坏材料版本链。
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of("RESUME", "JOB_DESCRIPTION", "PROJECT_MATERIAL", "OFFER_MATERIAL", "SUPPLEMENTARY");
    private static final Set<String> SINGLE_SLOT_ATTACHMENT_TYPES = Set.of("RESUME", "JOB_DESCRIPTION");
    private static final Set<String> ALLOWED_ATTACHMENT_SOURCE_STAGES = Set.of("ORDER_CREATE", "CHAT_APPEND", "CHAT_REPLACE");
    // 导师工作台筛选项必须和前端枚举保持一致，未知值统一回退到 ALL/PRIORITY。
    private static final int MENTOR_WORKBENCH_EXPIRING_WINDOW_HOURS = 6;
    private static final Set<String> MENTOR_WORKBENCH_SERVICE_FILTERS = Set.of("ALL", "TEXT", "APPOINTMENT");
    private static final Set<String> MENTOR_WORKBENCH_TIME_FILTERS = Set.of("ALL", "CREATED_7D", "PAID_7D", "UPCOMING_APPOINTMENT");
    private static final Set<String> MENTOR_WORKBENCH_RISK_FILTERS = Set.of("ALL", "EXPIRING", "NEED_CONFIRMATION", "AFTER_SALES");
    private static final Set<String> MENTOR_WORKBENCH_SORT_MODES = Set.of("PRIORITY", "LATEST_CREATED", "LATEST_PAID", "DEADLINE_ASC", "AMOUNT_DESC");

    private final ConsultRepository consultRepository;
    private final MentorService mentorService;
    private final MentorRepository mentorRepository;
    private final MentorServicePackageRepository mentorServicePackageRepository;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final ConsultAttachmentStorageService consultAttachmentStorageService;
    private final NotificationService notificationService;
    private final MentorScheduleService mentorScheduleService;
    private final ConsultAfterSalesService consultAfterSalesService;
    private final ConsultPaymentGatewayService consultPaymentGatewayService;
    private final AiPracticeService aiPracticeService;
    private final FeatureFlagService featureFlagService;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final MentorDashboardCacheService mentorDashboardCacheService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;

    public ConsultService(
            ConsultRepository consultRepository,
            MentorService mentorService,
            MentorRepository mentorRepository,
            MentorServicePackageRepository mentorServicePackageRepository,
            PaymentProperties paymentProperties,
            ObjectMapper objectMapper,
            ConsultAttachmentStorageService consultAttachmentStorageService,
            NotificationService notificationService,
            MentorScheduleService mentorScheduleService,
            ConsultAfterSalesService consultAfterSalesService,
            ConsultPaymentGatewayService consultPaymentGatewayService,
            AiPracticeService aiPracticeService,
            FeatureFlagService featureFlagService,
            StudentProfileRepository studentProfileRepository,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService,
            MentorDashboardCacheService mentorDashboardCacheService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService
    ) {
        this.consultRepository = consultRepository;
        this.mentorService = mentorService;
        this.mentorRepository = mentorRepository;
        this.mentorServicePackageRepository = mentorServicePackageRepository;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.consultAttachmentStorageService = consultAttachmentStorageService;
        this.notificationService = notificationService;
        this.mentorScheduleService = mentorScheduleService;
        this.consultAfterSalesService = consultAfterSalesService;
        this.consultPaymentGatewayService = consultPaymentGatewayService;
        this.aiPracticeService = aiPracticeService;
        this.featureFlagService = featureFlagService;
        this.studentProfileRepository = studentProfileRepository;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.mentorDashboardCacheService = mentorDashboardCacheService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
    }

    @Transactional
    public ConsultOrderCreateResponse createOrder(long studentUserId, ConsultOrderCreateRequest request) {
        featureFlagService.requirePaymentEnabled();
        // 创单前重新读取导师公开信息，保证价格、可接单状态和套餐快照来自服务端。
        MentorDetailResponse mentor = mentorService.getMentorDetail(request.mentorUserId());
        if (!mentor.available()) {
            throw new ApiException("BIZ-1001", "mentor unavailable", HttpStatus.BAD_REQUEST);
        }

        String questionText = request.questionText().trim();
        String sourcePage = normalizeOptionalCode(request.sourcePage(), 80);
        String problemSummary = normalizeOptionalText(request.problemSummary());
        List<String> coreQuestions = normalizeShortTextList(request.coreQuestions());
        List<String> expectedOutcomes = normalizeShortTextList(request.expectedOutcomes());
        List<String> selectedMaterialTypes = normalizeMaterialTypeList(request.selectedMaterialTypes());
        String orderNo = buildOrderNo();
        MentorServicePackageResponse selectedPackage = resolveSelectedPackage(mentor.userId(), request.mentorPackageId());
        String sceneCode = selectedPackage == null ? normalizeOptionalCode(request.sceneCode(), 60) : selectedPackage.sceneCode();
        int amountFen = selectedPackage == null ? mentor.priceFen() : selectedPackage.priceFen();
        // 预约型套餐先预占时段，订单创建成功后再绑定正式 orderId。
        MentorScheduleService.ReservedSlot reservedSlot = reserveOrderSlot(mentor.userId(), orderNo, request.scheduleSlotId(), selectedPackage);
        long orderId = consultRepository.createOrder(new ConsultRepository.CreateOrderCommand(
                orderNo,
                studentUserId,
                mentor.userId(),
                sceneCode,
                sourcePage,
                amountFen,
                questionText,
                serializeQuestionPayload(request.questionPayload()),
                problemSummary,
                serializeJsonOrNull(coreQuestions),
                serializeJsonOrNull(expectedOutcomes),
                TextListCodec.join(selectedMaterialTypes),
                serializePrepSheetSnapshot(request.prepSheetSnapshot()),
                serializeServicePackageSnapshot(selectedPackage),
                reservedSlot == null ? null : reservedSlot.startAt(),
                reservedSlot == null ? null : reservedSlot.endAt()
        ));
        if (reservedSlot != null) {
            mentorScheduleService.bindReservedOrder(orderId, orderNo);
        }
        // 首条学生消息就是咨询问题快照，导师履约页会按消息流继续承接。
        consultRepository.createMessage(orderId, orderNo, studentUserId, UserRole.STUDENT.name(), questionText);
        evictMentorDashboardCache(mentor.userId());
        return new ConsultOrderCreateResponse(
                orderNo,
                amountFen,
                ConsultOrderStatus.CREATED.name(),
                toIso(reservedSlot == null ? null : reservedSlot.startAt()),
                toIso(reservedSlot == null ? null : reservedSlot.endAt()),
                sceneCode,
                0
        );
    }

    @Transactional
    public ConsultOrderAttachmentBatchUploadResponse uploadAttachments(long studentUserId, String orderNo, String manifestJson, List<MultipartFile> files) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        ensureOrderAllowsStudentAttachment(order.status());

        AttachmentUploadManifest manifest = parseAttachmentUploadManifest(manifestJson);
        if (files == null || files.isEmpty()) {
            throw new ApiException("BIZ-1001", "请至少上传一份材料", HttpStatus.BAD_REQUEST);
        }
        if (manifest.items().size() != files.size()) {
            throw new ApiException("BIZ-1001", "材料清单与文件数量不一致", HttpStatus.BAD_REQUEST);
        }

        List<ConsultOrderAttachmentItem> uploadedItems = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            AttachmentManifestItem normalizedItem = normalizeAttachmentManifestItem(manifest.items().get(index));
            // 单槽材料替换时先把旧 CURRENT 标为 SUPERSEDED，再保留 replacedAttachmentId 形成版本链。
            ConsultRepository.OrderAttachmentRow previousAttachment = normalizedItem.replaceCurrent()
                    ? consultRepository.findCurrentAttachmentBySlot(order.orderId(), orderNo, normalizedItem.slotCode()).orElse(null)
                    : null;

            if (previousAttachment != null) {
                consultRepository.markAttachmentSuperseded(order.orderId(), orderNo, previousAttachment.id());
            }

            ConsultAttachmentStorageService.StoredAttachment storedAttachment = consultAttachmentStorageService.upload(
                    orderNo,
                    normalizedItem.slotCode(),
                    normalizedItem.attachmentType(),
                    file
            );
            long attachmentId = consultRepository.createAttachment(order.orderId(), new ConsultRepository.CreateAttachmentCommand(
                    orderNo,
                    studentUserId,
                    normalizedItem.attachmentType(),
                    normalizedItem.slotCode(),
                    normalizedItem.sourceStage(),
                    storedAttachment.originalFilename(),
                    storedAttachment.contentType(),
                    storedAttachment.sizeBytes(),
                    storedAttachment.bucket(),
                    storedAttachment.objectKey(),
                    normalizedItem.description(),
                    "CURRENT",
                    previousAttachment == null ? null : previousAttachment.id()
            ));
            ConsultRepository.OrderAttachmentRow createdAttachment = consultRepository.findAttachmentById(order.orderId(), orderNo, attachmentId)
                    .orElseThrow(() -> new IllegalStateException("uploaded attachment not found"));
            uploadedItems.add(toAttachmentItem(createdAttachment));
        }

        return new ConsultOrderAttachmentBatchUploadResponse(uploadedItems, consultRepository.countCurrentAttachments(order.orderId(), orderNo));
    }

    public ConsultOrderAttachmentListResponse listAttachments(long userId, String role, String orderNo, boolean currentOnly, boolean includeSuperseded) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(userId, role, orderNo);
        List<ConsultOrderAttachmentItem> items = consultRepository.findAttachments(order.orderId(), orderNo, currentOnly, includeSuperseded)
                .stream()
                .map(this::toAttachmentItem)
                .toList();
        return new ConsultOrderAttachmentListResponse(items, consultRepository.countCurrentAttachments(order.orderId(), orderNo));
    }

    public AttachmentContentResponse readAttachment(long userId, String role, String orderNo, long attachmentId) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(userId, role, orderNo);
        ConsultRepository.OrderAttachmentRow attachment = consultRepository.findAttachmentById(order.orderId(), orderNo, attachmentId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "attachment not found", HttpStatus.NOT_FOUND));
        if ("DELETED".equalsIgnoreCase(attachment.lifecycleStatus())) {
            throw new ApiException("BIZ-1002", "attachment not found", HttpStatus.NOT_FOUND);
        }

        ConsultAttachmentStorageService.StoredAttachmentContent storedContent = consultAttachmentStorageService.read(
                attachment.storageBucket(),
                attachment.objectKey()
        );
        String contentType = TextListCodec.normalizeText(attachment.contentType());
        if (contentType == null) {
            contentType = TextListCodec.normalizeText(storedContent.contentType());
        }
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return new AttachmentContentResponse(attachment.originalFilename(), mediaType, storedContent.bytes());
    }

    @Transactional
    public ConsultOrderAttachmentDeleteResponse deleteAttachment(long studentUserId, String orderNo, long attachmentId) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        ensureOrderAllowsStudentAttachment(order.status());
        ConsultRepository.OrderAttachmentRow attachment = consultRepository.findAttachmentById(order.orderId(), orderNo, attachmentId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "attachment not found", HttpStatus.NOT_FOUND));
        if (!"CURRENT".equalsIgnoreCase(attachment.lifecycleStatus())) {
            throw new ApiException("BIZ-1001", "attachment already inactive", HttpStatus.BAD_REQUEST);
        }
        boolean deleted = consultRepository.markAttachmentDeleted(order.orderId(), orderNo, attachmentId);
        return new ConsultOrderAttachmentDeleteResponse(attachmentId, deleted, consultRepository.countCurrentAttachments(order.orderId(), orderNo));
    }

    public ConsultOrderListResponse listOrders(long userId, String role, int page, int size, String status) {
        // 列表读取前顺手做超时懒刷新，避免学生/导师看到已过期的中间状态。
        reconcileTimedOutPendingOrders();
        consultAfterSalesService.reconcileTimedOutMentorReplyOrders();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        ConsultOrderStatus statusFilter = parseOptionalStatus(status);
        UserRole userRole = parseSupportedRole(role);

        long total = userRole == UserRole.STUDENT
                ? consultRepository.countOrdersForStudent(userId, statusFilter)
                : consultRepository.countOrdersForMentor(userId, statusFilter);

        var rows = userRole == UserRole.STUDENT
                ? consultRepository.findOrdersForStudent(userId, statusFilter, safePage, safeSize)
                : consultRepository.findOrdersForMentor(userId, statusFilter, safePage, safeSize);

        return new ConsultOrderListResponse(
                rows.stream()
                        .map(item -> new ConsultOrderListResponse.OrderItem(
                                item.orderNo(),
                                item.counterpartUserId(),
                                item.counterpartDisplayName(),
                                item.amountFen(),
                                item.status().name(),
                                item.questionText(),
                                item.paymentMode(),
                                toIso(item.appointmentStartAt()),
                                toIso(item.appointmentEndAt()),
                                toIso(item.createdAt()),
                                toIso(item.paidAt()),
                                toIso(item.closedAt()),
                                resolveAutoCancelAt(item.status(), item.createdAt(), item.paidAt())
                        ))
                        .toList(),
                total,
                safePage,
                safeSize
        );
    }

    public ConsultMentorWorkbenchResponse getMentorWorkbench(
            long mentorUserId,
            int page,
            int size,
            String keyword,
            String status,
            String serviceFilter,
            String timeFilter,
            String paymentMode,
            String riskFilter,
            String sortMode
    ) {
        // 导师工作台也先做懒刷新，摘要卡和风险筛选才不会被旧订单状态污染。
        reconcileTimedOutPendingOrders();
        consultAfterSalesService.reconcileTimedOutMentorReplyOrders();

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedPaymentMode = normalizeOptionalCode(paymentMode, 30);
        if ("ALL".equals(normalizedPaymentMode)) {
            normalizedPaymentMode = null;
        }

        ConsultRepository.MentorWorkbenchQuery query = new ConsultRepository.MentorWorkbenchQuery(
                normalizeOptionalText(keyword),
                parseOptionalStatus(status),
                normalizeWorkbenchFilter(serviceFilter, MENTOR_WORKBENCH_SERVICE_FILTERS, "ALL"),
                normalizeWorkbenchFilter(timeFilter, MENTOR_WORKBENCH_TIME_FILTERS, "ALL"),
                normalizedPaymentMode,
                normalizeWorkbenchFilter(riskFilter, MENTOR_WORKBENCH_RISK_FILTERS, "ALL"),
                normalizeWorkbenchFilter(sortMode, MENTOR_WORKBENCH_SORT_MODES, "PRIORITY"),
                safePage,
                safeSize
        );
        int mentorReplyTimeoutHours = consultAfterSalesService.getMentorReplyTimeoutHours();
        ConsultRepository.MentorWorkbenchSummaryRow summaryRow = consultRepository.summarizeMentorWorkbench(
                mentorUserId,
                mentorReplyTimeoutHours,
                MENTOR_WORKBENCH_EXPIRING_WINDOW_HOURS
        );
        long total = consultRepository.countMentorWorkbenchOrders(
                mentorUserId,
                query,
                mentorReplyTimeoutHours,
                MENTOR_WORKBENCH_EXPIRING_WINDOW_HOURS
        );
        List<ConsultRepository.MentorWorkbenchOrderRow> rows = consultRepository.findMentorWorkbenchOrders(
                mentorUserId,
                query,
                mentorReplyTimeoutHours,
                MENTOR_WORKBENCH_EXPIRING_WINDOW_HOURS
        );
        return new ConsultMentorWorkbenchResponse(
                new ConsultMentorWorkbenchResponse.Summary(
                        summaryRow.pendingReplyCount(),
                        summaryRow.expiringSoonCount(),
                        summaryRow.waitingConfirmationCount(),
                        summaryRow.afterSalesImpactCount()
                ),
                rows.stream()
                        .map(item -> new ConsultMentorWorkbenchResponse.OrderItem(
                                item.orderNo(),
                                item.counterpartUserId(),
                                item.counterpartDisplayName(),
                                item.amountFen(),
                                item.status().name(),
                                item.questionText(),
                                item.paymentMode(),
                                toIso(item.appointmentStartAt()),
                                toIso(item.appointmentEndAt()),
                                toIso(item.createdAt()),
                                toIso(item.paidAt()),
                                toIso(item.closedAt()),
                                resolveAutoCancelAt(item.status(), item.createdAt(), item.paidAt()),
                                toIso(item.mentorReplyDeadlineAt()),
                                item.afterSalesImpact(),
                                item.pendingAfterSales(),
                                item.latestAfterSalesStatus()
                        ))
                        .toList(),
                total,
                safePage,
                safeSize,
                consultRepository.findMentorWorkbenchPaymentModes(mentorUserId)
        );
    }

    public ConsultOrderDetailResponse getOrderDetail(long userId, String role, String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(userId, role, orderNo);
        return toOrderDetailResponse(order);
    }

    public ConsultMessageListResponse listMessages(long userId, String role, String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(userId, role, orderNo);
        return new ConsultMessageListResponse(
                consultRepository.findMessages(order.orderId(), orderNo).stream()
                        .map(item -> new ConsultMessageListResponse.MessageItem(
                                item.id(),
                                item.senderUserId(),
                                item.senderDisplayName(),
                                item.senderRole(),
                                item.messageText(),
                                toIso(item.createdAt())
                        ))
                        .toList()
        );
    }

    public ConsultMentorReplyDraftResponse generateMentorReplyDraft(
            long mentorUserId,
            String orderNo,
            ConsultMentorReplyDraftRequest request,
            String traceId
    ) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(mentorUserId, UserRole.MENTOR.name(), orderNo);
        ensureMentorOrderOwner(order, mentorUserId);
        ensureOrderAllowsMentorReplyDraft(order.status());

        ConsultOrderDetailResponse detail = toOrderDetailResponse(order);
        List<ConsultRepository.MessageRow> messageRows = consultRepository.findMessages(order.orderId(), orderNo);
        return aiPracticeService.generateMentorOrderReplyDraft(
                mentorUserId,
                traceId,
                buildMentorReplyDraftTitle(detail),
                buildMentorReplyDraftContext(detail, messageRows),
                normalizeOptionalText(request.currentDraft()),
                normalizeOptionalText(request.instruction())
        );
    }

    @Transactional
    public int reconcileTimedOutPendingOrders() {
        int timeoutMinutes = paymentProperties.getUnpaidTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(timeoutMinutes * 60L);
        int canceledCount = 0;
        // 未支付超时既可由定时任务处理，也会在列表/详情读取前懒刷新。
        for (String timedOutOrderNo : consultRepository.findTimedOutUnpaidOrderNos(cutoff)) {
            ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(timedOutOrderNo).orElse(null);
            if (order != null && cancelPendingOrderInternal(order, PendingCancelReason.TIMEOUT)) {
                canceledCount++;
            }
        }
        return canceledCount;
    }

    @Transactional
    public ConsultAfterSalesRequestCreateResponse createAfterSalesRequest(long studentUserId, String orderNo, ConsultAfterSalesRequestCreateRequest request) {
        return consultAfterSalesService.createRequest(TraceId.next(), studentUserId, orderNo, request);
    }

    @Transactional
    public ConsultMessageCreateResponse sendMessage(long userId, String role, String orderNo, ConsultMessageCreateRequest request) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(userId, role, orderNo);
        UserRole userRole = parseSupportedRole(role);
        String messageText = request.messageText().trim();

        if (userRole == UserRole.STUDENT) {
            // 学生消息只追加沟通记录，不推动订单状态。
            ensureStudentOrderOwner(order, userId);
            ensureOrderAllowsStudentMessage(order.status());
            long messageId = consultRepository.createMessage(order.orderId(), orderNo, userId, UserRole.STUDENT.name(), messageText);
            return new ConsultMessageCreateResponse(messageId, orderNo, UserRole.STUDENT.name(), order.status().name(), Instant.now().toEpochMilli());
        }

        ensureMentorOrderOwner(order, userId);
        if (order.status() != ConsultOrderStatus.PAID && order.status() != ConsultOrderStatus.ANSWERED) {
            throw new ApiException("BIZ-1001", "mentor reply requires paid order", HttpStatus.BAD_REQUEST);
        }
        long messageId = consultRepository.createMessage(order.orderId(), orderNo, userId, UserRole.MENTOR.name(), messageText);
        ConsultOrderStatus nextStatus = order.status();
        if (order.status() == ConsultOrderStatus.PAID) {
            // 导师第一条正式回复是履约状态推进点，后续回复保持 ANSWERED。
            consultRepository.markOrderAnswered(order.orderId(), orderNo);
            nextStatus = ConsultOrderStatus.ANSWERED;
            evictMentorDashboardCache(order.mentorUserId());
        }
        notificationService.createNotification(order.studentUserId(), "CONSULT_REPLIED", "导师已回复您的咨询，请查看并决定是否关闭订单。", orderNo);
        return new ConsultMessageCreateResponse(messageId, orderNo, UserRole.MENTOR.name(), nextStatus.name(), Instant.now().toEpochMilli());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PaymentCreateResponse createPayment(long studentUserId, String orderNo) {
        featureFlagService.requirePaymentEnabled();
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);

        PaymentMode paymentMode = featureFlagService.resolvePaymentMode();
        if (order.status() == ConsultOrderStatus.PAYING && paymentMode == PaymentMode.SANDBOX) {
            // 沙箱支付可复用已初始化的支付记录，避免刷新页面后重复创建第三方交易。
            ConsultRepository.PaymentRecordRow latestRecord = consultRepository.findLatestPaymentRecord(order.orderId(), orderNo)
                    .orElseThrow(() -> new ApiException("BIZ-1001", "payment not initialized", HttpStatus.BAD_REQUEST));
            if (!PaymentMode.SANDBOX.name().equalsIgnoreCase(latestRecord.mode())) {
                throw new ApiException("BIZ-1001", "payment mode mismatch", HttpStatus.BAD_REQUEST);
            }
            return buildPaymentCreateResponse(orderNo, order.amountFen(), paymentMode);
        }
        if (order.status() != ConsultOrderStatus.CREATED) {
            throw new ApiException("BIZ-1001", "order status invalid for payment", HttpStatus.BAD_REQUEST);
        }

        try {
            PaymentCreateResponse response = buildPaymentCreateResponse(orderNo, order.amountFen(), paymentMode);
            // 支付记录先落 INIT，再把订单推进 PAYING，后续 mock/sandbox 回调继续更新状态。
            consultRepository.insertPaymentRecord(
                    order.orderId(),
                    orderNo,
                    paymentMode == PaymentMode.MOCK ? "MOCK" : "ALIPAY",
                    paymentMode.name(),
                    null,
                    order.amountFen(),
                    "INIT",
                    "INIT:" + orderNo,
                    null
            );
            consultRepository.markOrderPaying(order.orderId(), orderNo);
            evictMentorDashboardCache(order.mentorUserId());
            return response;
        } catch (ApiException ex) {
            if (paymentMode == PaymentMode.SANDBOX && "PAY-1001".equals(ex.getCode())) {
                consultRepository.markOrderFailed(order.orderId(), orderNo);
            }
            throw ex;
        }
    }

    @Transactional
    public ConsultPaymentQueryResponse queryPayment(String traceId, long studentUserId, String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        var result = consultPaymentGatewayService.querySandboxTradeForStudent(traceId, studentUserId, orderNo);
        return new ConsultPaymentQueryResponse(
                result.orderNo(),
                result.localStatus(),
                result.paymentMode(),
                result.providerTradeNo(),
                result.tradeStatus(),
                result.gatewayCode(),
                result.gatewayMessage(),
                result.syncedToPaid(),
                result.paidAt(),
                result.queriedAt()
        );
    }

    @Transactional
    public ConsultPaymentCloseResponse closePayment(String traceId, long studentUserId, String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        var result = consultPaymentGatewayService.closeSandboxTradeForStudent(traceId, studentUserId, orderNo);
        return new ConsultPaymentCloseResponse(
                result.orderNo(),
                result.localStatus(),
                result.paymentMode(),
                result.providerTradeNo(),
                result.gatewayCode(),
                result.gatewayMessage(),
                result.closed(),
                result.closedAt()
        );
    }

    private PaymentCreateResponse buildPaymentCreateResponse(String orderNo, int amountFen, PaymentMode paymentMode) {
        String paymentUrl = null;
        String instruction;
        if (paymentMode == PaymentMode.MOCK) {
            instruction = "已进入模拟支付，请继续调用模拟支付成功接口完成联调。";
        } else {
            paymentUrl = buildSandboxPaymentUrl(orderNo, amountFen);
            instruction = "已生成支付宝沙箱支付链接；若浏览器未自动完成支付，请打开返回的 paymentUrl，并确保 notify_url 可被支付宝沙箱回调访问。";
        }
        return new PaymentCreateResponse(orderNo, ConsultOrderStatus.PAYING.name(), paymentMode.name(), paymentMode == PaymentMode.MOCK ? "MOCK" : "ALIPAY", paymentUrl, instruction);
    }

    private String buildSandboxPaymentUrl(String orderNo, int amountFen) {
        PaymentProperties.Sandbox sandbox = paymentProperties.getSandbox();
        if (sandbox.hasSignedPagePayConfig()) {
            try {
                return AlipayPagePayUrlBuilder.build(sandbox, objectMapper, orderNo, amountFen);
            } catch (Exception ex) {
                throw new ApiException("PAY-1001", "sandbox payment config invalid", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        if (sandbox.getPayBaseUrl() != null && !sandbox.getPayBaseUrl().isBlank()) {
            return sandbox.getPayBaseUrl().contains("?")
                    ? sandbox.getPayBaseUrl() + "&orderNo=" + orderNo
                    : sandbox.getPayBaseUrl() + "?orderNo=" + orderNo;
        }
        throw new ApiException("PAY-1001", "sandbox payment not configured", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Transactional
    public PaymentSuccessResponse mockPaymentSuccess(String traceId, long studentUserId, String orderNo, String reason) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        String normalizedReason = TextListCodec.normalizeText(reason);

        ConsultRepository.PaymentRecordRow initRecord = consultRepository.findLatestPaymentRecord(order.orderId(), orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1001", "payment not initialized", HttpStatus.BAD_REQUEST));
        if (!PaymentMode.MOCK.name().equalsIgnoreCase(initRecord.mode())) {
            throw new ApiException("BIZ-1001", "payment mode mismatch", HttpStatus.BAD_REQUEST);
        }

        String successKey = "MOCK_SUCCESS:" + orderNo;
        boolean alreadyProcessed = consultRepository.findPaymentRecordByIdempotencyKey(successKey).isPresent();
        if (!alreadyProcessed) {
            if (order.status() != ConsultOrderStatus.PAYING) {
                throw new ApiException("BIZ-1001", "order status invalid for payment success", HttpStatus.BAD_REQUEST);
            }
            consultRepository.insertPaymentRecord(
                    order.orderId(),
                    orderNo,
                    "MOCK",
                    PaymentMode.MOCK.name(),
                    "MOCK-" + orderNo,
                    order.amountFen(),
                    "SUCCESS",
                    successKey,
                    normalizedReason
            );
            consultRepository.markOrderPaid(order.orderId(), orderNo);
            evictMentorDashboardCache(order.mentorUserId());
            evictOperationsDashboardCache();
            notificationService.createNotification(order.mentorUserId(), "CONSULT_PAID", "新的咨询订单已支付，可开始答复。", orderNo);
            consultRepository.insertAuditLog(
                    traceId,
                    studentUserId,
                    "MOCK_PAYMENT_SUCCESS",
                    "ORDER",
                    orderNo,
                    toJson(Map.of("reason", normalizedReason == null ? "" : normalizedReason, "amountFen", order.amountFen()))
            );
        }

        ConsultRepository.OrderDetailRow refreshed = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        return new PaymentSuccessResponse(orderNo, refreshed.status().name(), PaymentMode.MOCK.name(), alreadyProcessed, toIso(refreshed.paidAt()));
    }

    @Transactional
    public String handleSandboxCallback(Map<String, String> form) {
        if (paymentProperties.getSandbox().isVerifyEnabled()
                && !AlipaySignatureVerifier.verify(form, paymentProperties.getSandbox().resolveAlipayPublicKey(), firstNonBlank(form.get("sign_type"), paymentProperties.getSandbox().getSignType()))) {
            return "failure";
        }
        return consultPaymentGatewayService.handleSandboxCallback(form);
    }

    @Transactional
    public ConsultOrderCancelResponse cancelOrder(long studentUserId, String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        if (order.status() != ConsultOrderStatus.CREATED && order.status() != ConsultOrderStatus.PAYING) {
            throw new ApiException("BIZ-1001", "order not cancelable", HttpStatus.BAD_REQUEST);
        }
        if (!cancelPendingOrderInternal(order, PendingCancelReason.MANUAL)) {
            throw new ApiException("BIZ-1001", "order cancel failed", HttpStatus.BAD_REQUEST);
        }
        return new ConsultOrderCancelResponse(orderNo, ConsultOrderStatus.CANCELED.name(), Instant.now().toEpochMilli());
    }

    @Transactional
    public ConsultOrderCloseResponse closeOrder(long studentUserId, String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        if (order.status() != ConsultOrderStatus.ANSWERED) {
            throw new ApiException("BIZ-1001", "order not answered", HttpStatus.BAD_REQUEST);
        }
        consultRepository.markOrderClosed(order.orderId(), orderNo);
        mentorRepository.incrementTotalOrders(order.mentorUserId());
        evictMentorPublicDetailCache(order.mentorUserId());
        evictMentorDashboardCache(order.mentorUserId());
        notificationService.createNotification(order.mentorUserId(), "CONSULT_CLOSED", "学生已确认并关闭咨询订单。", orderNo);
        return new ConsultOrderCloseResponse(orderNo, ConsultOrderStatus.CLOSED.name(), Instant.now().toEpochMilli());
    }

    @Transactional
    public ConsultReviewResponse createReview(long studentUserId, String orderNo, ConsultReviewCreateRequest request) {
        ConsultRepository.OrderDetailRow order = getRequiredParticipantOrder(studentUserId, UserRole.STUDENT.name(), orderNo);
        ensureStudentOrderOwner(order, studentUserId);
        if (order.status() != ConsultOrderStatus.CLOSED) {
            throw new ApiException("BIZ-1001", "order not closed", HttpStatus.BAD_REQUEST);
        }
        if (order.reviewRating() != null) {
            throw new ApiException("BIZ-1001", "review already exists", HttpStatus.BAD_REQUEST);
        }

        String normalizedComment = TextListCodec.normalizeText(request.comment());
        consultRepository.createReview(order.orderId(), orderNo, request.rating(), normalizedComment);
        mentorRepository.refreshAverageRating(order.mentorUserId());
        evictMentorPublicDetailCache(order.mentorUserId());
        evictMentorDashboardCache(order.mentorUserId());
        notificationService.createNotification(order.mentorUserId(), "CONSULT_REVIEWED", "学生已提交本次咨询评价，可查看最新反馈与评分。", orderNo);
        BigDecimal mentorAvgRating = consultRepository.findMentorAverageRating(order.mentorUserId())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new ConsultReviewResponse(orderNo, request.rating(), normalizedComment, Instant.now().toEpochMilli(), mentorAvgRating);
    }

    private ConsultRepository.OrderDetailRow getRequiredParticipantOrder(long userId, String role, String orderNo) {
        ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
        order = refreshOrderIfTimedOut(order);
        order = consultAfterSalesService.refreshOrderIfMentorReplyTimedOut(order);
        UserRole userRole = parseSupportedRole(role);
        boolean participant = userRole == UserRole.STUDENT
                ? order.studentUserId() == userId
                : order.mentorUserId() == userId;
        if (!participant) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
        return order;
    }

    private ConsultOrderDetailResponse toOrderDetailResponse(ConsultRepository.OrderDetailRow order) {
        List<ConsultOrderAttachmentItem> currentAttachments = consultRepository.findAttachments(order.orderId(), order.orderNo(), true, false)
                .stream()
                .map(this::toAttachmentItem)
                .toList();
        ConsultOrderDetailResponse.ReviewSummary review = order.reviewRating() == null
                ? null
                : new ConsultOrderDetailResponse.ReviewSummary(order.reviewRating(), order.reviewComment(), toIso(order.reviewCreatedAt()));
        return new ConsultOrderDetailResponse(
                order.orderNo(),
                order.studentUserId(),
                order.studentDisplayName(),
                order.mentorUserId(),
                order.mentorDisplayName(),
                order.amountFen(),
                order.status().name(),
                order.sceneCode(),
                order.sourcePage(),
                order.questionText(),
                deserializeQuestionPayload(order.questionPayloadJson()),
                order.problemSummary(),
                deserializeJsonStringList(order.coreQuestionsJson()),
                deserializeJsonStringList(order.expectedOutcomesJson()),
                TextListCodec.split(order.selectedMaterialTypes()),
                deserializePrepSheetSnapshot(order.prepSheetSnapshotJson()),
                buildAttachmentsSummary(currentAttachments),
                buildStudentProfileSummary(order.studentUserId()),
                order.paymentMode(),
                toIso(order.appointmentStartAt()),
                toIso(order.appointmentEndAt()),
                toIso(order.createdAt()),
                toIso(order.paidAt()),
                toIso(order.closedAt()),
                resolveAutoCancelAt(order.status(), order.createdAt(), order.paidAt()),
                consultAfterSalesService.resolveMentorReplyDeadlineAt(order),
                review,
                consultAfterSalesService.getOrderRequestSummaries(order.orderId(), order.orderNo())
        );
    }

    private String buildMentorReplyDraftTitle(ConsultOrderDetailResponse detail) {
        String topic = firstNonBlank(
                detail.questionPayload() == null ? null : detail.questionPayload().primaryConcern(),
                detail.problemSummary(),
                detail.questionText(),
                "导师履约回复草稿"
        );
        return "咨询订单 " + detail.orderNo() + " / " + firstNonBlank(detail.studentDisplayName(), "学生") + " / " + topic;
    }

    private String buildMentorReplyDraftContext(ConsultOrderDetailResponse detail, List<ConsultRepository.MessageRow> messageRows) {
        List<String> lines = new ArrayList<>();
        lines.add("orderNo=" + detail.orderNo());
        lines.add("orderStatus=" + firstNonBlank(detail.status(), "UNKNOWN"));
        appendContextLine(lines, "studentDisplayName", detail.studentDisplayName());
        appendContextLine(lines, "mentorDisplayName", detail.mentorDisplayName());
        appendContextLine(lines, "sceneCode", detail.sceneCode());
        appendContextLine(lines, "serviceType", detail.appointmentStartAt() != null || detail.appointmentEndAt() != null ? "APPOINTMENT" : "TEXT_CONSULT");
        if (detail.appointmentStartAt() != null || detail.appointmentEndAt() != null) {
            lines.add("appointmentWindow=" + firstNonBlank(formatEpochMillis(detail.appointmentStartAt()), "TBD") + " -> " + firstNonBlank(formatEpochMillis(detail.appointmentEndAt()), "TBD"));
        }
        appendContextLine(lines, "mentorReplyDeadlineAt", formatEpochMillis(detail.mentorReplyDeadlineAt()));
        appendContextLine(lines, "questionText", detail.questionText());
        if (detail.questionPayload() != null) {
            appendContextLine(lines, "primaryConcern", detail.questionPayload().primaryConcern());
            appendContextLine(lines, "background", detail.questionPayload().background());
            appendContextLine(lines, "attemptedActions", detail.questionPayload().attemptedActions());
            appendContextLine(lines, "expectedHelp", detail.questionPayload().expectedHelp());
            appendContextLine(lines, "additionalNotes", detail.questionPayload().additionalNotes());
        }
        appendContextLine(lines, "problemSummary", detail.problemSummary());
        appendContextLine(lines, "coreQuestions", joinNormalizedItems(detail.coreQuestions()));
        appendContextLine(lines, "expectedOutcomes", joinNormalizedItems(detail.expectedOutcomes()));
        appendContextLine(lines, "selectedMaterialTypes", joinNormalizedItems(detail.selectedMaterialTypes()));
        if (detail.prepSheetSnapshot() != null) {
            appendContextLine(lines, "prepScene", detail.prepSheetSnapshot().scene());
            appendContextLine(lines, "prepSummary", detail.prepSheetSnapshot().summaryDraft());
            appendContextLine(lines, "prepCoreQuestions", joinNormalizedItems(detail.prepSheetSnapshot().coreQuestions()));
            appendContextLine(lines, "prepSuggestedMaterials", joinNormalizedItems(detail.prepSheetSnapshot().suggestedMaterials()));
            appendContextLine(lines, "prepExpectedOutcomes", joinNormalizedItems(detail.prepSheetSnapshot().expectedOutcomes()));
        }
        if (detail.attachmentsSummary() != null) {
            appendContextLine(lines, "currentAttachmentCount", String.valueOf(detail.attachmentsSummary().currentAttachmentCount()));
            appendContextLine(lines, "currentAttachmentTypes", joinNormalizedItems(detail.attachmentsSummary().currentMaterialTypes()));
            List<String> attachmentRecords = detail.attachmentsSummary().records().stream()
                    .map(item -> firstNonBlank(item.attachmentType(), "ATTACHMENT") + ":" + firstNonBlank(item.originalFilename(), "unnamed"))
                    .toList();
            appendContextLine(lines, "attachmentRecords", joinNormalizedItems(attachmentRecords));
        }
        if (detail.studentProfile() != null) {
            appendContextLine(lines, "studentJobStatus", detail.studentProfile().jobStatus());
            appendContextLine(lines, "studentSchoolName", detail.studentProfile().schoolName());
            appendContextLine(lines, "studentMajor", detail.studentProfile().major());
            appendContextLine(lines, "studentGrade", detail.studentProfile().grade());
            appendContextLine(lines, "studentGpa", detail.studentProfile().gpa());
            appendContextLine(lines, "studentTargetPosition", detail.studentProfile().targetPosition());
            appendContextLine(lines, "studentHonors", detail.studentProfile().honors());
            appendContextLine(lines, "studentSkillTags", joinNormalizedItems(detail.studentProfile().skillTags()));
            appendContextLine(lines, "studentSelfIntro", detail.studentProfile().selfIntro());
            List<String> portraitTags = detail.studentProfile().portraitTags().stream()
                    .map(item -> firstNonBlank(item.label(), item.code(), item.source()))
                    .toList();
            appendContextLine(lines, "studentPortraitTags", joinNormalizedItems(portraitTags));
        }
        lines.add("messageThread=");
        List<ConsultRepository.MessageRow> recentMessages = messageRows == null || messageRows.isEmpty()
                ? List.of()
                : messageRows.subList(Math.max(messageRows.size() - 10, 0), messageRows.size());
        if (recentMessages.isEmpty()) {
            lines.add("暂无历史消息，仅有订单问题描述。");
        } else {
            recentMessages.forEach(message -> lines.add(
                    firstNonBlank(message.senderRole(), "UNKNOWN")
                            + "@" + firstNonBlank(formatEpochMillis(message.createdAt() == null ? null : message.createdAt().toEpochMilli()), "UNKNOWN_TIME")
                            + ": " + firstNonBlank(message.messageText(), "(empty)")
            ));
        }
        return String.join("\n", lines);
    }

    private void appendContextLine(List<String> lines, String key, String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null) {
            lines.add(key + "=" + normalized);
        }
    }

    private String joinNormalizedItems(List<String> values) {
        List<String> normalized = TextListCodec.normalize(values);
        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(" | ", normalized);
    }

    private void evictMentorPublicDetailCache(long mentorUserId) {
        mentorPublicDetailCacheService.evictNow(mentorUserId);
        mentorPublicDetailCacheService.evictAfterCommit(mentorUserId);
        mentorPublicListCacheService.evictAllNow();
        mentorPublicListCacheService.evictAllAfterCommit();
    }

    private void evictMentorDashboardCache(long mentorUserId) {
        mentorDashboardCacheService.evictNow(mentorUserId);
        mentorDashboardCacheService.evictAfterCommit(mentorUserId);
    }

    private ConsultOrderDetailResponse.QuestionPayloadSnapshot deserializeQuestionPayload(String rawJson) {
        JsonNode root = readJsonTree(rawJson);
        if (root == null || !root.isObject()) {
            return null;
        }
        return new ConsultOrderDetailResponse.QuestionPayloadSnapshot(
                readTextField(root, "primaryConcern"),
                readTextField(root, "background"),
                readTextField(root, "attemptedActions"),
                readTextField(root, "expectedHelp"),
                readTextField(root, "additionalNotes")
        );
    }

    private ConsultOrderDetailResponse.PrepSheetSnapshot deserializePrepSheetSnapshot(String rawJson) {
        JsonNode root = readJsonTree(rawJson);
        if (root == null || !root.isObject()) {
            return null;
        }
        return new ConsultOrderDetailResponse.PrepSheetSnapshot(
                readTextField(root, "scene"),
                readTextField(root, "summaryDraft"),
                readTextArray(root.get("coreQuestions")),
                readTextArray(root.get("suggestedMaterials")),
                readTextArray(root.get("expectedOutcomes"))
        );
    }

    private ConsultOrderDetailResponse.AttachmentsSummary buildAttachmentsSummary(List<ConsultOrderAttachmentItem> currentAttachments) {
        List<String> materialTypes = currentAttachments.stream()
                .map(ConsultOrderAttachmentItem::attachmentType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        return new ConsultOrderDetailResponse.AttachmentsSummary(currentAttachments.size(), materialTypes, currentAttachments);
    }

    private ConsultOrderDetailResponse.StudentProfileSummary buildStudentProfileSummary(long studentUserId) {
        StudentProfileRepository.StudentProfileRow profileRow = studentProfileRepository.findStudentProfileByUserId(studentUserId).orElse(null);
        if (profileRow == null) {
            return null;
        }

        JsonNode privacyRoot = readJsonTree(studentProfileRepository.findPrivacySettingsJson(studentUserId).orElse(null));
        StudentProfileRepository.PortraitSnapshotRow portraitSnapshot = studentProfileRepository.findPortraitSnapshot(studentUserId).orElse(null);
        boolean portraitVisible = isMentorVisible(privacyRoot, "portrait", true);

        return new ConsultOrderDetailResponse.StudentProfileSummary(
                mentorVisibleText(profileRow.jobStatus(), privacyRoot, "jobStatus", true),
                mentorVisibleText(profileRow.schoolName(), privacyRoot, "eduInfo", true),
                mentorVisibleText(profileRow.major(), privacyRoot, "eduInfo", true),
                mentorVisibleText(profileRow.grade(), privacyRoot, "eduInfo", true),
                mentorVisibleText(profileRow.gpa(), privacyRoot, "academic", true),
                mentorVisibleText(profileRow.targetPosition(), privacyRoot, "targetPos", true),
                mentorVisibleText(profileRow.honors(), privacyRoot, "academic", true),
                isMentorVisible(privacyRoot, "skills", true) ? TextListCodec.split(profileRow.skillTags()) : List.of(),
                mentorVisibleText(profileRow.selfIntro(), privacyRoot, "intro", true),
                portraitVisible ? parsePortraitTags(portraitSnapshot == null ? null : portraitSnapshot.portraitTagsJson()) : List.of(),
                portraitVisible && portraitSnapshot != null && portraitSnapshot.updatedAt() != null ? portraitSnapshot.updatedAt().toEpochMilli() : null
        );
    }

    private String mentorVisibleText(String value, JsonNode privacyRoot, String sectionKey, boolean defaultVisible) {
        return isMentorVisible(privacyRoot, sectionKey, defaultVisible) ? TextListCodec.normalizeText(value) : null;
    }

    private boolean isMentorVisible(JsonNode privacyRoot, String sectionKey, boolean defaultVisible) {
        if (privacyRoot == null || !privacyRoot.isObject() || sectionKey == null) {
            return defaultVisible;
        }
        JsonNode sectionNode = privacyRoot.get(sectionKey);
        if (sectionNode == null || sectionNode.isNull()) {
            return defaultVisible;
        }
        JsonNode mentorNode = sectionNode.get("mentor");
        return mentorNode == null || mentorNode.isNull() ? defaultVisible : mentorNode.asBoolean(defaultVisible);
    }

    private List<ConsultOrderDetailResponse.PortraitTagSummary> parsePortraitTags(String rawJson) {
        JsonNode root = readJsonTree(rawJson);
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<ConsultOrderDetailResponse.PortraitTagSummary> items = new ArrayList<>();
        root.forEach(itemNode -> {
            if (!itemNode.isObject()) {
                return;
            }
            String label = readTextField(itemNode, "label");
            String code = readTextField(itemNode, "code");
            String source = readTextField(itemNode, "source");
            if (label != null || code != null || source != null) {
                items.add(new ConsultOrderDetailResponse.PortraitTagSummary(code, label, source));
            }
        });
        return List.copyOf(items);
    }

    private List<String> deserializeJsonStringList(String rawJson) {
        return readTextArray(readJsonTree(rawJson));
    }

    private JsonNode readJsonTree(String rawJson) {
        String normalized = TextListCodec.normalizeText(rawJson);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String readTextField(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return TextListCodec.normalizeText(node.get(fieldName).asText());
    }

    private List<String> readTextArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        node.forEach(itemNode -> {
            String value = TextListCodec.normalizeText(itemNode.asText());
            if (value != null) {
                items.add(value);
            }
        });
        return List.copyOf(items);
    }

    private void ensureStudentOrderOwner(ConsultRepository.OrderDetailRow order, long studentUserId) {
        if (order.studentUserId() != studentUserId) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureMentorOrderOwner(ConsultRepository.OrderDetailRow order, long mentorUserId) {
        if (order.mentorUserId() != mentorUserId) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureOrderAllowsStudentMessage(ConsultOrderStatus status) {
        if (status == ConsultOrderStatus.CLOSED || status == ConsultOrderStatus.CANCELED || status == ConsultOrderStatus.FAILED || status == ConsultOrderStatus.REFUNDED) {
            throw new ApiException("BIZ-1001", "order already closed", HttpStatus.BAD_REQUEST);
        }
    }

    private ConsultRepository.OrderDetailRow refreshOrderIfTimedOut(ConsultRepository.OrderDetailRow order) {
        if (!isTimedOutUnpaidOrder(order)) {
            return order;
        }
        // 详情读取时也做一次懒取消，避免用户看到已经过期但仍显示可支付的订单。
        cancelPendingOrderInternal(order, PendingCancelReason.TIMEOUT);
        return consultRepository.findOrderDetail(order.orderNo()).orElse(order);
    }

    private boolean isTimedOutUnpaidOrder(ConsultRepository.OrderDetailRow order) {
        if (order.paidAt() != null) {
            return false;
        }
        if (order.status() != ConsultOrderStatus.CREATED && order.status() != ConsultOrderStatus.PAYING) {
            return false;
        }
        int timeoutMinutes = paymentProperties.getUnpaidTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            return false;
        }
        return !order.createdAt().isAfter(Instant.now().minusSeconds(timeoutMinutes * 60L));
    }

    private Long resolveAutoCancelAt(ConsultOrderStatus status, Instant createdAt, Instant paidAt) {
        if (paidAt != null) {
            return null;
        }
        if (status != ConsultOrderStatus.CREATED && status != ConsultOrderStatus.PAYING) {
            return null;
        }
        int timeoutMinutes = paymentProperties.getUnpaidTimeoutMinutes();
        if (timeoutMinutes <= 0 || createdAt == null) {
            return null;
        }
        return toIso(createdAt.plusSeconds(timeoutMinutes * 60L));
    }

    private boolean cancelPendingOrderInternal(ConsultRepository.OrderDetailRow order, PendingCancelReason reason) {
        boolean canceled = consultRepository.markOrderCanceled(order.orderId(), order.orderNo());
        if (!canceled) {
            return false;
        }
        evictMentorDashboardCache(order.mentorUserId());
        mentorScheduleService.releaseSlotForOrder(order.orderId(), order.orderNo());
        if (reason == PendingCancelReason.MANUAL) {
            if (hasReservedSlot(order)) {
                notificationService.createNotification(order.mentorUserId(), "CONSULT_CANCELED", "学生已取消未支付咨询订单，系统已释放相关预约时段。", order.orderNo());
            }
            return true;
        }
        notificationService.createNotification(order.studentUserId(), "CONSULT_TIMEOUT_CANCELED", "你的未支付咨询订单已超时取消，请根据需要重新下单。", order.orderNo());
        if (hasReservedSlot(order)) {
            notificationService.createNotification(order.mentorUserId(), "CONSULT_TIMEOUT_CANCELED", "某未支付咨询订单已超时取消，相关预约时段已释放。", order.orderNo());
        }
        return true;
    }

    private boolean hasReservedSlot(ConsultRepository.OrderDetailRow order) {
        return order.appointmentStartAt() != null || order.appointmentEndAt() != null;
    }

    private void ensureOrderAllowsMentorReplyDraft(ConsultOrderStatus status) {
        if (status != ConsultOrderStatus.PAID && status != ConsultOrderStatus.ANSWERED) {
            throw new ApiException("BIZ-1001", "mentor reply draft requires active paid order", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureOrderAllowsStudentAttachment(ConsultOrderStatus status) {
        ensureOrderAllowsStudentMessage(status);
    }

    private ConsultOrderAttachmentItem toAttachmentItem(ConsultRepository.OrderAttachmentRow attachment) {
        return new ConsultOrderAttachmentItem(
                attachment.id(),
                attachment.attachmentType(),
                attachment.slotCode(),
                attachment.originalFilename(),
                attachment.description(),
                attachment.sourceStage(),
                attachment.sizeBytes(),
                attachment.lifecycleStatus(),
                toIso(attachment.createdAt())
        );
    }

    private AttachmentUploadManifest parseAttachmentUploadManifest(String manifestJson) {
        try {
            AttachmentUploadManifest manifest = objectMapper.readValue(manifestJson, AttachmentUploadManifest.class);
            if (manifest == null || manifest.items() == null || manifest.items().isEmpty()) {
                throw new ApiException("BIZ-1001", "材料清单不能为空", HttpStatus.BAD_REQUEST);
            }
            return manifest;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "材料清单格式错误", HttpStatus.BAD_REQUEST);
        }
    }

    private AttachmentManifestItem normalizeAttachmentManifestItem(AttachmentManifestItem rawItem) {
        if (rawItem == null) {
            throw new ApiException("BIZ-1001", "材料清单项不能为空", HttpStatus.BAD_REQUEST);
        }
        String attachmentType = normalizeMaterialType(rawItem.attachmentType());
        boolean singleSlot = SINGLE_SLOT_ATTACHMENT_TYPES.contains(attachmentType);
        String slotCode = normalizeOptionalCode(rawItem.slotCode(), 60);
        if (slotCode == null) {
            slotCode = attachmentType;
        }
        boolean replaceCurrent = singleSlot || Boolean.TRUE.equals(rawItem.replaceCurrent());
        String sourceStage = normalizeOptionalCode(rawItem.sourceStage(), 30);
        if (sourceStage == null) {
            sourceStage = "ORDER_CREATE";
        }
        if (!ALLOWED_ATTACHMENT_SOURCE_STAGES.contains(sourceStage)) {
            throw new ApiException("BIZ-1001", "attachment sourceStage invalid", HttpStatus.BAD_REQUEST);
        }
        String description = normalizeOptionalText(rawItem.description());
        return new AttachmentManifestItem(attachmentType, slotCode, description, replaceCurrent, sourceStage);
    }

    private List<String> normalizeShortTextList(List<String> rawItems) {
        return TextListCodec.normalize(rawItems);
    }

    private List<String> normalizeMaterialTypeList(List<String> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String rawItem : rawItems) {
            normalized.add(normalizeMaterialType(rawItem));
        }
        return List.copyOf(normalized.stream().distinct().toList());
    }

    private String normalizeMaterialType(String rawValue) {
        String normalized = normalizeOptionalCode(rawValue, 40);
        if (normalized == null || !ALLOWED_ATTACHMENT_TYPES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "attachment type invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeWorkbenchFilter(String rawValue, Set<String> allowedValues, String defaultValue) {
        String normalized = normalizeOptionalCode(rawValue, 40);
        if (normalized == null || "ALL".equals(normalized)) {
            return defaultValue;
        }
        if (!allowedValues.contains(normalized)) {
            throw new ApiException("BIZ-1001", "workbench filter invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalCode(String rawValue, int maxLength) {
        String normalized = TextListCodec.normalizeText(rawValue);
        if (normalized == null) {
            return null;
        }
        String code = normalized.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (code.length() > maxLength) {
            throw new ApiException("BIZ-1001", "code too long", HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    private String normalizeOptionalText(String rawValue) {
        return TextListCodec.normalizeText(rawValue);
    }

    private MentorServicePackageResponse resolveSelectedPackage(long mentorUserId, Long mentorPackageId) {
        if (mentorPackageId == null) {
            return null;
        }
        return mentorServicePackageRepository.findEnabledPackageByIdForMentor(mentorUserId, mentorPackageId)
                .map(MentorServicePackageSupport::toResponse)
                .orElseThrow(() -> new ApiException("BIZ-1001", "mentor package invalid", HttpStatus.BAD_REQUEST));
    }

    private MentorScheduleService.ReservedSlot reserveOrderSlot(
            long mentorUserId,
            String orderNo,
            Long scheduleSlotId,
            MentorServicePackageResponse selectedPackage
    ) {
        if (selectedPackage == null) {
            return mentorScheduleService.reserveSlot(mentorUserId, scheduleSlotId, orderNo);
        }

        boolean appointmentPackage = MentorServicePackageSupport.DELIVERY_MODE_APPOINTMENT.equals(selectedPackage.deliveryMode());
        if (appointmentPackage && scheduleSlotId == null) {
            throw new ApiException("BIZ-1001", "schedule slot required", HttpStatus.BAD_REQUEST);
        }
        if (!appointmentPackage && scheduleSlotId != null) {
            throw new ApiException("BIZ-1001", "schedule slot not allowed", HttpStatus.BAD_REQUEST);
        }
        if (!appointmentPackage) {
            return null;
        }

        MentorScheduleService.ReservedSlot reservedSlot = mentorScheduleService.reserveSlot(mentorUserId, scheduleSlotId, orderNo);
        validateReservedSlotMatchesPackage(orderNo, reservedSlot, selectedPackage);
        return reservedSlot;
    }

    private void validateReservedSlotMatchesPackage(
            String orderNo,
            MentorScheduleService.ReservedSlot reservedSlot,
            MentorServicePackageResponse selectedPackage
    ) {
        if (reservedSlot == null || selectedPackage.durationMinutes() == null) {
            return;
        }
        long durationMinutes = java.time.Duration.between(reservedSlot.startAt(), reservedSlot.endAt()).toMinutes();
        if (durationMinutes == selectedPackage.durationMinutes()) {
            return;
        }
        mentorScheduleService.releaseSlotForOrder(orderNo);
        throw new ApiException("BIZ-1001", "schedule slot duration mismatch", HttpStatus.BAD_REQUEST);
    }

    private String serializeQuestionPayload(ConsultOrderCreateRequest.QuestionPayload payload) {
        if (payload == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "primaryConcern", payload.primaryConcern());
        putIfPresent(map, "background", payload.background());
        putIfPresent(map, "attemptedActions", payload.attemptedActions());
        putIfPresent(map, "expectedHelp", payload.expectedHelp());
        putIfPresent(map, "additionalNotes", payload.additionalNotes());
        return serializeJsonOrNull(map);
    }

    private String serializePrepSheetSnapshot(ConsultOrderCreateRequest.PrepSheetSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "scene", snapshot.scene());
        putIfPresent(map, "summaryDraft", snapshot.summaryDraft());
        putIfPresent(map, "coreQuestions", TextListCodec.normalize(snapshot.coreQuestions()));
        putIfPresent(map, "suggestedMaterials", TextListCodec.normalize(snapshot.suggestedMaterials()));
        putIfPresent(map, "expectedOutcomes", TextListCodec.normalize(snapshot.expectedOutcomes()));
        return serializeJsonOrNull(map);
    }

    private String serializeServicePackageSnapshot(MentorServicePackageResponse selectedPackage) {
        if (selectedPackage == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("packageId", selectedPackage.id());
        putIfPresent(map, "packageName", selectedPackage.packageName());
        putIfPresent(map, "sceneCode", selectedPackage.sceneCode());
        putIfPresent(map, "sceneLabel", selectedPackage.sceneLabel());
        putIfPresent(map, "deliveryMode", selectedPackage.deliveryMode());
        if (selectedPackage.durationMinutes() != null) {
            map.put("durationMinutes", selectedPackage.durationMinutes());
        }
        map.put("priceFen", selectedPackage.priceFen());
        putIfPresent(map, "description", selectedPackage.description());
        return serializeJsonOrNull(map);
    }

    private void putIfPresent(Map<String, Object> bucket, String key, String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null) {
            bucket.put(key, normalized);
        }
    }

    private void putIfPresent(Map<String, Object> bucket, String key, List<String> values) {
        List<String> normalized = TextListCodec.normalize(values);
        if (!normalized.isEmpty()) {
            bucket.put(key, normalized);
        }
    }

    private String serializeJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
            return null;
        }
        if (value instanceof List<?> listValue && listValue.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize consult order snapshot", ex);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = TextListCodec.normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private UserRole parseSupportedRole(String role) {
        try {
            UserRole userRole = UserRole.parse(role);
            if (userRole != UserRole.STUDENT && userRole != UserRole.MENTOR) {
                throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
            }
            return userRole;
        } catch (IllegalArgumentException ex) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
    }

    private ConsultOrderStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return ConsultOrderStatus.parse(status);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String buildOrderNo() {
        return "ORD" + ORDER_NO_TIME_FORMAT.format(Instant.now()) + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private Long toIso(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private String formatEpochMillis(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize audit detail", ex);
        }
    }

    private void evictOperationsDashboardCache() {
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
    }

    private enum PendingCancelReason {
        MANUAL,
        TIMEOUT
    }

    private record AttachmentUploadManifest(
            List<AttachmentManifestItem> items
    ) {
    }

    public record AttachmentContentResponse(
            String filename,
            MediaType mediaType,
            byte[] bytes
    ) {
    }

    private record AttachmentManifestItem(
            String attachmentType,
            String slotCode,
            String description,
            Boolean replaceCurrent,
            String sourceStage
    ) {
    }
}
