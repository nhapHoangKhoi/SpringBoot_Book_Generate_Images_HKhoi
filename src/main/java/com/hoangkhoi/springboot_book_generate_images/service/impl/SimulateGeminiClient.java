package com.hoangkhoi.springboot_book_generate_images.service.impl;

import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;
import com.hoangkhoi.springboot_book_generate_images.service.GeminiClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Component
@ConditionalOnProperty(name = "gemini.mode", havingValue = "simulate")
public class SimulateGeminiClient implements GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(SimulateGeminiClient.class);

    private static final int PORTRAIT_WIDTH = 600;
    private static final int PORTRAIT_HEIGHT = 800;
    private static final int SCENE_WIDTH = 1024;
    private static final int SCENE_HEIGHT = 640;

    private final Duration textDelay;
    private final Duration imageDelay;
    /** Per image conversation, what it has drawn so far — the simulated stand-in for model memory. */
    private final Map<String, List<byte[]>> drawnSoFar = new ConcurrentHashMap<>();

    public SimulateGeminiClient(
            @Value("${gemini.simulate.text-delay:3s}") Duration textDelay,
            @Value("${gemini.simulate.image-delay:8s}") Duration imageDelay) {
        this.textDelay = textDelay;
        this.imageDelay = imageDelay;
    }

    /** Loud on purpose: nobody should mistake simulated output for something Gemini produced. */
    @PostConstruct
    void announce() {
        log.warn("gemini.mode=simulate — no real Gemini calls. Text steps pause {}, images {}.",
                textDelay, imageDelay);
    }

    @Override
    public String openContext(String bookText) {
        pause(textDelay);
        return "files/simulate-" + Integer.toHexString(bookText.hashCode());
    }

    @Override
    public String generateStyle(String contextRef) {
        pause(textDelay);
        return "Warm hand-painted watercolour with soft ink outlines — a storybook feel, "
                + "lightly heightened with saturated colour and gentle light.";
    }

    @Override
    public List<GeneratedItem> generateCharacters(String contextRef, String style) {
        pause(textDelay);
        return List.of(
                new GeneratedItem("The Mole",
                        "A shy, kindly adult mole in a velvet waistcoat, whiskers dusted with "
                                + "spring-cleaning whitewash, blinking in bright sunlight."),
                new GeneratedItem("The Water Rat",
                        "A capable adult river rat in a rowing jersey, sleeves pushed up, "
                                + "holding a wicker picnic basket by the riverbank."));
    }

    @Override
    public List<GeneratedItem> generateChapters(String contextRef, String style,
            List<String> characterNames) {
        pause(textDelay);
        return List.of(new GeneratedItem("The River Bank",
                "A sunlit riverbank in early spring, " + String.join(" and ", characterNames)
                        + " together beside a small blue rowing boat, reeds and hawthorn blossom."));
    }

    @Override
    public String openImageContext(String style) {
        pause(textDelay);
        return "simulate-images-" + Integer.toHexString(style.hashCode());
    }

    @Override
    public ImageTurn generateImage(String previousImageInteractionId, String prompt) {
        pause(imageDelay);
        // Anything drawn earlier in this chain is what a real model would "remember"; painting
        // them into the next picture makes that memory visible instead of merely claimed.
        List<byte[]> remembered = drawnSoFar.computeIfAbsent(
                previousImageInteractionId.split("#")[0], key -> new CopyOnWriteArrayList<>());
        boolean scene = !remembered.isEmpty();
        BufferedImage image = new BufferedImage(
                scene ? SCENE_WIDTH : PORTRAIT_WIDTH,
                scene ? SCENE_HEIGHT : PORTRAIT_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g, image, prompt);
            drawWrapped(g, prompt, image.getWidth());
            drawReferenceThumbnails(g, remembered, image.getHeight());
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(255, 255, 255, 210));
            g.drawString(scene ? "SIMULATED ILLUSTRATION" : "SIMULATED PORTRAIT", 24, 34);
        } finally {
            g.dispose();
        }
        byte[] png = toPng(image);
        remembered.add(png);
        return new ImageTurn(png, previousImageInteractionId.split("#")[0] + "#" + remembered.size());
    }

    private static void paintBackground(Graphics2D g, BufferedImage image, String prompt) {
        float hue = Math.abs(prompt.hashCode() % 360) / 360f;
        g.setPaint(new GradientPaint(
                0, 0, Color.getHSBColor(hue, 0.45f, 0.85f),
                image.getWidth(), image.getHeight(), Color.getHSBColor(hue, 0.65f, 0.45f)));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
    }

    private static void drawWrapped(Graphics2D g, String prompt, int width) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.setColor(Color.WHITE);
        int maxWidth = width - 96;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : prompt.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        int y = 90;
        for (String text : lines) {
            g.drawString(text, 48, y);
            y += 30;
        }
    }

    private static void drawReferenceThumbnails(Graphics2D g, List<byte[]> references, int height) {
        int x = 24;
        for (byte[] reference : references) {
            try {
                BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(reference));
                if (thumb != null) {
                    g.drawImage(thumb, x, height - 144, 90, 120, null);
                    g.setColor(Color.WHITE);
                    g.drawRect(x, height - 144, 90, 120);
                    x += 106;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Stands in for network latency — the reason in-progress states exist at all. */
    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulated call interrupted", e);
        }
    }
}
