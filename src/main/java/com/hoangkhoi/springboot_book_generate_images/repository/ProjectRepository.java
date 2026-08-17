package com.hoangkhoi.springboot_book_generate_images.repository;

import com.hoangkhoi.springboot_book_generate_images.exception.ProjectNotFoundException;
import com.hoangkhoi.springboot_book_generate_images.exception.StorageException;
import com.hoangkhoi.springboot_book_generate_images.model.Project;
import com.hoangkhoi.springboot_book_generate_images.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The data directory, as an API.
 *
 * <pre>
 * data/users/{userId}/user.json
 * data/users/{userId}/projects/{projectId}/project.json
 *                                         /book.txt
 *                                         /images/{name}.png
 * </pre>
 *
 */
@Component
public class ProjectRepository {

    private final JsonStore store;
    private final ProjectLocks locks;
    private final Path dataDir;

    public ProjectRepository(JsonStore store, ProjectLocks locks,
            @Value("${app.data-dir:data}") Path dataDir) {
        this.store = store;
        this.locks = locks;
        this.dataDir = dataDir;
    }

    public User saveUser(User user) {
        store.write(userDir(user.getId()).resolve("user.json"), user);
        return user;
    }

    public Optional<User> findUser(String userId) {
        return store.read(userDir(userId).resolve("user.json"), User.class);
    }

    /** Creates a project with its book text, and returns it in its initial state. */
    public Project create(String userId, String title, String bookText) {
        String projectId = UUID.randomUUID().toString().substring(0, 8);
        Project project = Project.create(projectId, title, Instant.now());
        store.write(projectFile(userId, projectId), project);
        writeString(projectDir(userId, projectId).resolve("book.txt"), bookText);
        return project;
    }

    /**
     * Reads under the project's lock, not just writes.
     *
     * <p>An unlocked read is not merely stale-prone: on Windows an open handle blocks the rename
     * that {@link JsonStore} uses to publish a write, so a poll arriving mid-write fails the write
     * outright. Since a browser polls this project every couple of seconds while a step runs, that
     * collision is routine rather than theoretical.
     */
    public Optional<Project> find(String userId, String projectId) {
        return locks.withLock(lockKey(userId, projectId),
                () -> store.read(projectFile(userId, projectId), Project.class));
    }

    /** Every user with data on disk. Only the startup sweep needs this. */
    public List<String> findAllUserIds() {
        Path users = dataDir.resolve("users");
        if (!Files.isDirectory(users)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(users)) {
            return dirs.filter(Files::isDirectory).map(dir -> dir.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new StorageException("Could not list users", e);
        }
    }

    /** That user's projects, newest first. */
    public List<Project> findAll(String userId) {
        Path projects = userDir(userId).resolve("projects");
        if (!Files.isDirectory(projects)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(projects)) {
            return dirs.map(dir -> dir.getFileName().toString())
                    .map(projectId -> find(userId, projectId))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Could not list projects for " + userId, e);
        }
    }

    /**
     * Read-modify-write under the project's lock, returning whatever the mutation returns.
     *
     * <p>This is the only way a project changes. The pipeline uses the return value to report
     * whether its compare-and-set was accepted.
     */
    public <T> T update(String userId, String projectId, Function<Project, T> mutation) {
        // The lock is reentrant, so the find() below re-enters it rather than deadlocking.
        return locks.withLock(lockKey(userId, projectId), () -> {
            Project project = find(userId, projectId)
                    .orElseThrow(() -> new ProjectNotFoundException(userId, projectId));
            T result = mutation.apply(project);
            store.write(projectFile(userId, projectId), project);
            return result;
        });
    }

    public String readBookText(String userId, String projectId) {
        Path file = projectDir(userId, projectId).resolve("book.txt");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Could not read " + file, e);
        }
    }

    /** Writes an image and returns the file name to record on the item. */
    public String writeImage(String userId, String projectId, String fileName, byte[] bytes) {
        Path file = imagesDir(userId, projectId).resolve(fileName);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
            return fileName;
        } catch (IOException e) {
            throw new StorageException("Could not write " + file, e);
        }
    }

    /** Empty when the image has not been generated. */
    public Optional<Path> findImage(String userId, String projectId, String fileName) {
        Path file = imagesDir(userId, projectId).resolve(fileName).normalize();
        // normalize() plus this check stops a crafted file name escaping the images directory.
        if (!file.startsWith(imagesDir(userId, projectId)) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private void writeString(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lockKey(String userId, String projectId) {
        return userId + "/" + projectId;
    }

    private Path userDir(String userId) {
        return dataDir.resolve("users").resolve(userId);
    }

    private Path projectDir(String userId, String projectId) {
        return userDir(userId).resolve("projects").resolve(projectId);
    }

    private Path projectFile(String userId, String projectId) {
        return projectDir(userId, projectId).resolve("project.json");
    }

    private Path imagesDir(String userId, String projectId) {
        return projectDir(userId, projectId).resolve("images");
    }
}
