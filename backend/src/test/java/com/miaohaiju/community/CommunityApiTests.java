package com.miaohaiju.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import com.miaohaiju.community.ai.AiParseService;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommunityApiTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AiParseService ai;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM contact_records");
        jdbc.update("DELETE FROM consumptions");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM ai_call_logs");
        jdbc.update("DELETE FROM customers");
    }

    @Test
    void healthIsPublicAndBusinessApisRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product").value("海聚客户管理系统社区版"));

        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void customerContactConsumptionNotificationAndAuditFormARealLoop() throws Exception {
        long customerId = createCustomer("陈女士", "13800000001");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customers/{id}", customerId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"陈女士（已更新）","phone":"13800000001","wechat":"chen","tags":"重点","notes":"已人工核对"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("陈女士（已更新）"));

        mvc.perform(post("/api/v1/customers/{id}/contacts", customerId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactAt":"2026-08-03T00:30:00","channel":"微信","summary":"确认下周到店","nextContactAt":"2026-08-03T01:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("确认下周到店"));

        mvc.perform(post("/api/v1/customers/{id}/contacts", customerId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactAt":"2026-08-03T09:00:00","channel":"微信","summary":"错误时间测试","nextContactAt":"2026-08-03T08:00:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40021));

        mvc.perform(post("/api/v1/customers/{id}/consumptions", customerId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occurredAt":"2026-08-03T09:30:00","amount":299.00,"itemName":"基础服务","note":"现场支付"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(299.0));

        mvc.perform(get("/api/v1/dashboard/overview").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerCount").value(1))
                .andExpect(jsonPath("$.data.dueFollowUps").value(1))
                .andExpect(jsonPath("$.data.totalConsumption").value(299.0));

        mvc.perform(get("/api/v1/notifications").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].customerName").value("陈女士（已更新）"));

        mvc.perform(get("/api/v1/operation-logs").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void csvImportSkipsDuplicatePhoneAndExportPreventsFormulaInjection() throws Exception {
        String csv = "姓名,手机号,微信,标签,备注\r\n张女士,13800000002,zhang,重点,正常\r\n重复客户,13800000002,,,跳过\r\n公式客户,13900000003,,,@SUM(1+1)";
        MockMultipartFile file = new MockMultipartFile("file", "customers.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/customers/import").file(file).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.createdRows").value(2))
                .andExpect(jsonPath("$.data.skippedRows").value(1));

        byte[] exported = mvc.perform(get("/api/v1/customers/export").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String exportedText = new String(exported, StandardCharsets.UTF_8);
        assertThat(exportedText).contains("'@SUM(1+1)");
    }

    @Test
    void duplicatePhoneIsRejectedAndAiNeverFallsBackWhenModelIsMissing() throws Exception {
        createCustomer("甲客户", "13800000004");
        mvc.perform(post("/api/v1/customers")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"乙客户","phone":"13800000004","wechat":"","tags":"","notes":""}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        mvc.perform(post("/api/v1/ai/parse-customer")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"客户王女士，手机号 13800000005\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50310))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("外部模型未配置")));

        Integer fakeSuccess = jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_logs WHERE status = 'SUCCESS'", Integer.class);
        Integer configurationErrors = jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_logs WHERE status = 'CONFIGURATION_ERROR'", Integer.class);
        assertThat(fakeSuccess).isZero();
        assertThat(configurationErrors).isEqualTo(1);
    }

    private long createCustomer(String name, String phone) throws Exception {
        String body = objectMapper.writeValueAsString(new CustomerInput(name, phone, "", "重点", "测试客户"));
        String response = mvc.perform(post("/api/v1/customers")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("id").asLong();
    }

    @Test
    void crossIndustryFactsPersistFilterAndSurviveLegacyUpdates() throws Exception {
        List<String> industries = List.of("通用关系维护", "保险", "银行", "房地产", "企业服务", "教育培训", "专业服务", "零售", "公益合作");
        long insuranceId = 0;
        for (String industry : industries) {
            String body = objectMapper.writeValueAsString(Map.of("name", industry + "测试联系人", "relationship", Map.of(
                    "entityType", "个人", "industry", industry, "organization", "示例机构", "jobTitle", "项目联系人",
                    "email", "contact@example.com", "relationshipType", "合作伙伴", "stage", "需求沟通", "needs", "讨论服务范围，不涉及敏感资料")));
            String response = mvc.perform(post("/api/v1/customers").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship.industry").value(industry))
                    .andExpect(jsonPath("$.data.relationship.needs").value("讨论服务范围，不涉及敏感资料"))
                    .andReturn().getResponse().getContentAsString();
            if (industry.equals("保险")) insuranceId = objectMapper.readTree(response).path("data").path("id").asLong();
        }
        mvc.perform(get("/api/v1/customers").param("industry", "保险").param("stage", "需求沟通").param("keyword", "示例机构").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].relationship.jobTitle").value("项目联系人"));
        mvc.perform(get("/api/v1/customers").param("industry", "保险' OR 1=1 --").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/customers").param("keyword", "讨论服务范围").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(9));
        mvc.perform(put("/api/v1/customers/{id}", insuranceId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"保险测试联系人（旧客户端更新）\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship.industry").value("保险"))
                .andExpect(jsonPath("$.data.relationship.organization").value("示例机构"));
        mvc.perform(put("/api/v1/customers/{id}", insuranceId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"保险测试联系人\",\"relationship\":{\"industry\":\"保险\",\"stage\":\"持续维护\",\"needs\":\"约定服务回访\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship.stage").value("持续维护"))
                .andExpect(jsonPath("$.data.relationship.organization").isEmpty());
        mvc.perform(get("/api/v1/customers/{id}", insuranceId).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship.needs").value("约定服务回访"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumptions", Integer.class)).isZero();
    }

    @Test
    void csvCarriesRelationshipFieldsAndRollsBackInvalidBatch() throws Exception {
        String csv = "姓名,对象类型,行业,所属机构,职务,邮箱,关系类型,关系阶段,关注事项\n地产示例,个人,房地产,示例机构,经办人,agent@example.com,客户,方案跟进,=@关注区域\n银行示例,机构,银行,示例企业,,,合作伙伴,需求沟通,服务回访";
        mvc.perform(multipart("/api/v1/customers/import").file(new MockMultipartFile("file", "industry.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.createdRows").value(2));
        String exported = mvc.perform(get("/api/v1/customers/export").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(exported).contains("对象类型,行业,所属机构,职务,邮箱,关系类型,关系阶段,关注事项", "房地产", "'=@关注区域", "agent@example.com");
        int logsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class);
        String badCsv = "姓名,行业,邮箱\n应回滚,保险,good@example.com\n错误邮箱,银行,not-an-email";
        mvc.perform(multipart("/api/v1/customers/import").file(new MockMultipartFile("file", "bad.csv", "text/csv", badCsv.getBytes(StandardCharsets.UTF_8))).with(admin()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class)).isEqualTo(logsBefore);
    }

    @Test
    void validationCoversNestedFieldsAndDoesNotOverwriteFactsOnFailure() throws Exception {
        long id = createCustomer("测试对象", "");
        mvc.perform(put("/api/v1/customers/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "不应保存", "relationship", Map.of("industry", "长".repeat(61))))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/customers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"错误邮箱\",\"relationship\":{\"email\":\"invalid\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/customers/{id}", id).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("测试对象"))
                .andExpect(jsonPath("$.data.relationship.industry").value("通用关系维护"));
        mvc.perform(get("/api/v1/customers").param("industry", "银行")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/operation-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void historicalAndUnknownActionsHaveChineseLabelsWithoutRewritingCodes() throws Exception {
        Map<String, String> labels = Map.of("CUSTOMER_CREATED", "新增客户", "CUSTOMER_UPDATED", "更新客户资料",
                "CUSTOMER_DELETED", "删除客户", "CUSTOMER_CSV_IMPORTED", "导入客户资料", "CONTACT_RECORDED", "记录沟通跟进",
                "CONSUMPTION_RECORDED", "记录消费", "FUTURE_ACTION", "其他操作");
        for (String action : labels.keySet()) jdbc.update("INSERT INTO operation_logs(action,target_type) VALUES (?, 'CUSTOMER')", action);
        String response = mvc.perform(get("/api/v1/operation-logs").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode item : objectMapper.readTree(response).path("data")) {
            assertThat(item.path("actionLabel").asText()).isEqualTo(labels.get(item.path("action").asText()));
            assertThat(item.path("targetLabel").asText()).isEqualTo("客户与关系档案");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action = 'FUTURE_ACTION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void nextContactAndDeletionKeepRelationshipOverviewConsistent() throws Exception {
        long id = createCustomer("银行服务示例", "");
        mvc.perform(post("/api/v1/customers/{id}/contacts", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactAt\":\"2026-01-01T10:00:00\",\"channel\":\"邮件\",\"summary\":\"约定回访\",\"nextContactAt\":\"2099-01-01T10:00:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/overview").with(admin())).andExpect(jsonPath("$.data.plannedFollowUps").value(1));
        mvc.perform(delete("/api/v1/customers/{id}", id).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/overview").with(admin())).andExpect(jsonPath("$.data.plannedFollowUps").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contact_records WHERE customer_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void aiResponseSchemaIncludesRelationsAndRejectsInvalidFieldsWithoutSaving() {
        // Synthetic model-response fixture verifies parsing only, not an actual model call.
        AiParseService.CustomerDraft parsed = ReflectionTestUtils.invokeMethod(ai, "readDraft",
                "{\"name\":\"保险示例\",\"relationship\":{\"industry\":\"保险\",\"needs\":\"续期服务回访\",\"organization\":\"示例机构\"}}");
        assertThat(parsed.relationship().industry()).isEqualTo("保险");
        assertThat(parsed.relationship().needs()).isEqualTo("续期服务回访");
        assertThat(parsed.relationship().email()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isZero();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(ai, "readDraft", "{\"name\":\"示例\",\"relationship\":{\"email\":\"wrong\"}}"))
                .isInstanceOf(com.miaohaiju.community.common.BusinessException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(ai, "readDraft", "{\"name\":\"示例\",\"relationship\":[]}"))
                .isInstanceOf(com.miaohaiju.community.common.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_logs WHERE status = 'SUCCESS'", Integer.class)).isZero();
    }

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.httpBasic("admin", "community-test-password");
    }

    private record CustomerInput(String name, String phone, String wechat, String tags, String notes) {
    }
}
