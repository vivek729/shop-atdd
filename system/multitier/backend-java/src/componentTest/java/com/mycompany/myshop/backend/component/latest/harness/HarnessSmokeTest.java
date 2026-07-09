package com.mycompany.myshop.backend.component.latest.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import org.junit.jupiter.api.Test;

/**
 * "After" of the SUT-side driver refactor: the harness canary driven through the shared
 * {@code backend} DSL ({@link com.mycompany.myshop.backend.support.BackendDsl}) instead of raw
 * {@code restTemplate}. Same assertion as the {@code legacy/} twin — proves the in-process harness
 * boots and serves (random port/real socket, Testcontainers Postgres migrated and reachable, HTTP
 * round-trips work), with no compose.
 *
 * <p>{@code browseCoupons()} folds the {@code 200 OK} + non-null-body assertions into the DSL, so the
 * test itself only asserts the fresh-boot empty list. Because it goes through the DSL, this twin also
 * lightly exercises {@code BackendDsl}/{@code BackendDriver}; the {@code legacy/} restTemplate twin
 * remains the dependency-light pure-infra canary.
 */
class HarnessSmokeTest extends AbstractComponentTest {

    @Test
    void bootsInProcessAndServesHttp() {
        var coupons = backend.browseCoupons();

        assertThat(coupons.getCoupons()).isEmpty();
    }
}
