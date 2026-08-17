package com.hoangkhoi.springboot_book_generate_images.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoangkhoi.springboot_book_generate_images.config.GeminiProperties;
import com.hoangkhoi.springboot_book_generate_images.exception.GeminiException;
import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;
import com.hoangkhoi.springboot_book_generate_images.service.GeminiClient;
import com.hoangkhoi.springboot_book_generate_images.service.PipelineRules;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The real Gemini client, over plain REST, following the cookbook notebook's pipeline.
 *
 * Step 1 uploads the book through the File API and opens a conversation referencing it;
 * steps 2 and 4 continue that conversation by id, so the book crosses the wire exactly once.
 * Steps 3 and 5 run a second conversation on the image model, each picture continuing the previous one — that
 * chain, not re-sent reference bytes, is what keeps characters consistent.
 */
@Component
@ConditionalOnProperty(name = "gemini.mode", havingValue = "real", matchIfMissing = true)
public class RestGeminiClient implements GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(RestGeminiClient.class);

    /**
     * The notebook's negative prompt. Passed as an ordinary message, not as system_instruction —
     * the notebook notes the image model currently ignores system instructions.
     */
    private static final String IMAGE_RULES = """
            There must be no text on the image, it should not look like a cover page.
            It should be a full illustration with no borders, titles, nor description.
            Unless asked otherwise, stay family-friendly with uplifting colors.
            Each produced image should be a simple image, no panels.""";

    /** Matches the notebook's Prompt pydantic model: an array of {name, prompt}. */
    private static final String PROMPT_LIST_SCHEMA = """
            {"type":"array","items":{"type":"object",
             "properties":{"name":{"type":"string"},"prompt":{"type":"string"}},
             "required":["name","prompt"]}}""";

    private final RestClient http;
    private final GeminiProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public RestGeminiClient(RestClient.Builder builder, GeminiProperties properties) {
        this.properties = properties;
        this.http = builder.build();
    }

    /**
     * Warns rather than refuses to start: without a key the app must still list projects and show
     * what earlier runs produced. The failure belongs on the step the user presses, not on boot.
     */
    @PostConstruct
    void checkKey() {
        if (!properties.hasApiKey()) {
            log.warn("gemini.mode=real but GEMINI_API_KEY is not set — every step will fail. "
                    + "Set the key, or set gemini.mode=simulate to work without one.");
        }
    }

    @Override
    public String openContext(String bookText) {
        String fileUri = uploadBook(bookText);

        ObjectNode body = interaction(properties.getTextModel(), null);
        ArrayNode input = body.putArray("input");
        input.addObject().put("type", "text").put("text",
                "Here's a book, to illustrate using Nano Banana. "
                        + "Don't say anything for now, instructions will follow.");
        input.addObject().put("type", "document").put("uri", fileUri);

        return GeminiWire.interactionId(send(body, "send the book"))
                .orElseThrow(() -> new GeminiException(
                        "Gemini accepted the book but returned no conversation id to reuse."));
    }

    @Override
    public String generateStyle(String contextRef) {
        ObjectNode body = interaction(properties.getTextModel(), contextRef);
        body.put("input", "Can you define an art style that would fit the story but with a twist? "
                + "Just give us the prompt for the art style that will be added to the future "
                + "prompts.");

        return GeminiWire.text(send(body, "generate a style"))
                .orElseThrow(() -> new GeminiException("Gemini returned no style."));
    }

    @Override
    public List<GeneratedItem> generateCharacters(String contextRef, String style) {
        ObjectNode body = interaction(properties.getTextModel(), contextRef);
        body.put("input", """
                Can you describe the main characters (only the adults, at most %d of them) and \
                prepare a prompt describing them with as much detail as possible (use the \
                descriptions from the book) so Nano Banana can generate images of them? \
                Each prompt should be at least 50 words. Follow this style: "%s"."""
                .formatted(PipelineRules.MAX_CHARACTERS, style));
        jsonOutput(body);

        return parseItems(send(body, "generate characters"), "characters");
    }

    @Override
    public List<GeneratedItem> generateChapters(String contextRef, String style,
            List<String> characterNames) {
        ObjectNode body = interaction(properties.getTextModel(), contextRef);
        body.put("input", """
                Now, for at most %d chapter of the book, give me a prompt to illustrate what \
                happens in it. It should be a single image, not a multi-tiled page. Be very \
                descriptive, especially of the characters: name them and reuse their character \
                prompts if they appear. These are the characters: %s. Follow this style: "%s"."""
                .formatted(PipelineRules.MAX_CHAPTERS, String.join(", ", characterNames), style));
        jsonOutput(body);

        return parseItems(send(body, "generate chapter prompts"), "chapter prompts");
    }

    @Override
    public String openImageContext(String style) {
        ObjectNode body = interaction(properties.getImageModel(), null);
        body.put("input", """
                You are going to generate illustrations for a book.
                The style we want you to follow is: %s
                Also follow these rules: %s""".formatted(style, IMAGE_RULES));

        return GeminiWire.interactionId(send(body, "start the illustration conversation"))
                .orElseThrow(() -> new GeminiException(
                        "Gemini returned no conversation id for the illustrations."));
    }

    @Override
    public ImageTurn generateImage(String previousImageInteractionId, String prompt) {
        ObjectNode body = interaction(properties.getImageModel(), previousImageInteractionId);
        body.put("input", prompt);

        JsonNode response = send(body, "generate an image");
        byte[] image = GeminiWire.image(response).orElseThrow(() -> new GeminiException(
                "Gemini returned no image. It may have refused this prompt; retry, or edit the "
                        + "book text if it keeps happening."));
        // Falling back to the previous id keeps the chain alive if this reply omitted its own.
        String next = GeminiWire.interactionId(response).orElse(previousImageInteractionId);
        return new ImageTurn(image, next);
    }

    // ---- request building -------------------------------------------------

    private ObjectNode interaction(String model, String previousInteractionId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        if (previousInteractionId != null) {
            body.put("previous_interaction_id", previousInteractionId);
        }
        return body;
    }

    /** The notebook's structured-output form: JSON mime type plus an explicit schema. */
    private void jsonOutput(ObjectNode body) {
        try {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "text");
            format.put("mime_type", "application/json");
            format.set("schema", mapper.readTree(PROMPT_LIST_SCHEMA));
        } catch (Exception e) {
            throw new IllegalStateException("The built-in response schema is not valid JSON", e);
        }
    }

    // ---- transport --------------------------------------------------------

    /** Uploads the book so the conversation can reference it instead of carrying it. */
    private String uploadBook(String bookText) {
        requireKey();
        log.info("Gemini upload the book → {} bytes (this is the only time the text is sent)",
                bookText.length());
        try {
            String raw = http.post()
                    .uri(properties.getUploadUrl() + "?uploadType=media")
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(bookText.getBytes(StandardCharsets.UTF_8))
                    .exchange((request, response) -> read(response, "upload the book"));
            JsonNode file = mapper.readTree(raw).path("file");
            String uri = file.path("uri").asText(null);
            if (uri == null || uri.isBlank()) {
                throw new GeminiException("Gemini stored the book but returned no file URI.");
            }
            return uri;
        } catch (GeminiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GeminiException("Could not reach Gemini to upload the book: "
                    + e.getMessage());
        } catch (Exception e) {
            throw new GeminiException("Gemini's reply to the book upload could not be read: "
                    + e.getMessage());
        }
    }

    private JsonNode send(ObjectNode body, String what) {
        requireKey();
        // Logs the size of every outgoing call, which is how "the book is sent once" is checked
        // from outside: the upload is large, every later call is a few hundred bytes.
        log.info("Gemini {} → {} bytes{}", what, body.toString().length(),
                body.has("previous_interaction_id")
                        ? " (continuing " + body.get("previous_interaction_id").asText() + ")"
                        : "");
        try {
            String raw = http.post()
                    .uri(properties.getBaseUrl() + "/interactions")
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .exchange((request, response) -> read(response, what));
            return mapper.readTree(raw);
        } catch (GeminiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GeminiException("Could not reach Gemini to " + what + ": " + e.getMessage());
        } catch (Exception e) {
            throw new GeminiException("Gemini's reply to " + what + " could not be read: "
                    + e.getMessage());
        }
    }

    private String read(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
            String what) throws java.io.IOException {
        String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        int status = response.getStatusCode().value();
        if (status >= 400) {
            throw failure(status, text, what);
        }
        return text;
    }

    private void requireKey() {
        if (!properties.hasApiKey()) {
            throw new GeminiException(
                    "GEMINI_API_KEY is not set, so Gemini cannot be called. Set the key and retry.");
        }
    }

    private GeminiException failure(int status, String body, String what) {
        if (GeminiWire.isMissingContext(status, body)) {
            return new GeminiException(
                    "Gemini no longer has the stored conversation for this book. Retrying will "
                            + "send the book again.", true);
        }
        log.warn("Gemini returned {} trying to {}: {}", status, what, body);
        if (status == 429) {
            return new GeminiException("Gemini rejected the call: quota exhausted (429). Image "
                    + "models have a much tighter free-tier limit than text. Retry later, or "
                    + "switch gemini.mode=simulate while you work on something else.");
        }
        return new GeminiException("Gemini returned " + status + " trying to " + what + ".");
    }

    // ---- parsing ----------------------------------------------------------

    /**
     * Reads the name/prompt list, tolerating a model that wraps its JSON in prose or code fences —
     * which they do occasionally, whatever the response format asks for.
     */
    private List<GeneratedItem> parseItems(JsonNode response, String what) {
        String text = GeminiWire.text(response)
                .orElseThrow(() -> new GeminiException("Gemini returned no " + what + "."));
        try {
            JsonNode parsed = mapper.readTree(stripFences(text));
            JsonNode array = parsed.isArray() ? parsed : firstArray(parsed);
            if (array == null || !array.isArray() || array.isEmpty()) {
                throw new GeminiException("Gemini's " + what + " came back in an unusable shape.");
            }
            List<GeneratedItem> items = new ArrayList<>();
            for (JsonNode item : array) {
                String name = item.path("name").asText("").trim();
                String prompt = item.path("prompt").asText("").trim();
                if (!name.isEmpty() && !prompt.isEmpty()) {
                    items.add(new GeneratedItem(name, prompt));
                }
            }
            if (items.isEmpty()) {
                throw new GeminiException("Gemini returned no usable " + what + ".");
            }
            return items;
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException("Gemini's " + what + " were not valid JSON.");
        }
    }

    private static String stripFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > 0 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static JsonNode firstArray(JsonNode node) {
        var fields = node.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }
}
