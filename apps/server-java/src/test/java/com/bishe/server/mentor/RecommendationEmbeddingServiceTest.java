package com.bishe.server.mentor;

import com.bishe.server.mentor.service.RecommendationEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEmbeddingServiceTest {

    private final RecommendationEmbeddingService service = new RecommendationEmbeddingService(new ObjectMapper());

    @Test
    void embed_shouldPreferBackendMentorForBackendStudentSignals() {
        double[] studentVector = service.embed("""
                目标岗位：Java 后端开发工程师
                技能标签：Java、Spring Boot、Redis、MySQL
                最近面试短板：缓存一致性、系统设计表达
                最近简历建议：补充项目结果指标和技术取舍
                """);
        double backendSimilarity = service.cosineSimilarity(
                studentVector,
                service.embed("资深后端工程师，擅长 Java、Spring、Redis、MySQL、系统设计、简历诊断")
        );
        double frontendSimilarity = service.cosineSimilarity(
                studentVector,
                service.embed("资深前端工程师，擅长 React、TypeScript、作品集表达、性能优化")
        );

        assertThat(backendSimilarity).isGreaterThan(frontendSimilarity);
    }

    @Test
    void embed_shouldKeepProjectExpressionTextsSemanticallyClose() {
        double similarity = service.cosineSimilarity(
                service.embed("简历项目表达、量化结果、亮点提炼"),
                service.embed("擅长简历诊断、项目表达、结果量化")
        );

        assertThat(similarity).isGreaterThan(0.10);
    }
}
