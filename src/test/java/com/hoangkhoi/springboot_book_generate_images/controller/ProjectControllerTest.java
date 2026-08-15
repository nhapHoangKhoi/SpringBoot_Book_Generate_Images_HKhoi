package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.model.User;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Routing, identity and the shape of what each endpoint returns. Storage is mocked. */
@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    private static final String USER_ID = User.idFor("testa@gmail.com");
    private static final Instant CREATED = Instant.parse("2026-08-15T10:00:00Z");
    private static final String BOOK = "I.\nTHE RIVER BANK\n\nThe Mole had been working hard.";
    /** The same text, escaped for embedding in a JSON request body. */
    private static final String BOOK_JSON =
            "I.\\nTHE RIVER BANK\\n\\nThe Mole had been working hard.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectRepository repository;

    @BeforeEach
    void signedIn() {
        when(repository.findUser(USER_ID))
                .thenReturn(Optional.of(User.of("Test A Name", "testa@gmail.com")));
    }

    private static Project project() {
        return Project.create("tlv2u592", "Hello A", CREATED);
    }

    @Test
    void withoutTheUserHeaderNothingIsReadable() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));

        verify(repository, never()).findAll(any());
    }

    @Test
    void anUnknownUserIdIsRejected() throws Exception {
        when(repository.findUser("ffffffffffffffff")).thenReturn(Optional.empty());

        mvc.perform(get("/api/projects").header("X-User-Id", "ffffffffffffffff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theProjectListCarriesDerivedProgressAndNoBookText() throws Exception {
        Project p = project();
        p.completeStep(Step.STYLE);
        when(repository.findAll(USER_ID)).thenReturn(List.of(p));

        mvc.perform(get("/api/projects").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Hello A"))
                .andExpect(jsonPath("$.data[0].status").value("STYLE_SET"))
                .andExpect(jsonPath("$.data[0].currentStep").value("CHARACTERS"))
                .andExpect(jsonPath("$.data[0].completedSteps").value(1))
                .andExpect(jsonPath("$.data[0].bookText").doesNotExist());
    }

    @Test
    void anEmptyListIsAnEmptyArrayNotAnError() throws Exception {
        when(repository.findAll(USER_ID)).thenReturn(List.of());

        mvc.perform(get("/api/projects").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void creatingAProjectReturnsItsDetail() throws Exception {
        when(repository.create(eq(USER_ID), eq("Hello A"), eq(BOOK))).thenReturn(project());

        mvc.perform(post("/api/projects")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hello A\",\"bookText\":\"" + BOOK_JSON + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("tlv2u592"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.currentStep").value("STYLE"))
                .andExpect(jsonPath("$.data.stale").value(false));
    }

    @Test
    void aProjectWithoutATitleIsRejectedBeforeAnythingIsWritten() throws Exception {
        mvc.perform(post("/api/projects")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","bookText":"some text"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));

        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void aProjectWithoutBookTextIsRejected() throws Exception {
        mvc.perform(post("/api/projects")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hello A","bookText":""}"""))
                .andExpect(status().isBadRequest());

        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void theDetailPayloadIncludesCharactersAndTheirImageState() throws Exception {
        Project p = project();
        p.completeStep(Step.CHARACTERS);
        IllustratedItem mole = new IllustratedItem("Mole", "a shy mole in a waistcoat");
        mole.completeImage("portrait-0.png");
        p.setCharacters(List.of(mole, new IllustratedItem("Rat", "a river rat")));
        when(repository.find(USER_ID, "tlv2u592")).thenReturn(Optional.of(p));

        mvc.perform(get("/api/projects/tlv2u592").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters.length()").value(2))
                .andExpect(jsonPath("$.data.characters[0].imageState").value("DONE"))
                .andExpect(jsonPath("$.data.characters[0].imageFile").value("portrait-0.png"))
                .andExpect(jsonPath("$.data.characters[1].imageState").value("PENDING"))
                .andExpect(jsonPath("$.data.characters[1].imageFile").doesNotExist());
    }

    @Test
    void anUnknownProjectIsNotFound() throws Exception {
        when(repository.find(USER_ID, "nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/projects/nope").header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("PROJECT_NOT_FOUND"));
    }

    /** The book travels inside the same envelope as everything else, newlines intact. */
    @Test
    void theBookTextIsServedInTheStandardEnvelope() throws Exception {
        when(repository.find(USER_ID, "tlv2u592")).thenReturn(Optional.of(project()));
        when(repository.readBookText(USER_ID, "tlv2u592")).thenReturn(BOOK);

        mvc.perform(get("/api/projects/tlv2u592/book").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(BOOK));
    }

    @Test
    void aGeneratedImageIsServedAsBytes(@TempDir Path tempDir) throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        Path file = tempDir.resolve("portrait-0.png");
        Files.write(file, png);
        when(repository.findImage(USER_ID, "tlv2u592", "portrait-0.png"))
                .thenReturn(Optional.of(file));

        mvc.perform(get("/api/projects/tlv2u592/images/portrait-0.png")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(png));
    }

    /** An img tag cannot set a header, so the same route accepts the id as a query parameter. */
    @Test
    void aGeneratedImageCanAlsoBeFetchedWithTheUserIdInTheQuery(@TempDir Path tempDir)
            throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        Path file = tempDir.resolve("portrait-0.png");
        Files.write(file, png);
        when(repository.findImage(USER_ID, "tlv2u592", "portrait-0.png"))
                .thenReturn(Optional.of(file));

        mvc.perform(get("/api/projects/tlv2u592/images/portrait-0.png").param("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));
    }

    @Test
    void anImageRequestWithNoUserAtAllIsRejected() throws Exception {
        mvc.perform(get("/api/projects/tlv2u592/images/portrait-0.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anImageThatHasNotBeenGeneratedIsNotFound() throws Exception {
        when(repository.findImage(USER_ID, "tlv2u592", "portrait-9.png"))
                .thenReturn(Optional.empty());

        mvc.perform(get("/api/projects/tlv2u592/images/portrait-9.png")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
    }
}
