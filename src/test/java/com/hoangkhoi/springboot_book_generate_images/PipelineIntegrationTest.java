package com.hoangkhoi.springboot_book_generate_images;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.service.FakeGeminiClient;
import com.hoangkhoi.springboot_book_generate_images.service.GeminiClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application, end to end: real HTTP routes, real service, real files on disk. Only
 * Gemini is faked, so a full five-step run costs nothing.
 *
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineIntegrationTest {

    private static final String BOOK = """
            I.
            THE RIVER BANK

            The Mole had been working very hard all the morning, spring-cleaning his little home.""";

    /**
     * A plain path rather than {@code @TempDir}: the property supplier below is read during context
     * refresh, which does not reliably follow JUnit's temp-directory injection. Lives under
     * target/, so {@code mvn clean} disposes of it.
     */
    static final Path dataDir =
            Path.of("target", "integration-data", UUID.randomUUID().toString());

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", dataDir::toString);
    }

    @TestConfiguration
    static class FakeGemini {

        @Bean
        @Primary
        GeminiClient fakeGeminiClient() {
            return new FakeGeminiClient();
        }
    }

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String userId;
    private String projectId;

    @Test
    @Order(1)
    void aUserSignsInCreatesAProjectAndRunsAllFiveSteps() throws Exception {
        userId = signIn();
        projectId = createProject();

        assertThat(detail().get("status").asText()).isEqualTo("CREATED");
        assertThat(detail().get("currentStep").asText()).isEqualTo("STYLE");

        runToCompletion(Step.STYLE, "Warm hand-painted watercolour");
        assertThat(detail().get("status").asText()).isEqualTo("STYLE_SET");
        assertThat(detail().get("style").asText()).isEqualTo("Warm hand-painted watercolour");

        runToCompletion(Step.CHARACTERS, null);
        assertThat(detail().get("characters")).hasSize(2);

        runToCompletion(Step.PORTRAITS, null);
        JsonNode characters = detail().get("characters");
        for (JsonNode character : characters) {
            assertThat(character.get("imageState").asText()).isEqualTo("DONE");
            assertThat(character.get("imageFile").asText()).isNotBlank();
        }

        runToCompletion(Step.CHAPTERS, null);
        assertThat(detail().get("chapters")).hasSize(1);

        runToCompletion(Step.ILLUSTRATIONS, null);
        JsonNode done = detail();
        assertThat(done.get("status").asText()).isEqualTo("DONE");
        assertThat(done.get("completedSteps").asInt()).isEqualTo(5);
        assertThat(done.get("currentStep").isNull()).isTrue();
        assertThat(done.get("chapters").get(0).get("imageState").asText()).isEqualTo("DONE");
    }

    @Test
    @Order(2)
    void everyGeneratedImageIsServedBackOverTheApi() throws Exception {
        JsonNode project = detail();

        for (JsonNode item : concat(project.get("characters"), project.get("chapters"))) {
            String file = item.get("imageFile").asText();
            byte[] bytes = mvc.perform(get(
                            "/api/projects/{id}/images/{file}", projectId, file)
                            .header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(bytes).isEqualTo(FakeGeminiClient.PNG);
        }
    }

    @Test
    @Order(3)
    void theBookTextIsStillReadableInFullAtTheEndOfThePipeline() throws Exception {
        String body = mvc.perform(get("/api/projects/{id}/book", projectId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("data").asText()).isEqualTo(BOOK);
    }

    /** Every response carries the same envelope, so a client never has to guess at the shape. */
    @Test
    @Order(6)
    void everyJsonResponseIsWrappedInTheStandardEnvelope() throws Exception {
        String body = mvc.perform(get("/api/projects").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("data").isArray()).isTrue();
    }

    @Test
    @Order(4)
    void afterEverythingIsDoneFurtherRunsAreRefused() throws Exception {
        mvc.perform(post("/api/projects/{id}/steps/ILLUSTRATIONS/run", projectId)
                        .header("X-User-Id", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("PIPELINE_COMPLETE"));
    }

    /** What a reviewer would see reopening the project later: state, results and files intact. */
    @Test
    @Order(5)
    void theFinishedProjectSurvivesOnDisk() throws Exception {
        Path projectDir = dataDir.resolve("users").resolve(userId)
                .resolve("projects").resolve(projectId);

        assertThat(projectDir.resolve("project.json")).exists();
        assertThat(projectDir.resolve("book.txt")).hasContent(BOOK);
        try (var images = Files.list(projectDir.resolve("images"))) {
            assertThat(images).hasSize(3);
        }
        String stored = Files.readString(projectDir.resolve("project.json"));
        assertThat(stored).contains("\"status\" : \"DONE\"").doesNotContain("THE RIVER BANK");
    }

    private String signIn() throws Exception {
        String body = mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test A Name","email":"testa@gmail.com"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("id").asText();
    }

    private String createProject() throws Exception {
        String body = mvc.perform(post("/api/projects")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                java.util.Map.of("title", "Hello A", "bookText", BOOK))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("id").asText();
    }

    /** Starts a step, then polls exactly as the browser will until it stops running. */
    private void runToCompletion(Step step, String style) throws Exception {
        String body = style == null ? "{}" : json.writeValueAsString(java.util.Map.of("style", style));
        mvc.perform(post("/api/projects/{id}/steps/{step}/run", projectId, step)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        for (int attempt = 0; attempt < 200; attempt++) {
            String state = detail().get("stepState").asText();
            if (!"RUNNING".equals(state)) {
                assertThat(state).isEqualTo("IDLE");
                return;
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        throw new AssertionError(step + " never finished");
    }

    private JsonNode detail() throws Exception {
        String body = mvc.perform(get("/api/projects/{id}", projectId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private static Iterable<JsonNode> concat(JsonNode first, JsonNode second) {
        var all = new java.util.ArrayList<JsonNode>();
        first.forEach(all::add);
        second.forEach(all::add);
        return all;
    }
}
