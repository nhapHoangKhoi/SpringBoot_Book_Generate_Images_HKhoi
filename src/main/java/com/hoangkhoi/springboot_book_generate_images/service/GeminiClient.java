package com.hoangkhoi.springboot_book_generate_images.service;

import com.hoangkhoi.springboot_book_generate_images.model.GeneratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;

import java.util.List;


public interface GeminiClient {

    /** Uploads the book and opens the text conversation. Returns the interaction id to continue. */
    String openContext(String bookText);

    /** Step 1. Invents an art style from the book. Only called when the user supplied none. */
    String generateStyle(String contextRef);

    /** Step 2. The main adult characters, each with an image prompt. */
    List<GeneratedItem> generateCharacters(String contextRef, String style);

    /** Step 4. Chapter illustration prompts, naming the characters so they can be reused. */
    List<GeneratedItem> generateChapters(String contextRef, String style,
            List<String> characterNames);


    // return the interaction id that the first image should continue from
    String openImageContext(String style);

    ImageTurn generateImage(String previousImageInteractionId, String prompt);
}
