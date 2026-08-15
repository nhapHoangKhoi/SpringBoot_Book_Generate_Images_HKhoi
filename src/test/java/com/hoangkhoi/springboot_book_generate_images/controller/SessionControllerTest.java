package com.hoangkhoi.springboot_book_generate_images.controller;

import com.hoangkhoi.springboot_book_generate_images.model.User;
import com.hoangkhoi.springboot_book_generate_images.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectRepository repository;

    @Test
    void anUnknownEmailCreatesTheUser() throws Exception {
        when(repository.saveUser(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test A Name","email":"testa@gmail.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("testa@gmail.com"))
                .andExpect(jsonPath("$.data.name").value("Test A Name"))
                .andExpect(jsonPath("$.data.id").value(User.idFor("testa@gmail.com")));
    }

    /** Same email, later visit: the id must be the one their projects are already filed under. */
    @Test
    void aKnownEmailResolvesToTheSameIdRegardlessOfCasing() throws Exception {
        when(repository.saveUser(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test A Name","email":"  TestA@Gmail.com "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(User.idFor("testa@gmail.com")))
                .andExpect(jsonPath("$.data.email").value("testa@gmail.com"));
    }

    @Test
    void signingInAgainUpdatesTheStoredName() throws Exception {
        when(repository.saveUser(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name","email":"testa@gmail.com"}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).saveUser(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("New Name");
    }

    @Test
    void aMissingNameIsRejected() throws Exception {
        mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","email":"testa@gmail.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }

    @Test
    void aMalformedEmailIsRejected() throws Exception {
        mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test A Name","email":"not-an-email"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }
}
