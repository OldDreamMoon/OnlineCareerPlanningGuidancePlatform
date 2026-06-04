package com.bishe.server.certification.api;

import com.bishe.server.certification.dto.CertificationOwnViewResponse;
import com.bishe.server.certification.dto.CertificationSubmissionResponse;
import com.bishe.server.certification.service.CertificationService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.security.UserPrincipal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导师 / 企业正式认证资料接口。
 */
@Tag(name = "Certification", description = "导师 / 企业正式认证资料接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/certification", produces = MediaType.APPLICATION_JSON_VALUE)
public class CertificationController {

    private final CertificationService certificationService;

    public CertificationController(CertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @Operation(summary = "获取我的认证资料与历史提交")
    @PreAuthorize("hasAnyRole('MENTOR', 'ENTERPRISE')")
    @GetMapping(path = "/me")
    public ApiResponse<CertificationOwnViewResponse> getMine(@AuthenticationPrincipal UserPrincipal principal) {
        CertificationOwnViewResponse data = certificationService.getOwnView(principal.getUserId(), principal.getRole());
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "读取指定认证附件内容")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'ENTERPRISE')")
    @GetMapping(path = "/assets/{assetId}/content")
    public ResponseEntity<ByteArrayResource> getAssetContent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long assetId
    ) {
        CertificationService.AssetContentResponse content = certificationService.readAsset(principal.getUserId(), principal.getRole(), assetId);
        ByteArrayResource resource = new ByteArrayResource(content.bytes());
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(content.filename()).build().toString())
                .contentLength(content.bytes().length)
                .body(resource);
    }

    @Operation(summary = "重新提交我的认证资料")
    @PreAuthorize("hasAnyRole('MENTOR', 'ENTERPRISE')")
    @PostMapping(path = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CertificationSubmissionResponse> submitMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String realName,
            @RequestParam String companyName,
            @RequestParam String jobTitle,
            @RequestParam MultipartFile file
    ) {
        CertificationSubmissionResponse data = certificationService.submitOwnCertification(
                principal.getUserId(),
                principal.getRole(),
                realName,
                companyName,
                jobTitle,
                file
        );
        return ApiResponse.ok("certification submitted", data, TraceId.next());
    }
}
