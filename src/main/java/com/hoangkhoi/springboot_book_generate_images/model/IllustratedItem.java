package com.hoangkhoi.springboot_book_generate_images.model;

import com.hoangkhoi.springboot_book_generate_images.enums.ItemState;

public class IllustratedItem {

    private String name;
    private String prompt;
    private ItemState imageState = ItemState.PENDING;
    private String imageFile;
    private String error;

    public IllustratedItem() {
    }

    public IllustratedItem(String name, String prompt) {
        this.name = name;
        this.prompt = prompt;
    }

    public void startImage() {
        this.imageState = ItemState.RUNNING;
        this.error = null;
    }

    public void completeImage(String imageFile) {
        this.imageFile = imageFile;
        this.imageState = ItemState.DONE;
        this.error = null;
    }

    public void failImage(String error) {
        this.imageState = ItemState.FAILED;
        this.error = error;
    }

    public boolean hasImage() {
        return imageState == ItemState.DONE;
    }

    /* Getter, setter */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public ItemState getImageState() {
        return imageState;
    }

    public void setImageState(ItemState imageState) {
        this.imageState = imageState;
    }

    public String getImageFile() {
        return imageFile;
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
    /* End getter, setter */
}
