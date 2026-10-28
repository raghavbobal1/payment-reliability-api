package dev.raghav.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises real HTTP requests against the running Spring Boot server.
 * Each test gets fresh demo balances and idempotency records.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransferHttpIntegrationTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired private Environment environment;
    @Autowired private JsonMapper mapper;

    private String url(String path) {
        return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
    }

    private HttpResponse<String> post(String key, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url("/api/transfers")))
                .header("Content-Type", "application/json");
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private String transfer(String from, String to, String amount) {
        return "{\"fromAccount\":\"" + from + "\",\"toAccount\":\"" + to
                + "\",\"amount\":" + amount + "}";
    }

    private void assertBalance(String account, String expected) throws Exception {
        HttpResponse<String> response = get("/api/accounts");
        assertEquals(200, response.statusCode());
        for (JsonNode item : json(response)) {
            if (account.equals(item.path("accountId").asText())) {
                assertEquals(0, new BigDecimal(expected).compareTo(
                        new BigDecimal(item.path("balance").asText())), account);
                return;
            }
        }
        throw new AssertionError("Account missing from HTTP response: " + account);
    }

    @Test
    void successfulTransferReturns201AndCanBeFetched() throws Exception {
        HttpResponse<String> response = post("http-first-0001",
                transfer("ACC-1001", "ACC-1002", "25.00"));

        assertEquals(201, response.statusCode());
        assertEquals("false", response.headers().firstValue("Idempotent-Replayed").orElseThrow());
        JsonNode receipt = json(response);
        assertFalse(receipt.path("replayed").asBoolean());
        assertEquals("ACC-1001", receipt.path("fromAccount").asText());
        assertEquals("ACC-1002", receipt.path("toAccount").asText());
        assertEquals(0, new BigDecimal("25.00").compareTo(
                new BigDecimal(receipt.path("amount").asText())));
        String id = receipt.path("id").asText();
        assertFalse(id.isBlank());

        HttpResponse<String> lookup = get("/api/transfers/" + id);
        assertEquals(200, lookup.statusCode());
        assertEquals(id, json(lookup).path("id").asText());
        assertBalance("ACC-1001", "4975.00");
        assertBalance("ACC-1002", "1525.00");
    }

    @Test
    void retryReturnsOriginalReceiptWithoutAnotherDebit() throws Exception {
        String payload = transfer("ACC-1001", "ACC-1002", "40.00");
        HttpResponse<String> first = post("http-retry-0002", payload);
        HttpResponse<String> retry = post("http-retry-0002", payload);

        assertEquals(201, first.statusCode());
        assertEquals(200, retry.statusCode());
        assertEquals("true", retry.headers().firstValue("Idempotent-Replayed").orElseThrow());
        assertEquals(json(first).path("id").asText(), json(retry).path("id").asText());
        assertEquals(json(first).path("createdAt").asText(), json(retry).path("createdAt").asText());
        assertTrue(json(retry).path("replayed").asBoolean());
        assertBalance("ACC-1001", "4960.00");
        assertBalance("ACC-1002", "1540.00");
    }

    @Test
    void changedPayloadWithSameKeyReturns409() throws Exception {
        assertEquals(201, post("http-conflict-0003",
                transfer("ACC-1001", "ACC-1002", "20.00")).statusCode());

        HttpResponse<String> conflict = post("http-conflict-0003",
                transfer("ACC-1001", "ACC-1002", "30.00"));
        assertEquals(409, conflict.statusCode());
        assertEquals("CONFLICT", json(conflict).path("code").asText());
        assertBalance("ACC-1001", "4980.00");
        assertBalance("ACC-1002", "1520.00");
    }

    @Test
    void unknownAccountReturns404WithoutMovingFunds() throws Exception {
        HttpResponse<String> response = post("http-unknown-0004",
                transfer("ACC-9999", "ACC-1002", "10.00"));
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", json(response).path("code").asText());
        assertBalance("ACC-1001", "5000.00");
        assertBalance("ACC-1002", "1500.00");
    }

    @Test
    void insufficientFundsReturns422WithoutMovingFunds() throws Exception {
        HttpResponse<String> response = post("http-overdraw-0005",
                transfer("ACC-1001", "ACC-1002", "9000.00"));
        assertEquals(422, response.statusCode());
        assertEquals("INSUFFICIENT_FUNDS", json(response).path("code").asText());
        assertBalance("ACC-1001", "5000.00");
        assertBalance("ACC-1002", "1500.00");
    }

    @Test
    void invalidAmountAndMissingKeyReturn400() throws Exception {
        HttpResponse<String> invalid = post("http-invalid-0006",
                transfer("ACC-1001", "ACC-1002", "1.234"));
        assertEquals(400, invalid.statusCode());
        assertEquals("INVALID_INPUT", json(invalid).path("code").asText());

        HttpResponse<String> missingKey = post(null,
                transfer("ACC-1001", "ACC-1002", "5.00"));
        assertEquals(400, missingKey.statusCode());
        assertEquals("INVALID_INPUT", json(missingKey).path("code").asText());
        assertBalance("ACC-1001", "5000.00");
    }

    @Test
    void missingTransferReturns404() throws Exception {
        HttpResponse<String> response = get("/api/transfers/not-a-transfer");
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", json(response).path("code").asText());
    }
}
