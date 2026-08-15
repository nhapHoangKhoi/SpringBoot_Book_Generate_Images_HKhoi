package com.hoangkhoi.springboot_book_generate_images.model;

import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Project {

    private String id;
    private String title;
    private Instant createdAt;

    private ProjectStatus status = ProjectStatus.CREATED;
    private StepState stepState = StepState.IDLE;
    private Instant stepStartedAt;
    private String stepError;

    private String geminiContextRef;
    private String geminiImageContextRef;

    private String style;
    private List<IllustratedItem> characters = new ArrayList<>();
    private List<IllustratedItem> chapters = new ArrayList<>();

    public Project() {
    }

    public static Project create(String id, String title, Instant createdAt) {
        Project p = new Project();
        p.id = id;
        p.title = title;
        p.createdAt = createdAt;
        return p;
    }

    public void startStep(Instant now) {
        this.stepState = StepState.RUNNING;
        this.stepStartedAt = now;
        this.stepError = null;
    }

    public void completeStep(Step step) {
        this.status = step.completedStatus();
        this.stepState = StepState.IDLE;
        this.stepStartedAt = null;
        this.stepError = null;
    }

    public void failStep(String error) {
        this.stepState = StepState.FAILED;
        this.stepStartedAt = null;
        this.stepError = error;
    }

    /* Getter, setter */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public StepState getStepState() {
        return stepState;
    }

    public void setStepState(StepState stepState) {
        this.stepState = stepState;
    }

    public Instant getStepStartedAt() {
        return stepStartedAt;
    }

    public void setStepStartedAt(Instant stepStartedAt) {
        this.stepStartedAt = stepStartedAt;
    }

    public String getStepError() {
        return stepError;
    }

    public void setStepError(String stepError) {
        this.stepError = stepError;
    }

    public String getGeminiContextRef() {
        return geminiContextRef;
    }

    public void setGeminiContextRef(String geminiContextRef) {
        this.geminiContextRef = geminiContextRef;
    }

    public String getGeminiImageContextRef() {
        return geminiImageContextRef;
    }

    public void setGeminiImageContextRef(String geminiImageContextRef) {
        this.geminiImageContextRef = geminiImageContextRef;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public List<IllustratedItem> getCharacters() {
        return characters;
    }

    public void setCharacters(List<IllustratedItem> characters) {
        this.characters = characters;
    }

    public List<IllustratedItem> getChapters() {
        return chapters;
    }

    public void setChapters(List<IllustratedItem> chapters) {
        this.chapters = chapters;
    }
    /* End getter, setter */
}
