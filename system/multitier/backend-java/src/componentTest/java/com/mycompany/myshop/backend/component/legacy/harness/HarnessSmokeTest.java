package com.mycompany.myshop.backend.component.legacy.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * "Before" of the SUT-side driver refactor: the harness canary driven by raw, inlined
 * {@code restTemplate}. Proves the in-process component-test harness itself boots and serves,
 * independent of any feature scenario — random port (real socket), Testcontainers Postgres migrated
 * and reachable, HTTP round-trips work — with no compose. The {@code latest/} twin makes the same
 * assertion through the shared {@code backend} DSL.
 *
 * <p>Because it drives the SUT with raw {@code restTemplate} rather than the DSL, this twin is the
 * dependency-light canary: when it is red, the failure is infrastructure (Flyway migration,
 * {@code @ServiceConnection}, WireMock wiring), not the DSL. The same harness is reused by the Pact
 * provider-verification test, so this also guards that foundation.
 */
class HarnessSmokeTest extends AbstractComponentTest {

    @Test
    void bootsInProcessAndServesHttp() {
        var response = restTemplate.getForEntity("/api/coupons", BrowseCouponsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCoupons()).isEmpty();
    }
}
