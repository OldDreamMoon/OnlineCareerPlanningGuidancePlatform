package com.bishe.server.ai.service;

import com.bishe.server.ai.dto.AiHistoryRecordItem;
import com.bishe.server.ai.dto.AiHistoryResponse;
import com.bishe.server.ai.dto.AiMetaPayload;
import com.bishe.server.ai.dto.AiModerationPayload;
import com.bishe.server.ai.dto.InterviewMessageItem;
import com.bishe.server.ai.dto.InterviewResumeContextDetailResponse;
import com.bishe.server.ai.dto.InterviewSessionContextDetailResponse;
import com.bishe.server.ai.dto.InterviewSessionDetailResponse;
import com.bishe.server.ai.dto.InterviewSummaryResponse;
import com.bishe.server.ai.dto.ResumeHistoryDetailResponse;
import com.bishe.server.ai.dto.ResumeHistoryPreviewResponse;
import com.bishe.server.ai.dto.ResumeRewriteItem;
import com.bishe.server.ai.dto.ResumeStructureItem;
import com.bishe.server.ai.history.AiHistoryRepository;
import com.bishe.server.ai.interview.AiInterviewRepository;
import com.bishe.server.ai.quota.AiTaskType;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.growth.repository.GrowthRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 使用历史服务：返回历史列表、详情、导出与删除能力。
 */
@Service
public class AiHistoryService {

    private static final AiModerationPayload PASS_MODERATION = new AiModerationPayload("AI_OUTPUT", "LOW", "PASS", "RULE_CLEAR");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ResumeStructureItem>> RESUME_STRUCTURE_ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ResumeRewriteItem>> RESUME_REWRITE_ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private final AiHistoryRepository aiHistoryRepository;
    private final AiInterviewRepository aiInterviewRepository;
    private final GrowthRepository growthRepository;
    private final ObjectMapper objectMapper;

    public AiHistoryService(
            AiHistoryRepository aiHistoryRepository,
            AiInterviewRepository aiInterviewRepository,
            GrowthRepository growthRepository,
            ObjectMapper objectMapper
    ) {
        this.aiHistoryRepository = aiHistoryRepository;
        this.aiInterviewRepository = aiInterviewRepository;
        this.growthRepository = growthRepository;
        this.objectMapper = objectMapper;
    }

    public AiHistoryResponse getHistory(long userId, int page, int size, String taskType) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;
        String normalizedTaskType = normalizeTaskType(taskType);

        List<AiHistoryRecordItem> records = aiHistoryRepository.findHistory(userId, normalizedTaskType, safeSize, offset).stream()
                .map(row -> new AiHistoryRecordItem(
                        row.id(),
                        row.taskType(),
                        row.summary(),
                        row.pointsConsumed(),
                        row.sessionId(),
                        row.sessionStatus(),
                        TimePayloads.toEpochMillis(row.createdAt())
                ))
                .toList();
        long total = aiHistoryRepository.countHistory(userId, normalizedTaskType);
        return new AiHistoryResponse(records, total, safePage, safeSize);
    }

    public ResumeHistoryPreviewResponse getResumePreview(long userId, long recordId) {
        AiHistoryRepository.ResumeHistoryDetailRow row = requireResumeDetailRow(userId, recordId);
        Map<String, Object> payload = readPayloadMap(row.resultPayloadJson());
        return new ResumeHistoryPreviewResponse(
                row.id(),
                row.resultSummary(),
                readStringList(payload.get("suggestions")),
                readString(payload.get("scoreLabel")),
                readString(payload.get("targetRole")),
                readString(payload.get("targetContext")),
                resolveResumeInputMode(payload),
                readString(payload.get("jobDescription")),
                readString(payload.get("resumeText")),
                readNullableString(payload.get("pdfFileName")),
                row.chargedPoints(),
                TimePayloads.toEpochMillis(row.createdAt())
        );
    }

    public ResumeHistoryDetailResponse getResumeDetail(long userId, long recordId) {
        AiHistoryRepository.ResumeHistoryDetailRow row = requireResumeDetailRow(userId, recordId);
        Map<String, Object> payload = readPayloadMap(row.resultPayloadJson());
        return new ResumeHistoryDetailResponse(
                row.id(),
                row.resultSummary(),
                readStringList(payload.get("strengths")),
                readStringList(payload.get("risks")),
                readStringList(payload.get("suggestions")),
                readString(payload.get("scoreLabel")),
                readResumeStructureItems(payload.get("structureItems")),
                readResumeRewriteItems(payload.get("rewriteItems")),
                readString(payload.get("targetRole")),
                readString(payload.get("targetContext")),
                resolveResumeInputMode(payload),
                readString(payload.get("jobDescription")),
                readString(payload.get("resumeText")),
                readNullableString(payload.get("pdfFileName")),
                row.chargedPoints(),
                TimePayloads.toEpochMillis(row.createdAt()),
                new AiMetaPayload("RESUME", null, null, row.latencyMs()),
                PASS_MODERATION
        );
    }

    public InterviewSessionDetailResponse getInterviewDetail(long userId, String sessionId) {
        AiInterviewRepository.InterviewSessionRow session = aiInterviewRepository.findSessionBySessionId(userId, sessionId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "interview session not found", HttpStatus.NOT_FOUND));
        List<InterviewMessageItem> messages = aiInterviewRepository.findMessages(session.id()).stream()
                .map(message -> new InterviewMessageItem(
                        message.senderRole(),
                        message.messageText(),
                        message.coachFeedback(),
                        message.scoreHint(),
                        message.audioObjectKey(),
                        TimePayloads.toEpochMillis(message.createdAt())
                ))
                .toList();
        InterviewSummaryResponse summary = buildPersistedSummary(session);
        InterviewSessionContextDetailResponse sessionContext = buildInterviewSessionContextDetail(session.sessionContextJson());
        InterviewResumeContextDetailResponse resumeContext = buildInterviewResumeContextDetail(session.resumeContextJson());
        return new InterviewSessionDetailResponse(
                session.sessionId(),
                session.targetRole(),
                session.mode(),
                session.status(),
                session.prepaidPoints(),
                session.reservedQuotaWeight(),
                growthRepository.getCurrentBalance(userId),
                session.replyRoundLimit(),
                session.replyRoundUsed(),
                session.endedByAi(),
                session.finishReason(),
                TimePayloads.toEpochMillis(session.createdAt()),
                TimePayloads.toEpochMillis(session.summaryGeneratedAt()),
                sessionContext,
                resumeContext,
                messages,
                summary
        );
    }

    public ExportedResumePdf exportResumePdf(long userId, long recordId) {
        ResumeHistoryDetailResponse detail = getResumeDetail(userId, recordId);
        byte[] bytes = buildResumePdf(detail);
        return new ExportedResumePdf("resume-report-" + recordId + ".pdf", bytes);
    }

    public void deleteResumeHistory(long userId, long recordId) {
        if (!aiHistoryRepository.softDeleteResumeRecord(userId, recordId)) {
            throw new ApiException("BIZ-1002", "resume history not found", HttpStatus.NOT_FOUND);
        }
    }

    public void deleteInterviewHistory(long userId, String sessionId) {
        if (!aiInterviewRepository.softDeleteSession(userId, sessionId)) {
            throw new ApiException("BIZ-1002", "interview session not found", HttpStatus.NOT_FOUND);
        }
    }

    private AiHistoryRepository.ResumeHistoryDetailRow requireResumeDetailRow(long userId, long recordId) {
        return aiHistoryRepository.findResumeDetail(userId, recordId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "resume history not found", HttpStatus.NOT_FOUND));
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return null;
        }
        try {
            AiTaskType parsed = AiTaskType.from(taskType);
            if (parsed != AiTaskType.RESUME && parsed != AiTaskType.INTERVIEW_TEXT) {
                throw new ApiException("BIZ-1001", "taskType invalid", HttpStatus.BAD_REQUEST);
            }
            return parsed.name();
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "taskType invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private InterviewSummaryResponse buildPersistedSummary(AiInterviewRepository.InterviewSessionRow session) {
        if (!session.summaryGenerated()) {
            return null;
        }
        return new InterviewSummaryResponse(
                session.summaryOverallScore() == null ? 0 : session.summaryOverallScore(),
                readStringList(session.summaryStrengthsJson()),
                readStringList(session.summaryWeaknessesJson()),
                readStringList(session.summarySuggestionsJson()),
                new AiMetaPayload(
                        "INTERVIEW_SUMMARY",
                        session.summaryProvider(),
                        session.summaryModel(),
                        session.summaryLatencyMs() == null ? 0L : session.summaryLatencyMs()
                ),
                PASS_MODERATION
        );
    }

    private Map<String, Object> readPayloadMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "history payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private InterviewSessionContextDetailResponse buildInterviewSessionContextDetail(String json) {
        Map<String, Object> payload = readPayloadMap(json);
        if (payload.isEmpty()) {
            return null;
        }
        return new InterviewSessionContextDetailResponse(
                readString(payload.get("interviewType")),
                readString(payload.get("interviewerStyle")),
                readString(payload.get("difficulty")),
                readString(payload.get("answerMode")),
                readString(payload.get("targetCompany")),
                readString(payload.get("targetJobDescription")),
                readStringList(payload.get("prepMaterialKeys")),
                readNullableBoolean(payload.get("answerHelperEnabled")),
                readStringList(payload.get("answerHelperCueKeys")),
                readString(payload.get("promptContext"))
        );
    }

    private InterviewResumeContextDetailResponse buildInterviewResumeContextDetail(String json) {
        Map<String, Object> payload = readPayloadMap(json);
        if (payload.isEmpty()) {
            return null;
        }
        return new InterviewResumeContextDetailResponse(
                payload.get("recordId") instanceof Number number ? number.longValue() : 0L,
                readString(payload.get("summary")),
                readStringList(payload.get("suggestions")),
                readNullableString(payload.get("scoreLabel")),
                readString(payload.get("targetRole")),
                readString(payload.get("targetContext")),
                resolveResumeInputMode(payload),
                readString(payload.get("jobDescription")),
                readNullableString(payload.get("pdfFileName")),
                readString(payload.get("resumeTextExcerpt")),
                readEpochMillis(payload.get("createdAt"))
        );
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> item == null ? "" : String.valueOf(item)).filter(item -> !item.isBlank()).toList();
        }
        if (value instanceof String text) {
            return readStringList(text);
        }
        return List.of();
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                return objectMapper.readValue(json, STRING_LIST_TYPE);
            }
            return List.of(json);
        } catch (IOException ex) {
            return List.of(json);
        }
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String readNullableString(Object value) {
        String normalized = readString(value);
        return normalized.isBlank() ? null : normalized;
    }

    private Boolean readNullableBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return null;
        }
        String normalized = readString(value);
        if (normalized.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return null;
    }

    private Long readEpochMillis(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String normalized = readString(value);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.matches("^-?\\d+$")) {
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Instant.parse(normalized).toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveResumeInputMode(Map<String, Object> payload) {
        String inputMode = readString(payload.get("inputMode"));
        if (!inputMode.isBlank()) {
            return inputMode;
        }
        return readNullableString(payload.get("pdfFileName")) != null ? "pdf" : "text";
    }

    private List<ResumeStructureItem> readResumeStructureItems(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(value, RESUME_STRUCTURE_ITEM_LIST_TYPE);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private List<ResumeRewriteItem> readResumeRewriteItems(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(value, RESUME_REWRITE_ITEM_LIST_TYPE);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private byte[] buildResumePdf(ResumeHistoryDetailResponse detail) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadPdfFont(document);
            PdfWriterState writerState = new PdfWriterState(document, font, 11f, 16f, 48f);
            writerState.writeTitle("Resume Optimization Report");
            writerState.writeParagraph("Record ID: " + detail.recordId());
            writerState.writeParagraph("Generated At: " + (detail.createdAt() == null ? "" : detail.createdAt()));
            writerState.writeParagraph("用时：" + detail.aiMeta().latencyMs() + " ms");
            writerState.writeParagraph("Points Consumed: " + detail.pointsConsumed());
            if (!safe(detail.targetRole()).isBlank()) {
                writerState.writeParagraph("Target Role: " + safe(detail.targetRole()));
            }
            if (!safe(detail.targetContext()).isBlank()) {
                writerState.writeParagraph("Target Context: " + safe(detail.targetContext()));
            }
            if (!safe(detail.inputMode()).isBlank()) {
                writerState.writeParagraph("Input Mode: " + safe(detail.inputMode()));
            }
            if (!safe(detail.pdfFileName()).isBlank()) {
                writerState.writeParagraph("PDF File: " + safe(detail.pdfFileName()));
            }
            if (!safe(detail.scoreLabel()).isBlank()) {
                writerState.writeParagraph("Score Label: " + safe(detail.scoreLabel()));
            }
            writerState.writeBlankLine();
            writerState.writeSection("Summary", List.of(detail.summary()));
            writerState.writeSection("Strengths", detail.strengths());
            writerState.writeSection("Risks", detail.risks());
            writerState.writeSection("Suggestions", detail.suggestions());
            writerState.writeSection(
                    "Structure Map",
                    detail.structureItems().stream()
                            .map(item -> item.label() + " [" + item.score() + "/5] " + item.tip())
                            .toList()
            );
            writerState.writeSection(
                    "Rewrite Sandbox",
                    detail.rewriteItems().stream()
                            .map(item -> item.title() + " | " + item.problem() + " | " + item.afterText())
                            .toList()
            );
            writerState.finish();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "resume pdf export failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private PDFont loadPdfFont(PDDocument document) {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private String encodeForPdf(PDFont font, String rawText) {
        String value = safe(rawText)
                .replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
        StringBuilder builder = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (codePoint == ' ' || (codePoint >= 0x21 && codePoint <= 0x7E)) {
                builder.appendCodePoint(codePoint);
            } else {
                builder.append(String.format("\\u%04X", codePoint));
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record ExportedResumePdf(String fileName, byte[] bytes) {
    }

    private final class PdfWriterState {
        private final PDDocument document;
        private final PDFont font;
        private final float fontSize;
        private final float leading;
        private final float margin;
        private PDPage page;
        private PDPageContentStream stream;
        private float cursorY;

        private PdfWriterState(PDDocument document, PDFont font, float fontSize, float leading, float margin) throws IOException {
            this.document = document;
            this.font = font;
            this.fontSize = fontSize;
            this.leading = leading;
            this.margin = margin;
            openNewPage();
        }

        private void writeTitle(String title) throws IOException {
            writeLine(title, 14f);
            writeBlankLine();
        }

        private void writeSection(String title, List<String> items) throws IOException {
            writeLine(title, 12f);
            if (items == null || items.isEmpty()) {
                writeParagraph("- N/A");
                writeBlankLine();
                return;
            }
            for (String item : items) {
                writeParagraph("- " + safe(item));
            }
            writeBlankLine();
        }

        private void writeParagraph(String text) throws IOException {
            List<String> lines = wrapText(text, fontSize);
            if (lines.isEmpty()) {
                writeLine("", fontSize);
                return;
            }
            for (String line : lines) {
                writeLine(line, fontSize);
            }
        }

        private void writeBlankLine() throws IOException {
            ensureSpace(leading);
            cursorY -= leading;
        }

        private void writeLine(String rawText, float currentFontSize) throws IOException {
            ensureSpace(leading);
            String encodedText = encodeForPdf(font, rawText);
            stream.beginText();
            stream.setFont(font, currentFontSize);
            stream.newLineAtOffset(margin, cursorY);
            stream.showText(encodedText);
            stream.endText();
            cursorY -= leading;
        }

        private List<String> wrapText(String rawText, float currentFontSize) throws IOException {
            String encoded = encodeForPdf(font, rawText);
            if (encoded.isBlank()) {
                return List.of("");
            }
            float maxWidth = page.getMediaBox().getWidth() - margin * 2;
            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            for (String token : encoded.split(" ")) {
                String candidate = currentLine.length() == 0 ? token : currentLine + " " + token;
                float width = font.getStringWidth(candidate) / 1000 * currentFontSize;
                if (width <= maxWidth) {
                    currentLine.setLength(0);
                    currentLine.append(candidate);
                    continue;
                }
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                if (font.getStringWidth(token) / 1000 * currentFontSize <= maxWidth) {
                    currentLine.append(token);
                    continue;
                }
                StringBuilder chunk = new StringBuilder();
                for (int index = 0; index < token.length(); index++) {
                    String candidateChunk = chunk.toString() + token.charAt(index);
                    if (font.getStringWidth(candidateChunk) / 1000 * currentFontSize > maxWidth && chunk.length() > 0) {
                        lines.add(chunk.toString());
                        chunk.setLength(0);
                    }
                    chunk.append(token.charAt(index));
                }
                currentLine.append(chunk);
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
            return lines;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (cursorY - requiredHeight >= margin) {
                return;
            }
            closeStream();
            openNewPage();
        }

        private void openNewPage() throws IOException {
            this.page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            this.stream = new PDPageContentStream(document, page);
            this.cursorY = page.getMediaBox().getHeight() - margin;
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private void finish() throws IOException {
            closeStream();
        }
    }
}
