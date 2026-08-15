package com.hoangkhoi.springboot_book_generate_images.repository;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** The mutual exclusion that makes read-modify-write on a JSON file safe. */
class ProjectLocksTest {

    private final ProjectLocks locks = new ProjectLocks();

    /** Unsynchronised, this loses updates; the whole point of the lock is that it does not. */
    @Test
    void concurrentMutationsOfOneProjectAreSerialised() throws Exception {
        int threads = 8;
        int perThread = 200;
        List<Integer> counter = new ArrayList<>(List.of(0));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    locks.withLock("user/p1", () -> counter.set(0, counter.get(0) + 1));
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(counter.get(0)).isEqualTo(threads * perThread);
    }

    /** One project's slow write must not stall another project's request. */
    @Test
    void differentProjectsDoNotBlockEachOther() throws Exception {
        CountDownLatch bothInside = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Boolean> a = pool.submit(() -> locks.withLock("user/p1", () -> arrive(bothInside)));
        Future<Boolean> b = pool.submit(() -> locks.withLock("user/p2", () -> arrive(bothInside)));

        assertThat(a.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(b.get(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
    }

    /** Signals arrival, then waits for the other thread — times out if the locks are shared. */
    private static boolean arrive(CountDownLatch latch) {
        latch.countDown();
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    void theActionsResultIsReturnedToTheCaller() {
        assertThat(locks.<String>withLock("user/p1", () -> "done")).isEqualTo("done");
    }
}
