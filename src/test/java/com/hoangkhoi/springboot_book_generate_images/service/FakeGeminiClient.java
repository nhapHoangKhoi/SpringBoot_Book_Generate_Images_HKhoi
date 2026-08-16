package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for Gemini so the pipeline can be tested without spending image quota.
 *
 * Counts its calls, so a test can assert that a double-click produced exactly one; can be told
 * to fail a specific step; and can be made to block, which is the only way to hold a step in
 * RUNNING long enough to aim a second request at it.
 */
public class FakeGeminiClient implements GeminiClient {

    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final AtomicInteger openContextCalls = new AtomicInteger();
    private final AtomicInteger imageCalls = new AtomicInteger();
    /** The conversation id each image call continued from, in order. */
    private final List<String> imageChain = new ArrayList<>();

    private int charactersToReturn = 2;
    private int chaptersToReturn = 1;
    private RuntimeException failure;
    private String failingPrompt;
    private CountDownLatch blockImagesUntil;

    /** Makes every generating call throw, as a transport error would. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /** Makes only the image whose prompt matches fail, leaving its siblings to succeed. */
    public void failImageFor(String prompt, RuntimeException failure) {
        this.failingPrompt = prompt;
        this.failure = failure;
    }

    /** Holds every image call until the latch opens, so a step stays visibly RUNNING. */
    public void blockImagesUntil(CountDownLatch latch) {
        this.blockImagesUntil = latch;
    }

    public void returnCharacters(int count) {
        this.charactersToReturn = count;
    }

    public void returnChapters(int count) {
        this.chaptersToReturn = count;
    }

    public int openContextCalls() {
        return openContextCalls.get();
    }

    public int imageCalls() {
        return imageCalls.get();
    }

    /** The conversation id each image call continued from, in order. */
    public List<String> imageChain() {
        return imageChain;
    }

    @Override
    public String openContext(String bookText) {
        openContextCalls.incrementAndGet();
        return "files/fake-" + Integer.toHexString(bookText.hashCode());
    }

    @Override
    public String generateStyle(String contextRef) {
        throwIfFailing(null);
        return "Warm hand-painted watercolour with soft ink outlines";
    }

    @Override
    public List<GeneratedItem> generateCharacters(String contextRef, String style) {
        throwIfFailing(null);
        List<GeneratedItem> items = new ArrayList<>();
        for (int i = 0; i < charactersToReturn; i++) {
            items.add(new GeneratedItem("Character " + i, "portrait prompt " + i));
        }
        return items;
    }

    @Override
    public List<GeneratedItem> generateChapters(String contextRef, String style,
            List<String> characterNames) {
        throwIfFailing(null);
        List<GeneratedItem> items = new ArrayList<>();
        for (int i = 0; i < chaptersToReturn; i++) {
            items.add(new GeneratedItem("Chapter " + i,
                    "scene prompt " + i + " featuring " + String.join(" and ", characterNames)));
        }
        return items;
    }

    @Override
    public String openImageContext(String style) {
        throwIfFailing(null);
        return "img-root";
    }

    @Override
    public ImageTurn generateImage(String previousImageInteractionId, String prompt) {
        if (blockImagesUntil != null) {
            try {
                blockImagesUntil.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int call = imageCalls.incrementAndGet();
        synchronized (imageChain) {
            imageChain.add(previousImageInteractionId);
        }
        throwIfFailing(promptKey(prompt));
        return new ImageTurn(PNG, "img-" + call);
    }

    /** The item's own prompt, recovered from the instruction the pipeline wraps around it. */
    private static String promptKey(String instruction) {
        int marker = instruction.indexOf("description: ");
        return marker < 0 ? instruction : instruction.substring(marker + "description: ".length());
    }

    private void throwIfFailing(String prompt) {
        if (failure == null) {
            return;
        }
        if (failingPrompt == null || failingPrompt.equals(prompt)) {
            throw failure;
        }
    }
}
