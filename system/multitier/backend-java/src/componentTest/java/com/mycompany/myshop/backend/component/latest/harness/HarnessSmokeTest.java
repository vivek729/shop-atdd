package com.mycompany.myshop.backend.component.latest.harness;

import com.mycompany.myshop.backend.AbstractComponentTest;
import org.junit.jupiter.api.Test;

/**
 * "After" of the SUT-side driver refactor: the harness canary driven through the shared
 * {@code backend} DSL ({@link com.mycompany.myshop.backend.support.BackendDsl}) instead of raw
 * {@code restTemplate}. Same assertion as the {@code legacy/} twin — proves the in-process harness
 * boots and serves (random port/real socket, Testcontainers Postgres migrated and reachable, HTTP
 * round-trips work), with no compose.
 *
 * <p>Hits the dedicated liveness endpoint ({@code GET /health}) rather than a feature endpoint, so
 * the canary depends only on the harness being up — not on the coupon feature. This is the SUT-side
 * mirror of the system-test's {@code assume().myShop().shouldBeRunning()}, which resolves to the same
 * {@code /health} probe. {@code checkHealth()} folds the {@code 200 OK} + {@code status: UP}
 * assertions into the DSL; the {@code legacy/} restTemplate twin remains the dependency-light canary.
 */
class HarnessSmokeTest extends AbstractComponentTest {

    @Test
    void bootsInProcessAndServesHttp() {
        backend.checkHealth();
    }
}
