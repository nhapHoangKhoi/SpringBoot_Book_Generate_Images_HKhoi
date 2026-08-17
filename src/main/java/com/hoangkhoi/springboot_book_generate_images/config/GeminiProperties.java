package com.hoangkhoi.springboot_book_generate_images.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {


    private String apiKey = "";

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /** File API lives on a different path prefix from the rest of the API. */
    private String uploadUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files";


    private String textModel = "gemini-3.7-flash";

    private String imageModel = "gemini-3.1-flash-lite-image";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getTextModel() {
        return textModel;
    }

    public void setTextModel(String textModel) {
        this.textModel = textModel;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
