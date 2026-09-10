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

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.httpBasic("admin", "community-test-password");
    }

    private record CustomerInput(String name, String phone, String wechat, String tags, String notes) {
    }
}
