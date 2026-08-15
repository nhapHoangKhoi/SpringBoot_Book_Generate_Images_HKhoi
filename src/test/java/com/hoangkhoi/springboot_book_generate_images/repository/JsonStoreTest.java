package com.hoangkhoi.springboot_book_generate_images.repository;

import com.hoangkhoi.springboot_book_generate_images.enums.ItemState;
import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Serialisation and the atomic-write guarantee. */
class JsonStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    private final JsonStore store = new JsonStore();

    @TempDir
    Path tempDir;

    @Test
    void missingFileReadsAsEmpty() {
        assertThat(store.read(tempDir.resolve("nope.json"), Project.class)).isEmpty();
    }

    /** Everything the pipeline depends on has to survive a restart, enums and Instants included. */
    @Test
    void projectSurvivesARoundTripWithItsStateIntact() {
        Project original = Project.create("p1", "The Wind in the Willows", NOW);
        original.setStyle("Warm hand-painted watercolour");
        original.startStep(NOW);
        original.completeStep(Step.CHARACTERS);
        IllustratedItem mole = new IllustratedItem("Mole", "a shy mole in a waistcoat");
        mole.completeImage("portrait-0.png");
        original.setCharacters(List.of(mole));
        Path file = tempDir.resolve("project.json");

        store.write(file, original);
        Project loaded = store.read(file, Project.class).orElseThrow();

        assertThat(loaded.getId()).isEqualTo("p1");
        assertThat(loaded.getTitle()).isEqualTo("The Wind in the Willows");
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getStatus()).isEqualTo(ProjectStatus.CHARACTERS_GENERATED);
        assertThat(loaded.getStepState()).isEqualTo(StepState.IDLE);
        assertThat(loaded.getStyle()).isEqualTo("Warm hand-painted watercolour");
        assertThat(loaded.getCharacters()).hasSize(1);
        assertThat(loaded.getCharacters().get(0).getImageState()).isEqualTo(ItemState.DONE);
        assertThat(loaded.getCharacters().get(0).getImageFile()).isEqualTo("portrait-0.png");
    }

    @Test
    void writeCreatesMissingParentDirectories() {
        Path file = tempDir.resolve("users/abc/projects/p1/project.json");

        store.write(file, Project.create("p1", "Deep", NOW));

        assertThat(file).exists();
    }

    @Test
    void writeReplacesAnExistingDocument() {
        Path file = tempDir.resolve("project.json");
        store.write(file, Project.create("p1", "First title", NOW));

        store.write(file, Project.create("p1", "Second title", NOW));

        assertThat(store.read(file, Project.class).orElseThrow().getTitle())
                .isEqualTo("Second title");
    }

    /** A leftover .tmp would mean the move didn't happen — and that readers could see a partial. */
    @Test
    void writeLeavesNoTemporaryFileBehind() throws IOException {
        Path file = tempDir.resolve("project.json");

        store.write(file, Project.create("p1", "The Wind in the Willows", NOW));

        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("project.json");
        }
    }
}
