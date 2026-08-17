package com.hoangkhoi.springboot_book_generate_images.service.impl;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.Optional;


final class GeminiWire {

    private GeminiWire() {
    }

    /** The interaction id used to continue this conversation later. */
    static Optional<String> interactionId(JsonNode response) {
        return firstText(response, "id");
    }

    /**
     * The model's text answer.
     *
     */
    static Optional<String> text(JsonNode response) {
        Optional<String> flat = firstText(response, "output_text");
        if (flat.isPresent()) {
            return flat;
        }
        StringBuilder collected = new StringBuilder();
        collectText(response, collected);
        String joined = collected.toString().trim();
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    static Optional<byte[]> image(JsonNode response) {
        return findImage(response).map(node -> Base64.getDecoder()
                .decode(node.path("data").asText().replaceAll("\\s", "")));
    }

    /**
     * True when Gemini rejected the referenced conversation — typically because it aged out.
     *
     * Worth telling apart from any other failure: the fix is to send the book again, and the
     * caller can only know to do that if this is distinguishable.
     */
    static boolean isMissingContext(int status, String body) {
        if (status != 404 && status != 400 && status != 403) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("previous_interaction")
                || lower.contains("interaction not found")
                || (lower.contains("interaction") && lower.contains("not found"))
                || lower.contains("expired");
    }

    private static Optional<String> firstText(JsonNode node, String field) {
        JsonNode found = node.path(field);
        return found.isTextual() && !found.asText().isBlank()
                ? Optional.of(found.asText())
                : Optional.empty();
    }

    private static void collectText(JsonNode node, StringBuilder into) {
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual() && !text.asText().isBlank()) {
                if (!into.isEmpty()) {
                    into.append('\n');
                }
                into.append(text.asText());
            }
            node.fields().forEachRemaining(entry -> {
                if (!"text".equals(entry.getKey())) {
                    collectText(entry.getValue(), into);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectText(child, into));
        }
    }

    private static Optional<JsonNode> findImage(JsonNode node) {
        if (node.isObject()) {
            if (looksLikeImage(node)) {
                return Optional.of(node);
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Optional<JsonNode> found = findImage(fields.next().getValue());
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> found = findImage(child);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeImage(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null || !data.isTextual() || data.asText().isBlank()) {
            return false;
        }
        JsonNode mime = node.has("mime_type") ? node.get("mime_type") : node.get("mimeType");
        return mime != null && mime.isTextual() && mime.asText().startsWith("image/");
    }
}
