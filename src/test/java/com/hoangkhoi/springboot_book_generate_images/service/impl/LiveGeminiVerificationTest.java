package com.hoangkhoi.springboot_book_generate_images.service.impl;

import com.hoangkhoi.springboot_book_generate_images.config.GeminiProperties;
import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;
import com.hoangkhoi.springboot_book_generate_images.service.GeminiClient;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms this client's assumptions against the real Gemini API. Skipped unless GEMINI_API_KEY is
 * set, so it never runs in a normal build or in CI.
 *
 * <pre>
 *   GEMINI_API_KEY=... ./mvnw test -Dtest=LiveGeminiVerificationTest
 * </pre>
 *
 * <p>Why it exists: Google's own pages describe the Interactions API in more than one shape, and no
 * mock can tell you which one is real. This is the cheapest possible check — four calls, one tiny
 * book, one small image — and it either proves the field names or fails with the exact message
 * needed to fix them.
 *
 * <p>It costs real quota. That is the point, and it is why it is opt-in.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveGeminiVerificationTest {

    private static final String BOOK = """
            THE RIVER BANK

            The Mole had been working very hard all the morning, spring-cleaning his little home.
            Then the Water Rat appeared with a picnic basket and a small blue boat, and the two
            of them spent the afternoon on the river.""";

    private static String interactionId;
    private static String style;
    private static List<GeneratedItem> characters;

    private static GeminiClient client() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(System.getenv("GEMINI_API_KEY"));
        return new RestGeminiClient(RestClient.builder(), properties);
    }

    @Test
    @Order(1)
    void theBookCanBeSentAndItsConversationIdReturned() {
        interactionId = client().openContext(BOOK);

        assertThat(interactionId).isNotBlank();
    }

    @Test
    @Order(2)
    void aStyleComesBackAsUsableText() {
        style = client().generateStyle(interactionId);

        assertThat(style).isNotBlank();
    }

    /** Also proves the chaining works: the model answers about a book it was never re-sent. */
    @Test
    @Order(3)
    void charactersComeBackAsStructuredJsonFromTheStoredConversation() {
        characters = client().generateCharacters(interactionId, style);

        assertThat(characters).isNotEmpty();
        assertThat(characters).allSatisfy(character -> {
            assertThat(character.name()).isNotBlank();
            assertThat(character.prompt()).isNotBlank();
        });
        assertThat(characters.toString()).containsIgnoringCase("mole");
    }

    /**
     * The costly one, and the one most likely to hit a free-tier limit: image models are metered
     * far more tightly than text. A 429 here means the client is fine and the quota is not.
     */
    @Test
    @Order(4)
    void anImageComesBackAsDecodablePngBytesAndAdvancesTheChain() {
        GeminiClient client = client();
        String imageChain = client.openImageContext(style);

        ImageTurn turn = client.generateImage(imageChain, characters.get(0).prompt());

        assertThat(turn.image()).hasSizeGreaterThan(1000);
        assertThat(turn.interactionId()).isNotBlank();
    }
}
