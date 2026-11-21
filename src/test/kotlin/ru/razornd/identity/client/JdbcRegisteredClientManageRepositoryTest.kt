/*
 * Copyright 2026 Daniil <RazorNd> Razorenov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.razornd.identity.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.json.JsonAssert
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.json.JsonComparison
import ru.razornd.identity.TestcontainersConfiguration
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.function.BiPredicate

@JdbcTest
@Import(TestcontainersConfiguration::class)
@Sql("classpath:sql/create-registered-client-test-data.sql")
class JdbcRegisteredClientManageRepositoryTest {

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private lateinit var repository: JdbcRegisteredClientManageRepository

    @Autowired
    fun setUp(operations: JdbcOperations) {
        repository = JdbcRegisteredClientManageRepository(operations)
    }

    @Test
    fun `finds registered client by internal id`() {
        val actual = repository.findById(FIXTURE_ID)

        assertThat(actual).isNotNull
        assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo(fixtureClient())
    }

    @Test
    fun `returns null when internal id is unknown`() {
        assertThat(repository.findById("missing-id")).isNull()
    }

    @Test
    fun `finds registered client by client id`() {
        val actual = repository.findByClientId(FIXTURE_CLIENT_ID)

        assertThat(actual).isNotNull
        assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo(fixtureClient())
    }

    @Test
    fun `returns null when client id is unknown`() {
        assertThat(repository.findByClientId("missing-client")).isNull()
    }

    @Test
    fun `creates registered client row`() {
        val client = registeredClient(
            id = "created-id",
            clientId = "created-client",
            clientName = "Created client",
        )

        repository.save(client)

        assertThat(findClientRow(client.id))
            .usingRecursiveComparison()
            .withEqualsForFields(jsonEquals(), "client_settings", "token_settings")
            .isEqualTo(
                mapOf(
                    "id" to "created-id",
                    "client_id" to "created-client",
                    "client_id_issued_at" to Timestamp.from(Instant.parse("2026-02-03T04:05:06Z")),
                    "client_secret" to "created-secret",
                    "client_secret_expires_at" to null,
                    "client_name" to "Created client",
                    "client_authentication_methods" to "client_secret_basic",
                    "authorization_grant_types" to "client_credentials",
                    "redirect_uris" to "https://created.example/callback",
                    "post_logout_redirect_uris" to "https://created.example/logout",
                    "scopes" to "profile",
                    "client_settings" to CLIENT_SETTINGS_JSON,
                    "token_settings" to TOKEN_SETTINGS_JSON,
                )
            )
    }

    @Test
    fun `updates registered client row`() {
        val client = registeredClient(
            id = FIXTURE_ID,
            clientId = FIXTURE_CLIENT_ID,
            clientName = "Updated client",
            clientSecret = "updated-secret",
            scope = "email",
        )

        repository.save(client)

        assertThat(findClientRow(client.id))
            .usingRecursiveComparison()
            .withEqualsForFields(jsonEquals(), "client_settings", "token_settings")
            .isEqualTo(
                mapOf(
                    "id" to FIXTURE_ID,
                    "client_id" to FIXTURE_CLIENT_ID,
                    "client_id_issued_at" to Timestamp.from(Instant.parse("2026-01-02T03:04:05Z")),
                    "client_secret" to "updated-secret",
                    "client_secret_expires_at" to null,
                    "client_name" to "Updated client",
                    "client_authentication_methods" to "client_secret_basic",
                    "authorization_grant_types" to "client_credentials",
                    "redirect_uris" to "https://created.example/callback",
                    "post_logout_redirect_uris" to "https://created.example/logout",
                    "scopes" to "email",
                    "client_settings" to CLIENT_SETTINGS_JSON,
                    "token_settings" to TOKEN_SETTINGS_JSON,
                )
            )
    }

    @Test
    fun `deletes registered client row`() {
        repository.deleteById(FIXTURE_ID)

        assertThat(rowExists(FIXTURE_ID)).isFalse()
    }

    @Test
    fun `deleting unknown id does not fail`() {
        repository.deleteById("missing-id")

        assertThat(rowExists("missing-id")).isFalse()
    }

    private fun fixtureClient() = RegisteredClient.withId(FIXTURE_ID)
        .clientId(FIXTURE_CLIENT_ID)
        .clientIdIssuedAt(Instant.parse("2026-01-02T03:04:05Z"))
        .clientSecret("fixture-secret")
        .clientName("Fixture client")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://fixture.example/callback")
        .scope("profile")
        .clientSettings(
            ClientSettings.builder()
                .requireProofKey(false)
                .requireAuthorizationConsent(false)
                .build()
        )
        .tokenSettings(
            TokenSettings.builder()
                .authorizationCodeTimeToLive(Duration.ofMinutes(3))
                .accessTokenTimeToLive(Duration.ofMinutes(7))
                .refreshTokenTimeToLive(Duration.ofHours(2))
                .build()
        )
        .build()

    private fun registeredClient(
        id: String,
        clientId: String,
        clientName: String,
        clientSecret: String = "created-secret",
        scope: String = "profile",
    ) = RegisteredClient.withId(id)
        .clientId(clientId)
        .clientIdIssuedAt(Instant.parse("2026-02-03T04:05:06Z"))
        .clientSecret(clientSecret)
        .clientName(clientName)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .redirectUri("https://created.example/callback")
        .postLogoutRedirectUri("https://created.example/logout")
        .scope(scope)
        .clientSettings(
            ClientSettings.builder()
                .requireProofKey(false)
                .requireAuthorizationConsent(false)
                .build()
        )
        .tokenSettings(
            TokenSettings.builder()
                .authorizationCodeTimeToLive(Duration.ofMinutes(3))
                .accessTokenTimeToLive(Duration.ofMinutes(7))
                .refreshTokenTimeToLive(Duration.ofHours(2))
                .build()
        )
        .build()

    private fun findClientRow(id: String): Map<String, Any?> =
        jdbcClient.sql(
            """
                SELECT id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
                       client_name, client_authentication_methods, authorization_grant_types,
                       redirect_uris, post_logout_redirect_uris, scopes, client_settings::text, token_settings::text
                FROM oauth2_registered_client
                WHERE id = :id
            """.trimIndent()
        )
            .param("id", id)
            .query()
            .singleRow()

    private fun rowExists(id: String): Boolean =
        jdbcClient.sql("SELECT COUNT(*) > 0 FROM oauth2_registered_client WHERE id = :id")
            .param("id", id)
            .query(Boolean::class.java)
            .single()

    private fun jsonEquals(): BiPredicate<String, String> {
        val comparator = JsonAssert.comparator(JsonCompareMode.STRICT)
        return BiPredicate { actual, expected ->
            comparator.compare(expected, actual).result == JsonComparison.Result.MATCH
        }
    }

    private companion object {
        const val FIXTURE_ID = "fixture-id"
        const val FIXTURE_CLIENT_ID = "fixture-client"
        const val CLIENT_SETTINGS_JSON =
            $$"""
              {
                "@class": "java.util.Collections$UnmodifiableMap",
                "settings.client.require-proof-key": false,
                "settings.client.require-authorization-consent": false
              }
              """
        const val TOKEN_SETTINGS_JSON =
            $$"""
              {
                "@class": "java.util.Collections$UnmodifiableMap",
                "settings.token.reuse-refresh-tokens": true,
                "settings.token.x509-certificate-bound-access-tokens": false,
                "settings.token.id-token-signature-algorithm": [
                  "org.springframework.security.oauth2.jose.jws.SignatureAlgorithm",
                  "RS256"
                ],
                "settings.token.access-token-time-to-live": [
                  "java.time.Duration",
                  "PT7M"
                ],
                "settings.token.access-token-format": {
                  "@class": "org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat",
                  "value": "self-contained"
                },
                "settings.token.refresh-token-time-to-live": [
                  "java.time.Duration",
                  "PT2H"
                ],
                "settings.token.authorization-code-time-to-live": [
                  "java.time.Duration",
                  "PT3M"
                ],
                "settings.token.device-code-time-to-live": [
                  "java.time.Duration",
                  "PT5M"
                ]
              }
              """
    }
}
