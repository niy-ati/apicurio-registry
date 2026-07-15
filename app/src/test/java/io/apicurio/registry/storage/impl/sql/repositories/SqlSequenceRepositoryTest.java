package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.H2SqlStatements;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression for #7585: H2 sequence counters must be per repository instance so
 * multiple storages in one JVM do not overwrite each other's IDs.
 */
public class SqlSequenceRepositoryTest {

    @Test
    public void sequenceCountersAreIsolatedBetweenInstances() {
        // HandleFactory is null because H2 *Raw methods use in-memory counters only.
        SqlSequenceRepository blue = new SqlSequenceRepository(null, new H2SqlStatements(),
                LoggerFactory.getLogger("blue"));
        SqlSequenceRepository green = new SqlSequenceRepository(null, new H2SqlStatements(),
                LoggerFactory.getLogger("green"));

        // Advance blue's counters
        assertEquals(1L, blue.nextGlobalIdRaw(null));
        assertEquals(2L, blue.nextGlobalIdRaw(null));
        assertEquals(1L, blue.nextContentIdRaw(null));

        // Green must start from its own counters, not continue from blue
        assertEquals(1L, green.nextGlobalIdRaw(null));
        assertEquals(1L, green.nextContentIdRaw(null));

        // Blue continues independently
        assertEquals(3L, blue.nextGlobalIdRaw(null));
        assertNotEquals(blue.nextGlobalIdRaw(null), green.nextGlobalIdRaw(null));
    }

    @Test
    public void h2SequenceCountersAreThreadSafe() throws Exception {
        SqlSequenceRepository repository = new SqlSequenceRepository(null, new H2SqlStatements(),
                LoggerFactory.getLogger("concurrent"));

        int threads = 8;
        int incrementsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong idSum = new AtomicLong(0);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        idSum.addAndGet(repository.nextGlobalIdRaw(null));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        int totalIds = threads * incrementsPerThread;
        assertEquals(totalIds * (totalIds + 1L) / 2, idSum.get());
    }
}
