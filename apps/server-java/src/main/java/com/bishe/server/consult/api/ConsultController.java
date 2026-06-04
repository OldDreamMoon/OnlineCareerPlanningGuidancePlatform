package com.bishe.server.consult.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestCreateRequest;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestCreateResponse;
import com.bishe.server.consult.dto.ConsultOrderAttachmentBatchUploadResponse;
import com.bishe.server.consult.dto.ConsultOrderAttachmentDeleteResponse;
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
import com.bishe.server.consult.service.ConsultService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 咨询订单接口：创建、列表、详情、消息、售后与评价。
 */
@Tag(name = "Consult", description = "咨询订单、消息、售后与评价接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/consult/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsultController {

    private final ConsultService consultService;

    public ConsultController(ConsultService consultService) {
        this.consultService = consultService;
    }

    @Operation(summary = "创建咨询订单")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ConsultOrderCreateResponse> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ConsultOrderCreateRequest request
    ) {
        // 创单只建立订单和问题快照，支付和材料上传由后续接口继续推进。
        return ApiResponse.ok(consultService.createOrder(principal.getUserId(), request), TraceId.next());
    }

    @Operation(summary = "获取我的订单列表")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @GetMapping
    public ApiResponse<ConsultOrderListResponse> listOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String status
    ) {
        // 列表读取前会懒刷新未支付和导师超时订单，避免前端看到过期状态。
        return ApiResponse.ok(consultService.listOrders(principal.getUserId(), principal.getRole(), page, size, status), TraceId.next());
    }

    @Operation(summary = "获取导师订单中心聚合视图")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping(path = "/mentor-workbench")
    public ApiResponse<ConsultMentorWorkbenchResponse> getMentorWorkbench(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceFilter,
            @RequestParam(required = false) String timeFilter,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String riskFilter,
            @RequestParam(required = false) String sortMode
    ) {
        // 导师工作台是服务端聚合分诊视图，筛选、风险和优先级都在 service/repository 处理。
        return ApiResponse.ok(
                consultService.getMentorWorkbench(
                        principal.getUserId(),
                        page,
                        size,
                        keyword,
                        status,
                        serviceFilter,
                        timeFilter,
                        paymentMode,
                        riskFilter,
                        sortMode
                ),
                TraceId.next()
        );
    }

    @Operation(summary = "获取订单详情")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @GetMapping(path = "/{orderNo}")
    public ApiResponse<ConsultOrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        // 详情按学生/导师角色返回同一订单的履约视图，并同步处理超时状态。
        return ApiResponse.ok(consultService.getOrderDetail(principal.getUserId(), principal.getRole(), orderNo), TraceId.next());
    }

    @Operation(summary = "批量上传咨询订单材料")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/attachments/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ConsultOrderAttachmentBatchUploadResponse> uploadAttachments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @RequestPart("manifestJson") String manifestJson,
            @RequestPart("files") List<MultipartFile> files
    ) {
        // manifestJson 负责声明 slotCode 和替换关系，文件流只按顺序配对保存。
        return ApiResponse.ok("attachments uploaded", consultService.uploadAttachments(principal.getUserId(), orderNo, manifestJson, files), TraceId.next());
    }

    @Operation(summary = "获取咨询订单材料列表")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @GetMapping(path = "/{orderNo}/attachments")
    public ApiResponse<ConsultOrderAttachmentListResponse> listAttachments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @RequestParam(defaultValue = "true") boolean currentOnly,
            @RequestParam(defaultValue = "false") boolean includeSuperseded
    ) {
        return ApiResponse.ok(consultService.listAttachments(principal.getUserId(), principal.getRole(), orderNo, currentOnly, includeSuperseded), TraceId.next());
    }

    @Operation(summary = "读取咨询订单材料内容")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @GetMapping(path = "/{orderNo}/attachments/{attachmentId}/content")
    public ResponseEntity<ByteArrayResource> getAttachmentContent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @PathVariable long attachmentId
    ) {
        ConsultService.AttachmentContentResponse content = consultService.readAttachment(
                principal.getUserId(),
                principal.getRole(),
                orderNo,
                attachmentId
        );
        ByteArrayResource resource = new ByteArrayResource(content.bytes());
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(content.filename()).build().toString())
                .contentLength(content.bytes().length)
                .body(resource);
    }

    @Operation(summary = "删除咨询订单当前材料")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping(path = "/{orderNo}/attachments/{attachmentId}")
    public ApiResponse<ConsultOrderAttachmentDeleteResponse> deleteAttachment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @PathVariable long attachmentId
    ) {
        return ApiResponse.ok("attachment deleted", consultService.deleteAttachment(principal.getUserId(), orderNo, attachmentId), TraceId.next());
    }

    @Operation(summary = "学生发起咨询售后申请")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/after-sales/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ConsultAfterSalesRequestCreateResponse> createAfterSalesRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @Valid @RequestBody ConsultAfterSalesRequestCreateRequest request
    ) {
        return ApiResponse.ok(consultService.createAfterSalesRequest(principal.getUserId(), orderNo, request), TraceId.next());
    }

    @Operation(summary = "发送咨询消息")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @PostMapping(path = "/{orderNo}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ConsultMessageCreateResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @Valid @RequestBody ConsultMessageCreateRequest request
    ) {
        // 导师首条正式回复会推动 PAID -> ANSWERED，并触发学生通知。
        return ApiResponse.ok(consultService.sendMessage(principal.getUserId(), principal.getRole(), orderNo, request), TraceId.next());
    }

    @Operation(summary = "获取咨询消息列表")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @GetMapping(path = "/{orderNo}/messages")
    public ApiResponse<ConsultMessageListResponse> listMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(consultService.listMessages(principal.getUserId(), principal.getRole(), orderNo), TraceId.next());
    }

    @Operation(summary = "导师生成咨询回复草稿")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/{orderNo}/ai-reply-draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ConsultMentorReplyDraftResponse> generateMentorReplyDraft(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @Valid @RequestBody ConsultMentorReplyDraftRequest request
    ) {
        String traceId = TraceId.next();
        // AI 草稿只辅助导师组织回复，真正履约仍要走 messages 接口。
        return ApiResponse.ok("generated", consultService.generateMentorReplyDraft(principal.getUserId(), orderNo, request, traceId), traceId);
    }

    @Operation(summary = "取消未支付咨询订单")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/cancel")
    public ApiResponse<ConsultOrderCancelResponse> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(consultService.cancelOrder(principal.getUserId(), orderNo), TraceId.next());
    }

    @Operation(summary = "学生查询支付状态")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/payment/query")
    public ApiResponse<ConsultPaymentQueryResponse> queryPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        String traceId = TraceId.next();
        // 支付查询会与支付网关/Mock 结果对齐，成功后订单进入 PAID。
        return ApiResponse.ok(consultService.queryPayment(traceId, principal.getUserId(), orderNo), traceId);
    }

    @Operation(summary = "学生关闭支付单")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/payment/close")
    public ApiResponse<ConsultPaymentCloseResponse> closePayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(consultService.closePayment(traceId, principal.getUserId(), orderNo), traceId);
    }

    @Operation(summary = "关闭咨询订单")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/close")
    public ApiResponse<ConsultOrderCloseResponse> closeOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(consultService.closeOrder(principal.getUserId(), orderNo), TraceId.next());
    }

    @Operation(summary = "提交咨询评价")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{orderNo}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ConsultReviewResponse> createReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @Valid @RequestBody ConsultReviewCreateRequest request
    ) {
        return ApiResponse.ok(consultService.createReview(principal.getUserId(), orderNo, request), TraceId.next());
    }
}
