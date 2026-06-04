package com.bishe.server.mentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 本地可替换 embedding 服务：当前先使用稳定的 hashed bag-of-words 向量，
 * 后续若接入真实 embedding provider，可保持同一调用面替换实现。
 */
@Service
public class RecommendationEmbeddingService {

    public static final String MODEL_CODE = "LOCAL_HASHED_BOW_V1";
    private static final int VECTOR_DIM = 192;
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[\\s,，。！？；;:：、/\\\\|()（）\\[\\]{}<>“”\"'‘’]+");

    private final ObjectMapper objectMapper;

    public RecommendationEmbeddingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String modelCode() {
        return MODEL_CODE;
    }

    public int vectorDim() {
        return VECTOR_DIM;
    }

    public double[] embed(String text) {
        double[] vector = new double[VECTOR_DIM];
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return vector;
        }
        // 本地向量不追求语义模型精度，重点是稳定、可解释、可脱离外部 embedding 服务运行。
        List<String> tokens = tokenize(normalized);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            int hash = token.hashCode();
            int secondaryHash = Integer.rotateLeft(hash ^ 0x9E3779B9, 11);
            double weight = weightOf(token);
            // 主 hash 和 secondary hash 同时入桶，降低少量关键词碰撞带来的偶然性。
            accumulate(vector, hash, weight);
            accumulate(vector, secondaryHash, weight * 0.6);
        }
        normalizeInPlace(vector);
        return vector;
    }

    public double cosineSimilarity(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0;
        }
        // 向量已经归一化，点积就是余弦相似度；维度不一致时取交集维度。
        int size = Math.min(left.length, right.length);
        double score = 0.0;
        for (int index = 0; index < size; index++) {
            score += left[index] * right[index];
        }
        return Math.max(-1.0, Math.min(1.0, score));
    }

    public String serializeVector(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector == null ? new double[0] : vector);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize recommendation embedding vector", ex);
        }
    }

    public double[] deserializeVector(String json) {
        if (json == null || json.isBlank()) {
            return new double[0];
        }
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse recommendation embedding vector", ex);
        }
    }

    public String hashContent(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // hash 使用归一化文本，避免空格和大小写差异导致重复生成向量。
            byte[] bytes = digest.digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash recommendation content", ex);
        }
    }

    private List<String> tokenize(String normalized) {
        List<String> tokens = new ArrayList<>();
        for (String raw : TOKEN_SPLIT_PATTERN.split(normalized)) {
            String token = raw == null ? "" : raw.trim();
            if (token.isBlank()) {
                continue;
            }
            tokens.add(token);
            // 在基础 token 外补充英文别名、中文二字片段和业务锚点，提升轻量向量的召回稳定性。
            appendAsciiAliases(tokens, token);
            appendHanSignals(tokens, token);
        }
        appendCanonicalSignals(tokens, normalized);
        return tokens;
    }

    private void appendAsciiAliases(List<String> tokens, String token) {
        String compact = token.replaceAll("[^a-z0-9#+.-]", "");
        if (!compact.isBlank() && !compact.equals(token)) {
            tokens.add(compact);
        }
        // 常见技术词补别名，提升“typescript/ts”这类表达差异下的匹配。
        if (compact.contains("springboot")) {
            tokens.add("spring");
            tokens.add("boot");
        }
        if (compact.contains("typescript")) {
            tokens.add("ts");
        }
        if (compact.contains("javascript")) {
            tokens.add("js");
        }
    }

    private void appendHanSignals(List<String> tokens, String token) {
        String hanOnly = token.replaceAll("[^\\p{IsHan}]", "");
        if (hanOnly.length() < 2) {
            return;
        }
        // 中文没有天然空格，二字滑窗能给“前端工程/工程化”这类词更多匹配机会。
        if (hanOnly.length() <= 10) {
            tokens.add(hanOnly);
        }
        for (int index = 0; index < hanOnly.length() - 1; index++) {
            tokens.add(hanOnly.substring(index, index + 2));
        }
    }

    private void appendCanonicalSignals(List<String> tokens, String normalized) {
        // 业务锚点是人工规则层：把岗位方向、简历、面试等强信号映射到稳定 token。
        appendIfContains(tokens, normalized, "前端", "dir_frontend", "react", "typescript", "工程化");
        appendIfContains(tokens, normalized, "后端", "dir_backend", "java", "spring", "mysql", "redis", "系统设计");
        appendIfContains(tokens, normalized, "数据分析", "dir_data_analysis", "sql", "bi", "指标", "图表");
        appendIfContains(tokens, normalized, "数据产品", "dir_data_product", "产品", "分析");
        appendIfContains(tokens, normalized, "产品", "dir_product", "需求", "原型", "业务");
        appendIfContains(tokens, normalized, "算法", "dir_ai_algorithm", "机器学习", "模型", "推荐");
        appendIfContains(tokens, normalized, "ai", "dir_ai_algorithm", "llm", "prompt", "rag");
        appendIfContains(tokens, normalized, "测试", "dir_test", "自动化测试", "质量");
        appendIfContains(tokens, normalized, "运营", "dir_operations", "增长", "内容");
        appendIfContains(tokens, normalized, "转行", "career_transition", "跨专业", "岗位方向");
        appendIfContains(tokens, normalized, "简历", "resume_signal", "项目表达", "量化", "亮点", "成果");
        appendIfContains(tokens, normalized, "面试", "interview_signal", "复盘", "追问", "表达");
        appendIfContains(tokens, normalized, "offer", "offer_signal", "比较", "决策");
        appendIfContains(tokens, normalized, "校招", "delivery_signal", "投递", "批次");
        appendIfContains(tokens, normalized, "项目", "project_signal", "结果", "业务");
        appendIfContains(tokens, normalized, "系统设计", "design_signal", "架构", "缓存", "数据库", "并发");
    }

    private void appendIfContains(List<String> tokens, String normalized, String anchor, String... aliases) {
        String lowerAnchor = anchor.toLowerCase(Locale.ROOT);
        if (!normalized.contains(lowerAnchor)) {
            return;
        }
        tokens.add(lowerAnchor);
        for (String alias : aliases) {
            tokens.add(alias.toLowerCase(Locale.ROOT));
        }
    }

    private void accumulate(double[] vector, int hash, double weight) {
        int index = Math.floorMod(hash, VECTOR_DIM);
        int sign = ((hash >>> 1) & 1) == 0 ? 1 : -1;
        // 带符号累加能减轻不同 token 落入同一桶时的单向膨胀。
        vector[index] += sign * weight;
    }

    private double weightOf(String token) {
        if (token == null || token.isBlank()) {
            return 0.0;
        }
        if (token.startsWith("dir_")) {
            // 岗位方向是推荐的强约束，权重比普通词高。
            return 1.9;
        }
        if (token.endsWith("_signal")) {
            // 简历/面试/项目等信号词是中强约束，略高于普通关键词。
            return 1.7;
        }
        if (token.length() >= 6) {
            return 1.3;
        }
        if (token.length() >= 4) {
            return 1.15;
        }
        return 1.0;
    }

    private void normalizeInPlace(double[] vector) {
        double norm = 0.0;
        for (double item : vector) {
            norm += item * item;
        }
        if (norm <= 0.0) {
            return;
        }
        // 归一化后向量长度一致，后续相似度不会被文本长短直接放大。
        double scale = 1.0 / Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] * scale;
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replace('\u3000', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
