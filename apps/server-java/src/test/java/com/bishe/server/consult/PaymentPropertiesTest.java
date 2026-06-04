package com.bishe.server.consult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveAppPrivateKeySupportsEscapedPemInline() {
        PaymentProperties.Sandbox sandbox = new PaymentProperties.Sandbox();
        sandbox.setAppPrivateKey("-----BEGIN PRIVATE KEY-----\\nabc123\\n-----END PRIVATE KEY-----");

        assertEquals(
                "-----BEGIN PRIVATE KEY-----\nabc123\n-----END PRIVATE KEY-----",
                sandbox.resolveAppPrivateKey()
        );
    }

    @Test
    void resolveAlipayPublicKeySupportsBase64Content() {
        PaymentProperties.Sandbox sandbox = new PaymentProperties.Sandbox();
        String pem = "-----BEGIN PUBLIC KEY-----\nabc123\n-----END PUBLIC KEY-----";
        sandbox.setAlipayPublicKeyBase64(Base64.getEncoder().encodeToString(pem.getBytes()));

        assertEquals(pem, sandbox.resolveAlipayPublicKey());
    }

    @Test
    void resolveAppPrivateKeyFallsBackToFile() throws IOException {
        Path keyFile = tempDir.resolve("app-private-key.pem");
        String pem = "-----BEGIN PRIVATE KEY-----\nfile-value\n-----END PRIVATE KEY-----";
        Files.writeString(keyFile, pem);

        PaymentProperties.Sandbox sandbox = new PaymentProperties.Sandbox();
        sandbox.setAppPrivateKeyPath(keyFile.toString());

        assertEquals(pem, sandbox.resolveAppPrivateKey());
    }
}
