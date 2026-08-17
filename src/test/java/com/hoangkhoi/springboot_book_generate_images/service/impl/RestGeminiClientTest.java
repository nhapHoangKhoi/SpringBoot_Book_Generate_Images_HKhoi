package com.hoangkhoi.springboot_book_generate_images.service.impl;

import com.hoangkhoi.springboot_book_generate_images.config.GeminiProperties;
import com.hoangkhoi.springboot_book_generate_images.exception.GeminiException;
import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * What goes on the wire and what comes back off it.
 *
 * <p>No network: {@link MockRestServiceServer} answers, so these run in CI and cost nothing. They
 * cannot prove Google's field names are right — only a live call can, and
 * {@code LiveGeminiVerificationTest} does that on demand — but they do pin the two things this
 * client is responsible for: sending the book once, and reading a reply robustly.
 */
class RestGeminiClientTest {

    private static final String KEY = "test-key";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private MockRestServiceServer server;
    private RestGeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(KEY);
        properties.setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        properties.setTextModel("test-text-model");
        properties.setImageModel("test-image-model");
        client = new RestGeminiClient(builder, properties);
    }

    private void respondWith(String json) {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Nested
    class SendingTheBook {

        /**
         * §4.3: the book is uploaded once through the File API and the conversation refers to it
         * by URI, so no later step carries the text.
         */
        @Test
        void theBookIsUploadedThenReferencedByUri() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/upload/v1beta/files"
                                    + "?uploadType=media"))
                    .andExpect(header("x-goog-api-key", KEY))
                    .andRespond(withSuccess(
                            "{\"file\":{\"uri\":\"https://files.example/abc\"}}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andExpect(jsonPath("$.model").value("test-text-model"))
                    .andExpect(jsonPath("$.input[1].type").value("document"))
                    .andExpect(jsonPath("$.input[1].uri").value("https://files.example/abc"))
                    .andRespond(withSuccess("""
                            {"id":"int-123","output_text":"ok"}""", MediaType.APPLICATION_JSON));

            assertThat(client.openContext("I.\nTHE RIVER BANK\n\nThe Mole worked hard."))
                    .isEqualTo("int-123");
            server.verify();
        }

        @Test
        void anUploadThatReturnsNoUriIsAClearFailure() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/upload/v1beta/files"
                                    + "?uploadType=media"))
                    .andRespond(withSuccess("{\"file\":{}}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.openContext("book"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("no file URI");
        }

        /** Every later step refers to that conversation instead of resending the text. */
        @Test
        void laterStepsReferToTheConversationRatherThanResendingTheBook() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andExpect(jsonPath("$.previous_interaction_id").value("int-123"))
                    .andRespond(withSuccess("""
                            {"id":"int-124","output_text":"Warm watercolour with ink outlines"}""",
                            MediaType.APPLICATION_JSON));

            assertThat(client.generateStyle("int-123")).contains("watercolour");
            server.verify();
        }

        @Test
        void aMissingConversationIdIsReportedRatherThanStoredAsNull() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/upload/v1beta/files"
                                    + "?uploadType=media"))
                    .andRespond(withSuccess(
                            "{\"file\":{\"uri\":\"https://files.example/abc\"}}",
                            MediaType.APPLICATION_JSON));
            respondWith("""
                    {"output_text":"fine"}""");

            assertThatThrownBy(() -> client.openContext("book"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("no conversation id");
        }
    }

    @Nested
    class ReadingReplies {

        /** Google documents the answer both flat and nested; either must work. */
        @Test
        void textIsReadFromTheFlatField() {
            respondWith("""
                    {"id":"i1","output_text":"Bold woodcut prints"}""");

            assertThat(client.generateStyle("int-1")).isEqualTo("Bold woodcut prints");
        }

        @Test
        void textIsReadWhenItIsNestedInsideSteps() {
            respondWith("""
                    {"id":"i1","execution_steps":[{"model_output":{"text":"Bold woodcut prints"}}]}""");

            assertThat(client.generateStyle("int-1")).isEqualTo("Bold woodcut prints");
        }

        @Test
        void charactersAreParsedFromJson() {
            respondWith("{\"id\":\"i1\",\"output_text\":\"[{\\\"name\\\":\\\"The Mole\\\","
                    + "\\\"prompt\\\":\\\"a shy mole\\\"},{\\\"name\\\":\\\"The Rat\\\","
                    + "\\\"prompt\\\":\\\"a river rat\\\"}]\"}");

            List<GeneratedItem> characters = client.generateCharacters("int-1", "watercolour");

            assertThat(characters).extracting(GeneratedItem::name)
                    .containsExactly("The Mole", "The Rat");
            assertThat(characters.get(0).prompt()).isEqualTo("a shy mole");
        }

        /** Models wrap JSON in code fences whatever the response format says. */
        @Test
        void charactersAreParsedEvenInsideCodeFences() {
            respondWith("""
                    {"id":"i1","output_text":"```json\\n[{\\"name\\":\\"The Mole\\",\\"prompt\\":\\"a shy mole\\"}]\\n```"}""");

            assertThat(client.generateCharacters("int-1", "watercolour"))
                    .extracting(GeneratedItem::name).containsExactly("The Mole");
        }

        @Test
        void anUnusableCharacterReplyIsAClearFailure() {
            respondWith("""
                    {"id":"i1","output_text":"Sorry, I can't help with that."}""");

            assertThatThrownBy(() -> client.generateCharacters("int-1", "watercolour"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("valid JSON");
        }

        @Test
        void chapterPromptsCarryTheCharacterNamesIntoTheRequest() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andExpect(jsonPath("$.input").value(
                            org.hamcrest.Matchers.containsString("The Mole, The Rat")))
                    .andRespond(withSuccess("""
                            {"id":"i1","output_text":"[{\\"name\\":\\"The River Bank\\",\\"prompt\\":\\"a sunlit bank\\"}]"}""",
                            MediaType.APPLICATION_JSON));

            assertThat(client.generateChapters("int-1", "watercolour",
                    List.of("The Mole", "The Rat"))).hasSize(1);
            server.verify();
        }
    }

    @Nested
    class Images {

        /** The notebook reads images out of steps[].content[]; the flat form must work too. */
        @Test
        void imageBytesAreDecodedWhereverTheySit() {
            respondWith("""
                    {"id":"i1","steps":[{"content":[{"type":"image","mime_type":"image/png","data":"%s"}]}]}"""
                    .formatted(Base64.getEncoder().encodeToString(PNG)));

            assertThat(client.generateImage("int-1", "a mole").image()).isEqualTo(PNG);
        }

        /**
         * Consistency across pictures comes from continuing one conversation on the image model —
         * not from re-uploading the earlier images.
         */
        @Test
        void everyPictureContinuesTheImageConversationAndAdvancesIt() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andExpect(jsonPath("$.model").value("test-image-model"))
                    .andExpect(jsonPath("$.previous_interaction_id").value("img-1"))
                    .andRespond(withSuccess("""
                            {"id":"img-2","output_image":{"mime_type":"image/png","data":"%s"}}"""
                            .formatted(Base64.getEncoder().encodeToString(PNG)),
                            MediaType.APPLICATION_JSON));

            ImageTurn turn = client.generateImage("img-1", "a riverbank");

            assertThat(turn.interactionId()).isEqualTo("img-2");
            server.verify();
        }

        @Test
        void theImageConversationIsOpenedWithTheStyleAndTheNoTextRule() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andExpect(jsonPath("$.model").value("test-image-model"))
                    .andExpect(jsonPath("$.input").value(
                            org.hamcrest.Matchers.containsString("watercolour")))
                    .andExpect(jsonPath("$.input").value(
                            org.hamcrest.Matchers.containsString("no text on the image")))
                    .andRespond(withSuccess("{\"id\":\"img-root\"}", MediaType.APPLICATION_JSON));

            assertThat(client.openImageContext("watercolour")).isEqualTo("img-root");
            server.verify();
        }

        @Test
        void aRefusedImageIsAReadableFailureNotAnEmptyFile() {
            respondWith("""
                    {"id":"i1","output_text":"I can't generate that image."}""");

            assertThatThrownBy(() -> client.generateImage("img-1", "p"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("no image");
        }

        /** Free-tier image quota is the failure this project will hit most; it must read clearly. */
        @Test
        void anExhaustedQuotaSaysSoInWordsTheUserCanActOn() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .body("{\"error\":{\"message\":\"quota exceeded\"}}"));

            assertThatThrownBy(() -> client.generateImage("img-1", "a mole"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("quota exhausted");
        }
    }

    @Nested
    class Failures {

        @Test
        void aServerErrorBecomesAReadableStepError() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andRespond(withServerError().body("upstream exploded"));

            assertThatThrownBy(() -> client.generateStyle("int-1"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("500");
        }

        /** An aged-out conversation is the one failure whose fix is to send the book again. */
        @Test
        void anExpiredConversationIsFlaggedSoTheBookCanBeResent() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .body("{\"error\":{\"message\":\"previous_interaction_id not found\"}}"));

            assertThatThrownBy(() -> client.generateStyle("int-1"))
                    .isInstanceOf(GeminiException.class)
                    .satisfies(e -> assertThat(((GeminiException) e).isContextExpired()).isTrue());
        }

        @Test
        void anOrdinaryFailureIsNotMistakenForAnExpiredConversation() {
            server.expect(requestTo(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("rate limited"));

            assertThatThrownBy(() -> client.generateStyle("int-1"))
                    .isInstanceOf(GeminiException.class)
                    .satisfies(e -> assertThat(((GeminiException) e).isContextExpired()).isFalse());
        }

        @Test
        void withoutAnApiKeyNothingIsSentAtAll() {
            GeminiProperties noKey = new GeminiProperties();
            noKey.setApiKey("");
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer strict = MockRestServiceServer.bindTo(builder).build();

            assertThatThrownBy(() -> new RestGeminiClient(builder, noKey).generateStyle("i"))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("GEMINI_API_KEY");
            strict.verify();
        }
    }
}
