package com.bishe.server.skill;

import com.bishe.server.skill.repository.AdminSkillOpsRepository;
import com.bishe.server.skill.repository.SkillRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 技能域 JPA + PostgreSQL 最小闭环验证。
 */
@DataJpaTest
@Import({SkillRepository.class, AdminSkillOpsRepository.class})
class SkillJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private AdminSkillOpsRepository adminSkillOpsRepository;

    @Test
    void skillRepositoryShouldReadAndWriteTreeAndProgressOnPostgres() {
        assertThat(skillRepository.existsStudentUser(1L)).isTrue();
        assertThat(skillRepository.existsStudentUser(999L)).isFalse();

        assertThat(skillRepository.findAllSkillNodes())
                .extracting(SkillRepository.SkillNodeRow::nodeCode)
                .contains(
                        "programming_language_foundations",
                        "java_programming",
                        "object_oriented_modeling",
                        "backend_service_development",
                        "transaction_consistency"
                );

        assertThat(skillRepository.findAllSkillResources())
                .extracting(SkillRepository.SkillResourceRow::resourceCode)
                .contains("skill_pg_root_doc", "skill_pg_backend_video");

        SkillRepository.SkillProgressSummary initialSummary = skillRepository.countProgressSummary(1L);
        assertThat(initialSummary.masteredCount()).isZero();
        assertThat(initialSummary.learningCount()).isZero();

        skillRepository.saveProgress(1L, "programming_language_foundations", "MASTERED");
        skillRepository.saveProgress(1L, "backend_service_development", "LEARNING");

        SkillRepository.SkillProgressSummary updatedSummary = skillRepository.countProgressSummary(1L);
        assertThat(updatedSummary.masteredCount()).isEqualTo(1);
        assertThat(updatedSummary.learningCount()).isEqualTo(1);

        assertThat(skillRepository.findProgressByStudentUserId(1L))
                .extracting(SkillRepository.SkillProgressRow::nodeCode, SkillRepository.SkillProgressRow::progressStatus)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("programming_language_foundations", "MASTERED"),
                        org.assertj.core.groups.Tuple.tuple("backend_service_development", "LEARNING")
                );

        skillRepository.deleteProgress(1L, "backend_service_development");

        SkillRepository.SkillProgressSummary afterDeleteSummary = skillRepository.countProgressSummary(1L);
        assertThat(afterDeleteSummary.masteredCount()).isEqualTo(1);
        assertThat(afterDeleteSummary.learningCount()).isZero();
    }

    @Test
    void adminSkillOpsRepositoryShouldSupportCrudAndAggregateViewOnPostgres() {
        assertThat(adminSkillOpsRepository.findAllNodes())
                .extracting(AdminSkillOpsRepository.AdminSkillNodeRow::nodeCode)
                .contains("java_programming", "backend_service_development");

        assertThat(adminSkillOpsRepository.findNodeByCode("java_programming"))
                .isPresent()
                .get()
                .satisfies(node -> {
                    assertThat(node.parentCode()).isEqualTo("programming_language_foundations");
                    assertThat(node.childCount()).isGreaterThanOrEqualTo(2);
                });

        adminSkillOpsRepository.createNode(
                "admin_pg_test_node",
                "管理员 PG 测试节点",
                "验证 PostgreSQL 下的节点写入",
                "backend_service_development",
                910
        );
        adminSkillOpsRepository.createNode(
                "admin_pg_delete_node",
                "管理员待删节点",
                "验证 PostgreSQL 下的节点删除",
                "backend_service_development",
                911
        );

        assertThat(adminSkillOpsRepository.findNodeByCode("admin_pg_test_node"))
                .isPresent()
                .get()
                .satisfies(node -> {
                    assertThat(node.parentCode()).isEqualTo("backend_service_development");
                    assertThat(node.parentLabel()).isEqualTo("后端服务开发");
                });

        adminSkillOpsRepository.createResource(
                "admin_pg_test_resource",
                "admin_pg_test_node",
                "article",
                "管理员 PG 资源",
                "平台测试",
                "阅读 6m",
                "https://example.com/pg-resource",
                301
        );
        adminSkillOpsRepository.createRelation(
                "admin_pg_test_node",
                "transaction_consistency",
                "BRIDGE",
                "PG 关联",
                401
        );

        assertThat(adminSkillOpsRepository.findResourceByCode("admin_pg_test_resource"))
                .isPresent()
                .get()
                .satisfies(resource -> {
                    assertThat(resource.nodeCode()).isEqualTo("admin_pg_test_node");
                    assertThat(resource.nodeLabel()).isEqualTo("管理员 PG 测试节点");
                });

        assertThat(adminSkillOpsRepository.findRelation("admin_pg_test_node", "transaction_consistency", "BRIDGE"))
                .isPresent()
                .get()
                .satisfies(relation -> assertThat(relation.label()).isEqualTo("PG 关联"));

        adminSkillOpsRepository.updateNode("admin_pg_test_node", "管理员 PG 测试节点 v2", "更新后的 PostgreSQL 描述", 912);
        adminSkillOpsRepository.updateResource(
                "admin_pg_test_resource",
                "admin_pg_test_node",
                "video",
                "管理员 PG 资源 v2",
                "平台测试升级",
                "观看 9m",
                "https://example.com/pg-resource-v2",
                302
        );
        adminSkillOpsRepository.updateRelation(
                "admin_pg_test_node",
                "transaction_consistency",
                "BRIDGE",
                "admin_pg_test_node",
                "transaction_consistency",
                "CO_LEARN",
                "PG 关联 v2",
                402
        );

        assertThat(adminSkillOpsRepository.findNodeByCode("admin_pg_test_node"))
                .isPresent()
                .get()
                .satisfies(node -> {
                    assertThat(node.label()).isEqualTo("管理员 PG 测试节点 v2");
                    assertThat(node.resourceCount()).isEqualTo(1);
                    assertThat(node.outboundRelationCount()).isEqualTo(1);
                });

        assertThat(adminSkillOpsRepository.findResourceByCode("admin_pg_test_resource"))
                .isPresent()
                .get()
                .satisfies(resource -> {
                    assertThat(resource.resourceType()).isEqualTo("video");
                    assertThat(resource.title()).isEqualTo("管理员 PG 资源 v2");
                });

        assertThat(adminSkillOpsRepository.findRelation("admin_pg_test_node", "transaction_consistency", "CO_LEARN"))
                .isPresent()
                .get()
                .satisfies(relation -> assertThat(relation.label()).isEqualTo("PG 关联 v2"));

        assertThat(adminSkillOpsRepository.deleteRelation("admin_pg_test_node", "transaction_consistency", "CO_LEARN")).isTrue();
        assertThat(adminSkillOpsRepository.deleteNode("admin_pg_delete_node")).isTrue();
        assertThat(adminSkillOpsRepository.findNodeByCode("admin_pg_delete_node")).isEmpty();
    }
}
