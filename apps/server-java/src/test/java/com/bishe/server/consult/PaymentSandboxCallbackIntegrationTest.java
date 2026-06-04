package com.bishe.server.consult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付宝沙箱回调验签、签名下单与幂等测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentSandboxCallbackIntegrationTest {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final String PUBLIC_KEY = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    private static final String PRIVATE_KEY = Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bishe_sandbox;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("payment.mode", () -> "SANDBOX");
        registry.add("payment.sandbox.gateway-url", () -> "https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        registry.add("payment.sandbox.app-id", () -> "2021000118630011");
        registry.add("payment.sandbox.app-private-key", () -> PRIVATE_KEY);
        registry.add("payment.sandbox.notify-url", () -> "https://sandbox.example.com/api/v1/pay/alipay/callback");
        registry.add("payment.sandbox.return-url", () -> "http://127.0.0.1:5173/consult/orders/{orderNo}");
        registry.add("payment.sandbox.verify-enabled", () -> "true");
        registry.add("payment.sandbox.alipay-public-key", () -> PUBLIC_KEY);
        registry.add("payment.sandbox.sign-type", () -> "RSA2");
    }

    @Test
    void sandboxCallback_shouldVerifySignatureAndRemainIdempotent() throws Exception {
        long mentorUserId = registerUser("MENTOR", "sandbox-mentor@example.com", "Passw0rd!", "SandboxMentor");
        registerUser("STUDENT", "sandbox-student@example.com", "Passw0rd!", "SandboxStudent");
        String mentorToken = loginAndGetAccessToken("sandbox-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("sandbox-student@example.com", "Passw0rd!");

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"mentorUserId\":" + mentorUserId + "," +
                                "\"questionText\":\"想用真实回调字段联调支付。\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        MvcResult createPaymentResult = mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"))
                .andExpect(jsonPath("$.data.status").value("PAYING"))
                .andReturn();
        String paymentUrl = objectMapper.readTree(createPaymentResult.getResponse().getContentAsString()).path("data").path("paymentUrl").asText();
        assertThat(paymentUrl).startsWith("https://openapi-sandbox.dl.alipaydev.com/gateway.do?");
        Map<String, String> signedQuery = parseQuery(paymentUrl);
        assertThat(signedQuery.get("app_id")).isEqualTo("2021000118630011");
        assertThat(signedQuery.get("notify_url")).isEqualTo("https://sandbox.example.com/api/v1/pay/alipay/callback");
        assertThat(signedQuery.get("return_url")).isEqualTo("http://127.0.0.1:5173/consult/orders/" + orderNo);
        assertThat(AlipaySignatureVerifier.verifyRequest(signedQuery, PUBLIC_KEY, "RSA2")).isTrue();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"))
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        String callbackTradeNo = "ALI-SANDBOX-" + System.nanoTime();
        Map<String, String> callbackForm = new LinkedHashMap<>();
        callbackForm.put("out_trade_no", orderNo);
        callbackForm.put("trade_no", callbackTradeNo);
        callbackForm.put("total_amount", "50.00");
        callbackForm.put("trade_status", "TRADE_SUCCESS");
        callbackForm.put("charset", "utf-8");
        callbackForm.put("sign_type", "RSA2");
        callbackForm.put("sign", sign(callbackForm));

        mockMvc.perform(post("/api/v1/pay/alipay/callback")
                        .params(TestParams.fromMap(callbackForm)))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        mockMvc.perform(post("/api/v1/pay/alipay/callback")
                        .params(TestParams.fromMap(callbackForm)))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        Integer successRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_records WHERE idempotency_key = ?",
                Integer.class,
                callbackTradeNo
        );
        assertThat(successRecordCount).isEqualTo(1);
    }

    @Test
    void sandboxCallback_shouldRejectInvalidSignature() throws Exception {
        long mentorUserId = registerUser("MENTOR", "sandbox-mentor-2@example.com", "Passw0rd!", "SandboxMentor2");
        registerUser("STUDENT", "sandbox-student-2@example.com", "Passw0rd!", "SandboxStudent2");
        String studentToken = loginAndGetAccessToken("sandbox-student-2@example.com", "Passw0rd!");

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"mentorUserId\":" + mentorUserId + "," +
                                "\"questionText\":\"这次回调签名应该失败。\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"));

        mockMvc.perform(post("/api/v1/pay/alipay/callback")
                        .param("out_trade_no", orderNo)
                        .param("trade_no", "ALI-SANDBOX-FAIL")
                        .param("total_amount", "50.00")
                        .param("trade_status", "TRADE_SUCCESS")
                        .param("sign_type", "RSA2")
                        .param("sign", "invalid-sign"))
                .andExpect(status().isOk())
                .andExpect(content().string("failure"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));
    }

    private String sign(Map<String, String> form) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(KEY_PAIR.getPrivate());
        signature.update(AlipaySignatureVerifier.buildSignContent(form).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private Map<String, String> parseQuery(String paymentUrl) {
        String query = paymentUrl.substring(paymentUrl.indexOf('?') + 1);
        return java.util.Arrays.stream(query.split("&"))
                .map(item -> item.split("=", 2))
                .collect(Collectors.toMap(
                        item -> URLDecoder.decode(item[0], StandardCharsets.UTF_8),
                        item -> item.length > 1 ? URLDecoder.decode(item[1], StandardCharsets.UTF_8) : "",
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = jsonNode.path("data").path("userId").asLong();
        if ("MENTOR".equals(role)) {
            jdbcTemplate.update(
                    "UPDATE mentor_profiles SET approval_status = 'APPROVED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    userId
            );
        }
        return userId;
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
