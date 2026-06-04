package com.bishe.server.consult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付宝沙箱交易查询 / 退款 / 退款查询 / 关单集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsultPaymentGatewayIntegrationTest {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final String PRIVATE_KEY = Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
    private static final HttpServer GATEWAY_SERVER = startGatewayServer();

    private static final AtomicReference<String> LAST_METHOD = new AtomicReference<>("");
    private static final AtomicReference<String> LAST_BIZ_CONTENT = new AtomicReference<>("{}");
    private static final AtomicReference<String> QUERY_TRADE_STATUS = new AtomicReference<>("TRADE_SUCCESS");
    private static final AtomicReference<String> QUERY_TRADE_NO = new AtomicReference<>("ALI-QUERY-001");
    private static final AtomicReference<String> CLOSE_TRADE_NO = new AtomicReference<>("ALI-CLOSE-001");
    private static final AtomicReference<String> REFUND_TRADE_NO = new AtomicReference<>("ALI-REFUND-001");
    private static final AtomicReference<String> REFUND_STATUS = new AtomicReference<>("REFUND_SUCCESS");
    private static final AtomicInteger CLOSE_CALL_COUNT = new AtomicInteger();
    private static final AtomicInteger REFUND_CALL_COUNT = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bishe_payment_ops;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("auth.admin.email", () -> "admin@bishe.local");
        registry.add("auth.admin.password", () -> "Passw0rd!");
        registry.add("payment.mode", () -> "SANDBOX");
        registry.add("payment.sandbox.gateway-url", () -> "http://127.0.0.1:" + GATEWAY_SERVER.getAddress().getPort() + "/gateway");
        registry.add("payment.sandbox.app-id", () -> "2021000118630011");
        registry.add("payment.sandbox.app-private-key", () -> PRIVATE_KEY);
        registry.add("payment.sandbox.notify-url", () -> "https://sandbox.example.com/api/v1/pay/alipay/callback");
        registry.add("payment.sandbox.return-url", () -> "http://127.0.0.1:5173/consult/orders/{orderNo}");
        registry.add("payment.sandbox.verify-enabled", () -> "false");
        registry.add("payment.sandbox.sign-type", () -> "RSA2");
    }

    @BeforeEach
    void resetGatewayScenario() {
        LAST_METHOD.set("");
        LAST_BIZ_CONTENT.set("{}");
        QUERY_TRADE_STATUS.set("TRADE_SUCCESS");
        QUERY_TRADE_NO.set("ALI-QUERY-001");
        CLOSE_TRADE_NO.set("ALI-CLOSE-001");
        REFUND_TRADE_NO.set("ALI-REFUND-001");
        REFUND_STATUS.set("REFUND_SUCCESS");
        CLOSE_CALL_COUNT.set(0);
        REFUND_CALL_COUNT.set(0);
    }

    @AfterAll
    static void shutdownServer() {
        GATEWAY_SERVER.stop(0);
    }

    @Test
    void adminShouldQuerySandboxTradeAndSyncOrderPaid() throws Exception {
        long mentorUserId = registerUser("MENTOR", "payment-query-mentor@example.com", "Passw0rd!", "PaymentQueryMentor");
        registerUser("STUDENT", "payment-query-student@example.com", "Passw0rd!", "PaymentQueryStudent");
        String studentToken = loginAndGetAccessToken("payment-query-student@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@bishe.local", "Passw0rd!");
        String providerTradeNo = "ALI-QUERY-" + System.nanoTime();
        QUERY_TRADE_NO.set(providerTradeNo);
        String orderNo = createSandboxOrderAndPayment(studentToken, mentorUserId, "请帮我测试交易查询补单。");

        mockMvc.perform(post("/api/v1/admin/consult/orders/{orderNo}/payment/query", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"))
                .andExpect(jsonPath("$.data.tradeStatus").value("TRADE_SUCCESS"))
                .andExpect(jsonPath("$.data.syncedToPaid").value(true))
                .andExpect(jsonPath("$.data.localStatus").value("PAID"));

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.payment.providerTradeNo").value(providerTradeNo));

        assertThat(LAST_METHOD.get()).isEqualTo("alipay.trade.query");
        JsonNode bizContent = objectMapper.readTree(LAST_BIZ_CONTENT.get());
        assertThat(bizContent.path("out_trade_no").asText()).isEqualTo(orderNo);
    }

    @Test
    void adminShouldCloseSandboxTradeAndCancelOrder() throws Exception {
        long mentorUserId = registerUser("MENTOR", "payment-close-mentor@example.com", "Passw0rd!", "PaymentCloseMentor");
        registerUser("STUDENT", "payment-close-student@example.com", "Passw0rd!", "PaymentCloseStudent");
        String studentToken = loginAndGetAccessToken("payment-close-student@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@bishe.local", "Passw0rd!");
        String orderNo = createSandboxOrderAndPayment(studentToken, mentorUserId, "请帮我测试交易关闭。");

        mockMvc.perform(post("/api/v1/admin/consult/orders/{orderNo}/payment/close", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.closed").value(true))
                .andExpect(jsonPath("$.data.localStatus").value("CANCELED"));

        mockMvc.perform(post("/api/v1/admin/consult/orders/{orderNo}/payment/close", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.closed").value(true))
                .andExpect(jsonPath("$.data.localStatus").value("CANCELED"));

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.payment.status").value("CLOSED"));

        assertThat(LAST_METHOD.get()).isEqualTo("alipay.trade.close");
        assertThat(CLOSE_CALL_COUNT.get()).isEqualTo(1);
    }

    @Test
    void studentShouldQuerySandboxTradeAndSyncOwnOrderPaid() throws Exception {
        long mentorUserId = registerUser("MENTOR", "student-payment-query-mentor@example.com", "Passw0rd!", "StudentPaymentQueryMentor");
        registerUser("STUDENT", "student-payment-query@example.com", "Passw0rd!", "StudentPaymentQuery");
        String studentToken = loginAndGetAccessToken("student-payment-query@example.com", "Passw0rd!");
        QUERY_TRADE_NO.set("ALI-QUERY-" + System.nanoTime());
        String orderNo = createSandboxOrderAndPayment(studentToken, mentorUserId, "学生想从订单详情查询支付状态。");

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/payment/query", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"))
                .andExpect(jsonPath("$.data.tradeStatus").value("TRADE_SUCCESS"))
                .andExpect(jsonPath("$.data.syncedToPaid").value(true))
                .andExpect(jsonPath("$.data.localStatus").value("PAID"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"));

        assertThat(LAST_METHOD.get()).isEqualTo("alipay.trade.query");
    }

    @Test
    void studentShouldCloseSandboxTradeAndCancelOwnOrder() throws Exception {
        long mentorUserId = registerUser("MENTOR", "student-payment-close-mentor@example.com", "Passw0rd!", "StudentPaymentCloseMentor");
        registerUser("STUDENT", "student-payment-close@example.com", "Passw0rd!", "StudentPaymentClose");
        String studentToken = loginAndGetAccessToken("student-payment-close@example.com", "Passw0rd!");
        String orderNo = createSandboxOrderAndPayment(studentToken, mentorUserId, "学生想在订单详情关闭支付单。");

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/payment/close", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.closed").value(true))
                .andExpect(jsonPath("$.data.localStatus").value("CANCELED"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"));

        assertThat(LAST_METHOD.get()).isEqualTo("alipay.trade.close");
    }

    @Test
    void adminRefundShouldCallSandboxRefundAndSupportRefundQuery() throws Exception {
        long mentorUserId = registerUser("MENTOR", "payment-refund-mentor@example.com", "Passw0rd!", "PaymentRefundMentor");
        registerUser("STUDENT", "payment-refund-student@example.com", "Passw0rd!", "PaymentRefundStudent");
        String studentToken = loginAndGetAccessToken("payment-refund-student@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@bishe.local", "Passw0rd!");
        String orderNo = createSandboxOrderAndPayment(studentToken, mentorUserId, "请帮我测试退款链路。");
        simulateSandboxCallback(orderNo, "ALI-REFUND-CALLBACK-" + System.nanoTime(), "50.00");

        mockMvc.perform(post("/api/v1/admin/consult/orders/{orderNo}/refund", orderNo)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"管理员按沙箱规则执行原路退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.externalRefundTriggered").value(true))
                .andExpect(jsonPath("$.data.externalRefundStatus").value("REFUND_SUCCESS"))
                .andExpect(jsonPath("$.data.externalRefundRequestNo").value("REFUND-" + orderNo));

        mockMvc.perform(post("/api/v1/admin/consult/orders/{orderNo}/refund", orderNo)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"管理员按沙箱规则执行原路退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.externalRefundTriggered").value(false))
                .andExpect(jsonPath("$.data.externalRefundStatus").doesNotExist())
                .andExpect(jsonPath("$.data.externalRefundRequestNo").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.payment.status").value("REFUND_SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}/payment/refund-query", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.refundRequestNo").value("REFUND-" + orderNo))
                .andExpect(jsonPath("$.data.refundStatus").value("REFUND_SUCCESS"))
                .andExpect(jsonPath("$.data.refundAmountFen").value(5000));

        assertThat(LAST_METHOD.get()).isEqualTo("alipay.trade.fastpay.refund.query");
        assertThat(REFUND_CALL_COUNT.get()).isEqualTo(1);
    }

    private String createSandboxOrderAndPayment(String studentToken, long mentorUserId, String questionText) throws Exception {
        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"mentorUserId\":" + mentorUserId + "," +
                                "\"questionText\":\"" + questionText + "\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("SANDBOX"))
                .andExpect(jsonPath("$.data.status").value("PAYING"));
        return orderNo;
    }

    private void simulateSandboxCallback(String orderNo, String tradeNo, String totalAmount) throws Exception {
        mockMvc.perform(post("/api/v1/pay/alipay/callback")
                        .param("out_trade_no", orderNo)
                        .param("trade_no", tradeNo)
                        .param("total_amount", totalAmount)
                        .param("trade_status", "TRADE_SUCCESS"))
                .andExpect(status().isOk());
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

    private static HttpServer startGatewayServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/gateway", AdminConsultPaymentGatewayIntegrationTest::handleGatewayRequest);
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void handleGatewayRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormBody(body);
        String method = params.getOrDefault("method", "");
        String bizContent = params.getOrDefault("biz_content", "{}");
        LAST_METHOD.set(method);
        LAST_BIZ_CONTENT.set(bizContent);
        String responseBody = buildGatewayResponse(method, bizContent);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String buildGatewayResponse(String method, String bizContentJson) throws IOException {
        JsonNode bizContent = new ObjectMapper().readTree(bizContentJson);
        String orderNo = bizContent.path("out_trade_no").asText("");
        String tradeNo = bizContent.path("trade_no").asText("");
        if ("alipay.trade.query".equals(method)) {
            String responseTradeNo = QUERY_TRADE_NO.get();
            return "{" +
                    "\"alipay_trade_query_response\":{" +
                    "\"code\":\"10000\"," +
                    "\"msg\":\"Success\"," +
                    "\"out_trade_no\":\"" + orderNo + "\"," +
                    "\"trade_no\":\"" + responseTradeNo + "\"," +
                    "\"trade_status\":\"" + QUERY_TRADE_STATUS.get() + "\"," +
                    "\"total_amount\":\"50.00\"}}";
        }
        if ("alipay.trade.close".equals(method)) {
            CLOSE_CALL_COUNT.incrementAndGet();
            String responseTradeNo = tradeNo.isBlank() ? CLOSE_TRADE_NO.get() : tradeNo;
            return "{" +
                    "\"alipay_trade_close_response\":{" +
                    "\"code\":\"10000\"," +
                    "\"msg\":\"Success\"," +
                    "\"out_trade_no\":\"" + orderNo + "\"," +
                    "\"trade_no\":\"" + responseTradeNo + "\"}}";
        }
        if ("alipay.trade.refund".equals(method)) {
            REFUND_CALL_COUNT.incrementAndGet();
            String responseTradeNo = tradeNo.isBlank() ? REFUND_TRADE_NO.get() : tradeNo;
            String refundAmount = bizContent.path("refund_amount").asText("50.00");
            String outRequestNo = bizContent.path("out_request_no").asText("");
            return "{" +
                    "\"alipay_trade_refund_response\":{" +
                    "\"code\":\"10000\"," +
                    "\"msg\":\"Success\"," +
                    "\"out_trade_no\":\"" + orderNo + "\"," +
                    "\"trade_no\":\"" + responseTradeNo + "\"," +
                    "\"out_request_no\":\"" + outRequestNo + "\"," +
                    "\"fund_change\":\"Y\"," +
                    "\"refund_fee\":\"" + refundAmount + "\"}}";
        }
        if ("alipay.trade.fastpay.refund.query".equals(method)) {
            String responseTradeNo = tradeNo.isBlank() ? REFUND_TRADE_NO.get() : tradeNo;
            String outRequestNo = bizContent.path("out_request_no").asText("");
            return "{" +
                    "\"alipay_trade_fastpay_refund_query_response\":{" +
                    "\"code\":\"10000\"," +
                    "\"msg\":\"Success\"," +
                    "\"out_trade_no\":\"" + orderNo + "\"," +
                    "\"trade_no\":\"" + responseTradeNo + "\"," +
                    "\"out_request_no\":\"" + outRequestNo + "\"," +
                    "\"refund_status\":\"" + REFUND_STATUS.get() + "\"," +
                    "\"refund_amount\":\"50.00\"}}";
        }
        return "{\"error_response\":{\"code\":\"40004\",\"msg\":\"method not mocked\"}}";
    }

    private static Map<String, String> parseFormBody(String formBody) {
        Map<String, String> map = new LinkedHashMap<>();
        if (formBody == null || formBody.isBlank()) {
            return map;
        }
        for (String pair : formBody.split("&")) {
            String[] items = pair.split("=", 2);
            String key = URLDecoder.decode(items[0], StandardCharsets.UTF_8);
            String value = items.length > 1 ? URLDecoder.decode(items[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
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

}
