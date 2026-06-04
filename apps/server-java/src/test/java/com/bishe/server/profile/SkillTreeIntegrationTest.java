package com.bishe.server.profile;

import com.bishe.server.skill.service.SkillNodeCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 技能树与学习进度最小闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillTreeIntegrationTest {

    private static final String ROOT_NODE_CODE = "programming_language_foundations";
    private static final String BRANCH_NODE_CODE = "java_programming";
    private static final String CHILD_NODE_CODE = "object_oriented_modeling";
    private static final int MIN_TOTAL_SKILL_NODES = 60;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillNodeCacheService skillNodeCacheService;

    @BeforeEach
    void setUp() {
        skillNodeCacheService.evictNow();
    }

    @Test
    void skillTree_shouldSupportQueryUpdateAndPortraitEvidenceRefresh() throws Exception {
        registerUser("STUDENT", "skill@example.com", "Passw0rd!", "SkillAlice");
        String studentToken = loginAndGetAccessToken("skill@example.com", "Passw0rd!");

        MvcResult initialTreeResult = mockMvc.perform(get("/api/v1/skills/tree")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.summary.mastered").value(0))
                .andExpect(jsonPath("$.data.summary.learning").value(0))
                .andReturn();

        JsonNode initialTreeData = objectMapper.readTree(initialTreeResult.getResponse().getContentAsString()).path("data");
        int totalSkillNodes = initialTreeData.path("nodes").size();
        assertThat(totalSkillNodes).isGreaterThanOrEqualTo(MIN_TOTAL_SKILL_NODES);
        assertThat(initialTreeData.path("summary").path("total").asInt()).isEqualTo(totalSkillNodes);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), ROOT_NODE_CODE).path("parentCode").isNull()).isTrue();
        assertThat(findNodeByCode(initialTreeData.path("nodes"), BRANCH_NODE_CODE).path("parentCode").asText()).isEqualTo(ROOT_NODE_CODE);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), CHILD_NODE_CODE).path("parentCode").asText()).isEqualTo(BRANCH_NODE_CODE);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), ROOT_NODE_CODE).path("resources").size()).isGreaterThan(0);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "backend_service_development").path("resources").size()).isGreaterThan(0);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "ai_programming_engineering").path("parentCode").asText()).isEqualTo(ROOT_NODE_CODE);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "prompt_context_engineering").path("parentCode").asText()).isEqualTo("ai_programming_engineering");
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "prompt_context_engineering").path("resources").size()).isGreaterThan(0);
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "vibe_coding_workflows").path("parentCode").asText()).isEqualTo("ai_native_development");
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "ai_coding_agent_systems").path("parentCode").asText()).isEqualTo("ai_programming_engineering");
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "tool_schema_design").path("parentCode").asText()).isEqualTo("context_window_orchestration");
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "ai_debug_refactor_loops").path("parentCode").asText()).isEqualTo("ai_pair_programming");
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "multi_agent_workflows").path("parentCode").asText()).isEqualTo("ai_coding_agent_tools");
        assertThat(findNodeByCode(initialTreeData.path("nodes"), "model_routing_cost_control").path("parentCode").asText()).isEqualTo("ai_eval_observability");
        assertThat(initialTreeData.path("relations").size()).isGreaterThan(0);

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "%s",
                                  "targetStatus": "LEARNING"
                                }
                                """.formatted(CHILD_NODE_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1201"));

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "%s",
                                  "targetStatus": "LEARNING"
                                }
                                """.formatted(CHILD_NODE_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1201"));

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "%s",
                                  "targetStatus": "MASTERED"
                                }
                                """.formatted(ROOT_NODE_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("progress updated"))
                .andExpect(jsonPath("$.data.nodeCode").value(ROOT_NODE_CODE))
                .andExpect(jsonPath("$.data.status").value("MASTERED"))
                .andExpect(jsonPath("$.data.portraitRefreshTriggered").value(true));

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "%s",
                                  "targetStatus": "LEARNING"
                                }
                                """.formatted(BRANCH_NODE_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCode").value(BRANCH_NODE_CODE))
                .andExpect(jsonPath("$.data.status").value("LEARNING"));

        mockMvc.perform(get("/api/v1/skills/tree")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.mastered").value(1))
                .andExpect(jsonPath("$.data.summary.learning").value(1))
                .andExpect(jsonPath("$.data.summary.notStarted").value(totalSkillNodes - 2));

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portrait.evidence.masteredSkills").value(1))
                .andExpect(jsonPath("$.data.portrait.evidence.learningSkills").value(1))
                .andExpect(jsonPath("$.data.portrait.updatedAt").isNotEmpty());
    }

    @Test
    void skillTree_shouldRejectMentorAccess() throws Exception {
        registerUser("MENTOR", "mentor-skill@example.com", "Passw0rd!", "MentorSkill");
        String mentorToken = loginAndGetAccessToken("mentor-skill@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/skills/tree")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        String body = String.format(
                "{" +
                        "\"role\":\"%s\"," +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"," +
                        "\"displayName\":\"%s\"" +
                        "}",
                role,
                email,
                password,
                displayName
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String loginBody = String.format(
                "{" +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"" +
                        "}",
                email,
                password
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private JsonNode findNodeByCode(JsonNode nodes, String nodeCode) {
        for (JsonNode node : nodes) {
            if (nodeCode.equals(node.path("nodeCode").asText())) {
                return node;
            }
        }
        throw new IllegalStateException("missing skill node: " + nodeCode);
    }
}
