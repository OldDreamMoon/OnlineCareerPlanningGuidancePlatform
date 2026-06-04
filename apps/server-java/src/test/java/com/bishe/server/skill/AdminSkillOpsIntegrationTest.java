package com.bishe.server.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员技能资源治理集成回归测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminSkillOpsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminSkillOps_shouldSupportOverviewListAndMutations() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(get("/api/v1/admin/skills/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalNodeCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.totalResourceCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.nodesWithoutResourceCount").value(0));

        mockMvc.perform(get("/api/v1/admin/skills/nodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='java_programming')].label").value(contains("Java 语言与工程实践")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='network_security')].label").value(contains("网络与安全")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='software_architecture_design')].parentCode").value(contains("requirement_analysis")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='distributed_systems')].parentCode").value(contains("network_security")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='wellbeing_self_management')].parentCode").value(contains("career_employment_readiness")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='service_layer_design')].parentCode").value(contains("backend_service_development")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='web_security')].parentCode").value(contains("network_protocols")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='container_kubernetes')].parentCode").value(contains("cloud_native_basics")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='networking_personal_brand')].parentCode").value(contains("job_market_research")));

        mockMvc.perform(get("/api/v1/admin/skills/preview-tree")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.nodes.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.summary.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.relations[?(@.sourceNodeCode=='web_security' && @.targetNodeCode=='secure_engineering')].relationType")
                        .value(contains("ADVANCE_TO")))
                .andExpect(jsonPath("$.data.relations[?(@.sourceNodeCode=='multi_agent_workflows' && @.targetNodeCode=='collaborative_development')].relationType")
                        .value(contains("BRIDGE")))
                .andExpect(jsonPath("$.data.relations[?(@.sourceNodeCode=='unit_integration_testing' && @.targetNodeCode=='ci_cd_pipeline')]").isEmpty())
                .andExpect(jsonPath("$.data.relations[?(@.sourceNodeCode=='sql_query_writing' && @.targetNodeCode=='query_optimization')]").isEmpty());

        mockMvc.perform(post("/api/v1/admin/skills/nodes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeCode": "admin_skill_ops_test_node",
                                  "label": "管理员测试节点",
                                  "description": "用于技能治理后台集成测试",
                                  "parentCode": "backend_service_development",
                                  "sortOrder": 991
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill node created"))
                .andExpect(jsonPath("$.data.nodeCode").value("admin_skill_ops_test_node"))
                .andExpect(jsonPath("$.data.parentCode").value("backend_service_development"))
                .andExpect(jsonPath("$.data.label").value("管理员测试节点"));

        mockMvc.perform(put("/api/v1/admin/skills/nodes/{nodeCode}", "admin_skill_ops_test_node")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "管理员测试节点 v2",
                                  "description": "更新后的节点说明",
                                  "sortOrder": 992
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill node updated"))
                .andExpect(jsonPath("$.data.nodeCode").value("admin_skill_ops_test_node"))
                .andExpect(jsonPath("$.data.label").value("管理员测试节点 v2"))
                .andExpect(jsonPath("$.data.sortOrder").value(992));

        mockMvc.perform(post("/api/v1/admin/skills/nodes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeCode": "admin_skill_ops_delete_node",
                                  "label": "管理员待删节点",
                                  "description": "用于验证删除节点接口",
                                  "parentCode": "backend_service_development",
                                  "sortOrder": 993
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill node created"))
                .andExpect(jsonPath("$.data.nodeCode").value("admin_skill_ops_delete_node"));

        mockMvc.perform(delete("/api/v1/admin/skills/nodes/{nodeCode}", "admin_skill_ops_delete_node")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill node deleted"));

        mockMvc.perform(get("/api/v1/admin/skills/nodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='admin_skill_ops_delete_node')]").isEmpty());

        mockMvc.perform(post("/api/v1/admin/skills/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceCode": "admin_skill_ops_test_resource",
                                  "nodeCode": "admin_skill_ops_test_node",
                                  "resourceType": "article",
                                  "title": "管理员测试资源",
                                  "sourceLabel": "平台测试",
                                  "durationLabel": "阅读 8m",
                                  "linkUrl": "https://example.com/admin-skill-ops-test",
                                  "sortOrder": 301
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill resource created"))
                .andExpect(jsonPath("$.data.resourceCode").value("admin_skill_ops_test_resource"))
                .andExpect(jsonPath("$.data.nodeCode").value("admin_skill_ops_test_node"))
                .andExpect(jsonPath("$.data.title").value("管理员测试资源"));

        mockMvc.perform(put("/api/v1/admin/skills/resources/{resourceCode}", "admin_skill_ops_test_resource")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeCode": "admin_skill_ops_test_node",
                                  "resourceType": "video",
                                  "title": "管理员测试资源 v2",
                                  "sourceLabel": "平台测试升级",
                                  "durationLabel": "观看 12m",
                                  "linkUrl": "https://example.com/admin-skill-ops-test-v2",
                                  "sortOrder": 302
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill resource updated"))
                .andExpect(jsonPath("$.data.resourceCode").value("admin_skill_ops_test_resource"))
                .andExpect(jsonPath("$.data.resourceType").value("video"))
                .andExpect(jsonPath("$.data.title").value("管理员测试资源 v2"))
                .andExpect(jsonPath("$.data.sortOrder").value(302));

        mockMvc.perform(post("/api/v1/admin/skills/relations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceNodeCode": "admin_skill_ops_test_node",
                                  "targetNodeCode": "transaction_consistency",
                                  "relationType": "BRIDGE",
                                  "label": "管理员测试关联",
                                  "sortOrder": 401
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill relation created"))
                .andExpect(jsonPath("$.data.sourceNodeCode").value("admin_skill_ops_test_node"))
                .andExpect(jsonPath("$.data.targetNodeCode").value("transaction_consistency"))
                .andExpect(jsonPath("$.data.relationType").value("BRIDGE"))
                .andExpect(jsonPath("$.data.label").value("管理员测试关联"));

        mockMvc.perform(put("/api/v1/admin/skills/relations/{sourceNodeCode}/{targetNodeCode}/{relationType}",
                        "admin_skill_ops_test_node", "transaction_consistency", "BRIDGE")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceNodeCode": "admin_skill_ops_test_node",
                                  "targetNodeCode": "transaction_consistency",
                                  "relationType": "CO_LEARN",
                                  "label": "管理员测试关联 v2",
                                  "sortOrder": 402
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill relation updated"))
                .andExpect(jsonPath("$.data.sourceNodeCode").value("admin_skill_ops_test_node"))
                .andExpect(jsonPath("$.data.targetNodeCode").value("transaction_consistency"))
                .andExpect(jsonPath("$.data.relationType").value("CO_LEARN"))
                .andExpect(jsonPath("$.data.label").value("管理员测试关联 v2"))
                .andExpect(jsonPath("$.data.sortOrder").value(402));

        mockMvc.perform(get("/api/v1/admin/skills/nodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='admin_skill_ops_test_node')].label").value(contains("管理员测试节点 v2")))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='admin_skill_ops_test_node')].resourceCount").value(contains(1)))
                .andExpect(jsonPath("$.data.records[?(@.nodeCode=='admin_skill_ops_test_node')].outboundRelationCount").value(contains(1)));

        mockMvc.perform(get("/api/v1/admin/skills/resources")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.resourceCode=='admin_skill_ops_test_resource')].title").value(contains("管理员测试资源 v2")))
                .andExpect(jsonPath("$.data.records[?(@.resourceCode=='admin_skill_ops_test_resource')].resourceType").value(contains("video")));

        mockMvc.perform(get("/api/v1/admin/skills/preview-tree")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relations[?(@.sourceNodeCode=='admin_skill_ops_test_node' && @.targetNodeCode=='transaction_consistency')].relationType")
                        .value(contains("CO_LEARN")));

        mockMvc.perform(delete("/api/v1/admin/skills/relations/{sourceNodeCode}/{targetNodeCode}/{relationType}",
                        "admin_skill_ops_test_node", "transaction_consistency", "CO_LEARN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin skill relation deleted"));

        mockMvc.perform(get("/api/v1/admin/skills/preview-tree")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relations[?(@.sourceNodeCode=='admin_skill_ops_test_node' && @.targetNodeCode=='transaction_consistency')]").isEmpty());
    }

    @Test
    void studentAccessAdminSkillOps_shouldReturn403() throws Exception {
        registerUser("STUDENT", "skill-admin-forbidden@example.com", "Passw0rd!", "SkillForbidden");
        String studentToken = loginAndGetAccessToken("skill-admin-forbidden@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/admin/skills/overview")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));

        mockMvc.perform(get("/api/v1/admin/skills/preview-tree")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "%s"
                                }
                                """.formatted(role, email, password, displayName)))
                .andExpect(status().isOk());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
