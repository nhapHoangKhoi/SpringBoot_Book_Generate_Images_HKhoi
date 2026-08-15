package com.hoangkhoi.springboot_book_generate_images.repository;

import com.hoangkhoi.springboot_book_generate_images.enums.ProjectStatus;
import com.hoangkhoi.springboot_book_generate_images.enums.Step;
import com.hoangkhoi.springboot_book_generate_images.enums.StepState;
import com.hoangkhoi.springboot_book_generate_images.exception.ProjectNotFoundException;
import com.hoangkhoi.springboot_book_generate_images.model.IllustratedItem;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The data directory as a whole: layout, isolation, and safety under concurrent writers. */
class ProjectRepositoryTest {

    private static final String BOOK = "I.\nTHE RIVER BANK\n\nThe Mole had been working very hard.";

    @TempDir
    Path dataDir;

    private ProjectRepository repo;
    private String userId;

    @BeforeEach
    void setUp() {
        repo = new ProjectRepository(new JsonStore(), new ProjectLocks(), dataDir);
        userId = repo.saveUser(User.of("Test A", "testa@gmail.com")).getId();
    }

    @Test
    void aUserIsFoundAgainByTheIdDerivedFromTheirEmail() {
        Optional<User> found = repo.findUser(User.idFor("TestA@Gmail.com "));

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test A");
        assertThat(found.get().getEmail()).isEqualTo("testa@gmail.com");
    }

    @Test
    void unknownUserIsEmpty() {
        assertThat(repo.findUser(User.idFor("nobody@example.com"))).isEmpty();
    }

    @Test
    void aNewProjectStartsAsADraft() {
        Project created = repo.create(userId, "Hello A", BOOK);

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(ProjectStatus.CREATED);
        assertThat(created.getStepState()).isEqualTo(StepState.IDLE);
    }

    /** Book text lives beside the project, never inside it — polling must not ship a novel. */
    @Test
    void bookTextIsStoredSeparatelyFromTheProjectDocument() throws IOException {
        Project created = repo.create(userId, "Hello A", BOOK);

        assertThat(repo.readBookText(userId, created.getId())).isEqualTo(BOOK);
        Path json = dataDir.resolve(
                "users/" + userId + "/projects/" + created.getId() + "/project.json");
        assertThat(Files.readString(json, StandardCharsets.UTF_8)).doesNotContain("THE RIVER BANK");
    }

    @Test
    void aSavedProjectIsFoundAgainWithItsState() {
        Project created = repo.create(userId, "Hello A", BOOK);
        repo.update(userId, created.getId(), p -> {
            p.setStyle("Warm hand-painted watercolour");
            p.completeStep(Step.STYLE);
            return null;
        });

        Project loaded = repo.find(userId, created.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(ProjectStatus.STYLE_SET);
        assertThat(loaded.getStyle()).isEqualTo("Warm hand-painted watercolour");
    }

    @Test
    void anUnknownProjectIsEmpty() {
        assertThat(repo.find(userId, "does-not-exist")).isEmpty();
    }

    @Test
    void updatingAnUnknownProjectFails() {
        assertThatThrownBy(() -> repo.update(userId, "does-not-exist", p -> null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void updateReturnsWhateverTheMutationReturned() {
        Project created = repo.create(userId, "Hello A", BOOK);

        String result = repo.update(userId, created.getId(), p -> "accepted");

        assertThat(result).isEqualTo("accepted");
    }

    /** A user must never see, or be able to load, another user's project. */
    @Test
    void projectsAreIsolatedPerUser() {
        String otherId = repo.saveUser(User.of("Test B", "testb@gmail.com")).getId();
        Project mine = repo.create(userId, "Mine", BOOK);
        repo.create(otherId, "Theirs", BOOK);

        assertThat(repo.findAll(userId)).extracting(Project::getTitle).containsExactly("Mine");
        assertThat(repo.findAll(otherId)).extracting(Project::getTitle).containsExactly("Theirs");
        assertThat(repo.find(otherId, mine.getId())).isEmpty();
    }

    /** Timestamps are set explicitly: three creates in one millisecond would tie on a real clock. */
    @Test
    void projectsAreListedNewestFirst() {
        Instant base = Instant.parse("2026-08-16T10:00:00Z");
        createdAt(repo.create(userId, "First", BOOK), base);
        createdAt(repo.create(userId, "Third", BOOK), base.plusSeconds(120));
        createdAt(repo.create(userId, "Second", BOOK), base.plusSeconds(60));

        assertThat(repo.findAll(userId)).extracting(Project::getTitle)
                .containsExactly("Third", "Second", "First");
    }

    private void createdAt(Project project, Instant instant) {
        repo.update(userId, project.getId(), p -> {
            p.setCreatedAt(instant);
            return null;
        });
    }

    @Test
    void aUserWithNoProjectsListsNothing() {
        String emptyUser = repo.saveUser(User.of("Empty", "empty@example.com")).getId();

        assertThat(repo.findAll(emptyUser)).isEmpty();
    }

    /**
     * The §5.2 requirement, directly: overlapping writes to one project must not lose an update
     * or leave the document unreadable.
     */
    @Test
    void concurrentUpdatesToOneProjectAllLand() throws Exception {
        Project created = repo.create(userId, "Hello A", BOOK);
        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            String name = "Character " + i;
            futures.add(pool.submit(() -> repo.update(userId, created.getId(), p -> {
                List<IllustratedItem> characters = new ArrayList<>(p.getCharacters());
                characters.add(new IllustratedItem(name, "prompt for " + name));
                p.setCharacters(characters);
                return null;
            })));
        }
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Project loaded = repo.find(userId, created.getId()).orElseThrow();
        assertThat(loaded.getCharacters()).hasSize(writers);
        assertThat(loaded.getCharacters()).extracting(IllustratedItem::getName)
                .doesNotHaveDuplicates();
    }

    /**
     * Polling while a step writes is the normal case, not an edge case. On Windows an open read
     * handle blocks the rename that publishes a write, so unlocked reads break writes outright.
     */
    @Test
    void readingWhileWritingBreaksNeither() throws Exception {
        Project created = repo.create(userId, "Hello A", BOOK);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> writer = pool.submit(() -> {
            for (int i = 0; i < 100; i++) {
                int generation = i;
                repo.update(userId, created.getId(), p -> {
                    p.setStyle("style " + generation);
                    return null;
                });
            }
        });
        Future<?> reader = pool.submit(() -> {
            for (int i = 0; i < 100; i++) {
                assertThat(repo.find(userId, created.getId())).isPresent();
            }
        });

        writer.get(30, TimeUnit.SECONDS);
        reader.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(repo.find(userId, created.getId()).orElseThrow().getStyle())
                .isEqualTo("style 99");
    }

    @Test
    void imagesAreWrittenAndFoundByName() {
        Project created = repo.create(userId, "Hello A", BOOK);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};

        String fileName = repo.writeImage(userId, created.getId(), "portrait-0.png", png);

        assertThat(fileName).isEqualTo("portrait-0.png");
        assertThat(repo.findImage(userId, created.getId(), "portrait-0.png"))
                .get()
                .satisfies(path -> assertThat(path).hasBinaryContent(png));
    }

    @Test
    void anImageThatWasNeverGeneratedIsEmpty() {
        Project created = repo.create(userId, "Hello A", BOOK);

        assertThat(repo.findImage(userId, created.getId(), "portrait-9.png")).isEmpty();
    }
}
