// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.core.demo.PlmApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PlmApi} on its own: without {@code PLM_API_BASE} every call is 503 before any connection
 * is tried; without an origin secret (local runs) no {@code x-origin-verify} header is sent; the
 * path names the PLM by its code from {@link PlmApi#PLMS} and the PLM's answer comes back as it was.
 */
class PlmApiTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void isUnconfiguredWithoutABaseAndAnswers503() {
        PlmApi api = new PlmApi("", () -> "secret", json);
        assertThat(api.configured()).isFalse();
        assertThatThrownBy(() -> api.update("uk", Map.of()))
                .isInstanceOf(DemoRejectedException.class)
                .satisfies(e -> {
                    assertThat(((DemoRejectedException) e).status().value()).isEqualTo(503);
                    assertThat(((DemoRejectedException) e).toError().reason()).isEqualTo("plm-api-not-configured");
                });
    }

    @Test
    void sendsNoOriginHeaderWithoutASecretAndReturnsThePlmAnswerAsItCame() throws IOException {
        try (StubPlmApi plm = new StubPlmApi()) {
            PlmApi api = new PlmApi(plm.base() + "/", () -> null, json);
            assertThat(api.configured()).isTrue();

            PlmApi.Reply reply = api.update("de", Map.of("table", "stecker"));

            assertThat(reply.ok()).isTrue();
            assertThat(json.readTree(reply.body()).get("plm").asText()).isEqualTo("DE");
            assertThat(plm.seen).hasSize(1);
            StubPlmApi.Seen call = plm.seen.get(0);
            assertThat(call.path()).isEqualTo("/api/de/demo/update");
            assertThat(call.headers()).doesNotContainKey("x-origin-verify");
            assertThat(call.headers()).containsEntry("x-atelier-profile", "export-officer").containsEntry("x-atelier-actor", "atelier-core");
            assertThat(call.body()).isEqualTo("{\"table\":\"stecker\"}");

            plm.failWith = 400;
            PlmApi.Reply refused = api.update("de", Map.of());
            assertThat(refused.ok()).isFalse();
            assertThat(refused.status()).isEqualTo(400);
            assertThat(json.readTree(refused.body()).get("reason").asText()).isEqualTo("stub-failure");
        }
    }

    @Test
    void sendsNothingForACodeThatIsNotOneOfThePlms() throws IOException {
        try (StubPlmApi plm = new StubPlmApi()) {
            PlmApi api = new PlmApi(plm.base(), () -> null, json);
            for (String code : new String[] {"../core", "fr/../x", "FR", "core", "fr?x=", ""}) {
                assertThatThrownBy(() -> api.update(code, Map.of()))
                        .as(code)
                        .isInstanceOf(DemoRejectedException.class)
                        .satisfies(e -> assertThat(((DemoRejectedException) e).toError().reason()).isEqualTo("unknown-plm"));
            }
            assertThat(plm.seen).isEmpty();
        }
    }
}
