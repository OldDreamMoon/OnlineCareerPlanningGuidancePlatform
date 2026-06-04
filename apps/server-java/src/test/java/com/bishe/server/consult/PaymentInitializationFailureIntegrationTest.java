package com.bishe.server.consult;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付初始化失败时应把订单置为 FAILED，避免继续停留在 CREATED。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentInitializationFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bishe_pay_fail;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("payment.mode", () -> "SANDBOX");
        registry.add("payment.sandbox.pay-base-url", () -> "");
        registry.add("payment.sandbox.gateway-url", () -> "");
        registry.add("payment.sandbox.app-id", () -> "");
        registry.add("payment.sandbox.app-private-key", () -> "");
        registry.add("payment.sandbox.notify-url", () -> "");
        registry.add("payment.sandbox.return-url", () -> "");
        registry.add("payment.sandbox.verify-enabled", () -> "false");
    }

    @Test
    void createPayment_shouldMarkOrderFailedWhenSandboxConfigInvalid() throws Exception {
        long mentorUserId = registerUser("MENTOR", "pay-init-fail-mentor@example.com", "Passw0rd!", "PayInitFailMentor");
        registerUser("STUDENT", "pay-init-fail-student@example.com", "Passw0rd!", "PayInitFailStudent");
        String studentToken = loginAndGetAccessToken("pay-init-fail-student@example.com", "Passw0rd!");

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"mentorUserId\":" + mentorUserId + "," +
                                "\"questionText\":\"支付配置缺失时应该进入失败态。\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();
        assertThat(orderNo).startsWith("ORD");

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PAY-1001"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.paidAt").isEmpty());
    }

    private long registerUser(String role, String email, String password, String displayName) throws Exception {
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

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("userId").asLong();
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"email\":\"" + email + "\"," +
                                "\"password\":\"" + password + "\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
