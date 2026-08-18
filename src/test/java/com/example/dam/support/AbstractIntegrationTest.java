package com.example.dam.support;


import com.example.dam.asset.domain.AssetType;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for integration tests: full Spring context, PostgreSQL + pgvector via Testcontainers, the offline
 * demo model behind a scriptable test double, and small JSON/HTTP helpers. All subclasses share one context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ScriptedChatModelConfiguration.class})
public abstract class AbstractIntegrationTest {

    protected static final String USER = "it.user";
    protected static final String REVIEWER = "it.reviewer";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ScriptedChatModel chatModel;
    @Autowired
    protected JsonMapper json;

    @AfterEach
    void resetChatModel() {
        chatModel.reset();
    }

    protected static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    protected UUID registerAsset(String externalId, AssetType type, String filename, Map<String, Object> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalAssetId", externalId);
        body.put("filename", filename);
        body.put("mimeType", "image/jpeg");
        body.put("assetUrl", "https://dam.example.com/assets/" + externalId + "/" + filename);
        body.put("type", type.name());
        body.put("metadata", metadata);
        return UUID.fromString(post("/api/assets", body, USER, 201).get("id").asString());
    }

    protected JsonNode get(String url) {
        return perform(MockMvcRequestBuilders.get(url), 200);
    }

    protected JsonNode get(String url, int expectedStatus) {
        return perform(MockMvcRequestBuilders.get(url), expectedStatus);
    }

    protected JsonNode post(String url, Object body, String user, int expectedStatus) {
        return perform(withUser(MockMvcRequestBuilders.post(url), user).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json.writeValueAsString(body)), expectedStatus);
    }

    protected JsonNode put(String url, Object body, String user, int expectedStatus) {
        return perform(withUser(MockMvcRequestBuilders.put(url), user).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), expectedStatus);
    }

    protected JsonNode delete(String url, String user, int expectedStatus) {
        return perform(withUser(MockMvcRequestBuilders.delete(url), user), expectedStatus);
    }

    protected JsonNode metadataValue(UUID assetId, String field) {
        for (JsonNode entry : get("/api/assets/" + assetId + "/metadata").get("metadata").values()) {
            if (entry.get("field").asString().equals(field)) {
                return entry;
            }
        }
        return null;
    }

    /** Polls a tracked workflow run until it is COMPLETED or FAILED. */
    protected JsonNode awaitWorkflow(String workflowId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            JsonNode run = get("/api/workflows/" + workflowId);
            String status = run.get("status").asString();
            if (status.equals("COMPLETED") || status.equals("FAILED")) {
                return run;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Workflow " + workflowId + " did not finish in time");
    }

    /** Starts background enrichment (Prefer: respond-async) and returns the 202 response body. */
    protected JsonNode startEnrichment(UUID assetId) {
        return perform(withUser(MockMvcRequestBuilders.post("/api/assets/" + assetId + "/enrich"), USER)
                .header("Prefer", "respond-async"), 202);
    }

        private JsonNode perform(MockHttpServletRequestBuilder request, int expectedStatus) {
        try {
            MvcResult result = mvc.perform(request).andReturn();
            String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (result.getResponse().getStatus() != expectedStatus) {
                throw new AssertionError("Expected HTTP %d but got %d: %s".formatted(expectedStatus,
                        result.getResponse().getStatus(), content));
            }
            return content.isBlank() ? json.createObjectNode() : json.readTree(content);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request, String user) {
        return user == null ? request : request.header("X-User-Id", user);
    }
}
