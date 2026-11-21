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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.test.context.bean.override.mockito.MockitoBean
import ru.razornd.identity.oauth.ScopeDefinition
import ru.razornd.identity.oauth.ScopeRegistry
import java.time.Duration
import java.time.Instant
import org.springframework.security.oauth2.core.ClientAuthenticationMethod as SpringClientAuthenticationMethod

@SpringBootTest(classes = [ClientService::class])
class ClientServiceTests {
    @MockitoBean
    private lateinit var registeredClientRepository: RegisteredClientManageRepository

    @MockitoBean
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var scopeRegistry: ScopeRegistry

    @Autowired
    private lateinit var clientService: ClientService

    private val registeredClientCaptor = argumentCaptor<RegisteredClient>()

    private val existingRegisteredClient = existingClient()

    @BeforeEach
    fun setUp() {
        doAnswer { (scope: String) ->
            ScopeDefinition(scope, scope, "Test scope").takeIf { scope in REGISTERED_SCOPES }
        }.whenever(scopeRegistry).find(any())
        doAnswer { invocation ->
            val requestedScopes = invocation.getArgument<Collection<String>>(0)
            val unknownScopes = requestedScopes - REGISTERED_SCOPES
            require(unknownScopes.isEmpty()) {
                "Unsupported OAuth2 scopes: ${unknownScopes.joinToString()}"
            }
            requestedScopes.map { ScopeDefinition(it, it, "Test scope") }
        }.whenever(scopeRegistry).requireAll(any())
        doAnswer { (pass: CharSequence) -> "encoded-$pass" }
            .whenever(passwordEncoder).encode(any())
        doAnswer { (checked: CharSequence, expected: String) -> "encoded-$checked" == expected }
            .whenever(passwordEncoder).matches(any(), any())
    }

    @Test
    fun `creates a client with generated id and encoded secret`() {
        val created = clientService.createClient(
            CreateClientRequest(
                clientName = "New client",
                clientAuthenticationMethods = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
                authorizationGrantTypes = setOf(ClientAuthorizationGrantType.AUTHORIZATION_CODE),
                redirectUris = setOf("https://client.example/callback"),
                scopes = setOf("openid", "profile"),
                tokenSettings = ClientTokenSettingsRequest(accessTokenTimeToLive = Duration.ofMinutes(12)),
            )
        )
        verify(registeredClientRepository).save(registeredClientCaptor.capture())
        val savedClient = registeredClientCaptor.singleValue
        val expectedDetails = ClientDetails(
            clientId = created.details.clientId,
            clientName = "New client",
            clientAuthenticationMethods = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
            authorizationGrantTypes = setOf(ClientAuthorizationGrantType.AUTHORIZATION_CODE),
            redirectUris = setOf("https://client.example/callback"),
            scopes = setOf("openid", "profile"),
            tokenSettings = ClientTokenSettings(accessTokenTimeToLive = Duration.ofMinutes(12)),
        )

        assertThat(created.details)
            .usingRecursiveComparison()
            .ignoringCollectionOrder()
            .isEqualTo(expectedDetails)
        assertThat(created.clientSecret).isNotBlank()
        assertThat(passwordEncoder.matches(created.clientSecret, savedClient.clientSecret)).isTrue()
    }

    @Test
    fun `does not generate a secret for a public client`() {
        val created = clientService.createClient(
            CreateClientRequest(
                clientName = "Public client",
                clientAuthenticationMethods = setOf(ClientAuthenticationMethod.NONE),
                authorizationGrantTypes = setOf(ClientAuthorizationGrantType.AUTHORIZATION_CODE),
                redirectUris = setOf("https://client.example/callback"),
            )
        )

        verify(registeredClientRepository).save(registeredClientCaptor.capture())
        assertThat(created).hasFieldOrPropertyWithValue("clientSecret", null)
        assertThat(registeredClientCaptor.singleValue).hasFieldOrPropertyWithValue("clientSecret", null)
    }

    @Test
    fun `rejects unregistered scopes when creating a client`() {
        assertThatThrownBy {
            clientService.createClient(
                CreateClientRequest(
                    clientName = "Invalid client",
                    clientAuthenticationMethods = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
                    authorizationGrantTypes = setOf(ClientAuthorizationGrantType.CLIENT_CREDENTIALS),
                    scopes = setOf("not:registered"),
                )
            )
        }.isInstanceOf(ClientScopeNotRegisteredException::class.java)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not:registered")

        verify(registeredClientRepository, never()).save(any())
    }

    @Test
    fun `applies shared protocol configuration checks when creating a client`() {
        assertThatThrownBy {
            clientService.createClient(
                CreateClientRequest(
                    clientName = "Invalid public client",
                    clientAuthenticationMethods = setOf(
                        ClientAuthenticationMethod.NONE,
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                    ),
                    authorizationGrantTypes = setOf(ClientAuthorizationGrantType.AUTHORIZATION_CODE),
                    redirectUris = setOf("https://client.example/callback"),
                )
            )
        }.isInstanceOf(InvalidClientConfigurationException::class.java)

        verify(registeredClientRepository, never()).save(any())
    }

    @Test
    fun `finds client details by client id`() {
        doReturn(existingRegisteredClient)
            .whenever(registeredClientRepository)
            .findByClientId("existing-client")

        val found = clientService.findClient("existing-client")
        val expected = ClientDetails(
            clientId = "existing-client",
            clientName = "Existing client",
            clientAuthenticationMethods = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
            authorizationGrantTypes = setOf(ClientAuthorizationGrantType.AUTHORIZATION_CODE),
            redirectUris = setOf("https://client.example/callback"),
            scopes = setOf("profile"),
            tokenSettings = ClientTokenSettings(
                authorizationCodeTimeToLive = Duration.ofMinutes(3),
                accessTokenTimeToLive = Duration.ofMinutes(7),
            ),
        )

        assertThat(found)
            .usingRecursiveComparison()
            .ignoringCollectionOrder()
            .isEqualTo(expected)
    }

    @Test
    fun `throws when finding an unknown client`() {
        assertThatThrownBy { clientService.findClient("missing-client") }
            .isInstanceOf(ClientNotFoundException::class.java)
    }

    @Test
    fun `deletes a registered client by its internal id`() {
        doReturn(existingRegisteredClient)
            .whenever(registeredClientRepository)
            .findByClientId("existing-client")

        clientService.deleteClient("existing-client")

        verify(registeredClientRepository).deleteById(existingRegisteredClient.id)
    }

    @Test
    fun `throws when deleting an unknown client`() {
        assertThatThrownBy { clientService.deleteClient("missing-client") }
            .isInstanceOf(ClientNotFoundException::class.java)

        verify(registeredClientRepository, never()).deleteById(any())
    }

    @Test
    fun `updates client metadata and applies set and token deltas`() {
        doReturn(existingRegisteredClient)
            .whenever(registeredClientRepository)
            .findByClientId("existing-client")

        val updated = clientService.updateClient(
            "existing-client",
            UpdateClientRequest(
                clientName = "Renamed client",
                clientAuthenticationMethods = SetChanges(
                    add = setOf(ClientAuthenticationMethod.CLIENT_SECRET_POST),
                ),
                authorizationGrantTypes = SetChanges(
                    add = setOf(ClientAuthorizationGrantType.REFRESH_TOKEN),
                ),
                redirectUris = SetChanges(
                    add = setOf("https://client.example/new-callback"),
                    remove = setOf("https://client.example/callback"),
                ),
                scopes = SetChanges(add = setOf("email"), remove = setOf("profile")),
                tokenSettings = ClientTokenSettingsRequest(
                    accessTokenTimeToLive = Duration.ofMinutes(20),
                    reuseRefreshTokens = false,
                ),
            )
        )
        verify(registeredClientRepository).save(registeredClientCaptor.capture())
        val savedClient = registeredClientCaptor.singleValue
        val expected = ClientDetails(
            clientId = "existing-client",
            clientName = "Renamed client",
            clientAuthenticationMethods = setOf(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                ClientAuthenticationMethod.CLIENT_SECRET_POST,
            ),
            authorizationGrantTypes = setOf(
                ClientAuthorizationGrantType.AUTHORIZATION_CODE,
                ClientAuthorizationGrantType.REFRESH_TOKEN,
            ),
            redirectUris = setOf("https://client.example/new-callback"),
            scopes = setOf("email"),
            tokenSettings = ClientTokenSettings(
                authorizationCodeTimeToLive = Duration.ofMinutes(3),
                accessTokenTimeToLive = Duration.ofMinutes(20),
                reuseRefreshTokens = false,
            ),
        )

        assertThat(updated)
            .usingRecursiveComparison()
            .ignoringCollectionOrder()
            .isEqualTo(expected)
        assertThat(savedClient)
            .usingRecursiveComparison()
            .comparingOnlyFields("id", "clientSecret")
            .isEqualTo(existingRegisteredClient)
    }

    @Test
    fun `rejects unregistered scopes when updating a client`() {
        doReturn(existingRegisteredClient)
            .whenever(registeredClientRepository)
            .findByClientId("existing-client")

        assertThatThrownBy {
            clientService.updateClient(
                "existing-client",
                UpdateClientRequest(scopes = SetChanges(add = setOf("not:registered"))),
            )
        }.isInstanceOf(ClientScopeNotRegisteredException::class.java)

        verify(registeredClientRepository, never()).save(any())
    }

    @Test
    fun `applies shared client configuration checks when updating a client`() {
        doReturn(existingRegisteredClient)
            .whenever(registeredClientRepository)
            .findByClientId("existing-client")

        assertThatThrownBy {
            clientService.updateClient(
                "existing-client",
                UpdateClientRequest(
                    redirectUris = SetChanges(remove = setOf("https://client.example/callback"))
                ),
            )
        }.isInstanceOf(InvalidClientConfigurationException::class.java)

        verify(registeredClientRepository, never()).save(any())
    }

    private fun existingClient() = RegisteredClient.withId("internal-existing-id")
        .clientId("existing-client")
        .clientIdIssuedAt(Instant.parse("2026-01-01T00:00:00Z"))
        .clientSecret("encoded-existing-secret")
        .clientName("Existing client")
        .clientAuthenticationMethod(SpringClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://client.example/callback")
        .scope("profile")
        .tokenSettings(
            TokenSettings.builder()
                .authorizationCodeTimeToLive(Duration.ofMinutes(3))
                .accessTokenTimeToLive(Duration.ofMinutes(7))
                .build()
        )
        .build()

    private companion object {
        val REGISTERED_SCOPES = setOf("openid", "profile", "email")
    }
}
