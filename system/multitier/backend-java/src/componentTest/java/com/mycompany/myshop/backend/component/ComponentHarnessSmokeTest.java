package com.mycompany.myshop.backend.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Step 5 smoke test: proves the in-process harness boots (random port, real socket), the
 * Testcontainers Postgres is migrated and reachable, and HTTP round-trips work — with no compose.
 */
class ComponentHarnessSmokeTest extends AbstractComponentTest {

    @Test
    void bootsInProcessAndServesHttp() {
        var response = restTemplate.getForEntity("/api/coupons", BrowseCouponsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCoupons()).isEmpty();
    }
}
