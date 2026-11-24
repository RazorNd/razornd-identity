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

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpMethod.valueOf
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.*
import ru.razornd.identity.SecurityConfiguration
import ru.razornd.identity.oauth.ScopeRegistry
import java.time.Duration

@Import(SecurityConfiguration::class)
@ImportAutoConfiguration(
    UserDetailsServiceAutoConfiguration::class,
    OAuth2AuthorizationServerJwtAutoConfiguration::class,
    OAuth2AuthorizationServerAutoConfiguration::class,
)
@MockitoBean(types = [UserDetailsService::class, RegisteredClientRepository::class, ScopeRegistry::class])
@WebMvcTest(AdminClientController::class)
class AdminClientControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var clientService: ClientService

    private val adminAuthorities = setOf(
        SimpleGrantedAuthority("ROLE_ADMIN"),
        SimpleGrantedAuthority("SCOPE_clients:admin"),
    )
    private val clientDetails = ClientDetails(
        clientId = "client-123",
        clientName = "Example client",
        clientAuthenticationMethods = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
        authorizationGrantTypes = setOf(
            ClientAuthorizationGrantType.AUTHORIZATION_CODE,
            ClientAuthorizationGrantType.REFRESH_TOKEN,
        ),
        redirectUris = setOf("https://example.com/callback"),
        scopes = setOf("openid", "profile"),
        tokenSettings = ClientTokenSettings(),
    )

    @Test
    fun `creates a client and returns generated secret once`() {
        val createdDetails = clientDetails.copy(
            tokenSettings = ClientTokenSettings(
                authorizationCodeTimeToLive = Duration.ofMinutes(2),
                accessTokenTimeToLive = Duration.ofMinutes(10),
                accessTokenFormat = ClientAccessTokenFormat.REFERENCE,
                reuseRefreshTokens = false,
                refreshTokenTimeToLive = Duration.ofHours(12),
            )
        )
        doReturn(CreatedClient(createdDetails, "generated-secret"))
            .whenever(clientService).createClient(any())

        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "clientName": "Example client",
                  "clientAuthenticationMethods": ["client_secret_basic"],
                  "authorizationGrantTypes": ["authorization_code", "refresh_token"],
                  "redirectUris": ["https://example.com/callback"],
                  "scopes": ["openid", "profile"],
                  "tokenSettings": {
                    "authorizationCodeTimeToLive": "PT2M",
                    "accessTokenTimeToLive": "PT10M",
                    "accessTokenFormat": "reference",
                    "reuseRefreshTokens": false,
                    "refreshTokenTimeToLive": "PT12H"
                  }
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isCreated() }
            header { string("Location", "http://localhost/api/admin/clients/client-123") }
            jsonPath("$.clientId") { value("client-123") }
            jsonPath("$.clientSecret") { value("generated-secret") }
            jsonPath("$.clientName") { value("Example client") }
            jsonPath("$.clientAuthenticationMethods[0]") { value("client_secret_basic") }
            jsonPath("$.authorizationGrantTypes[0]") { value("authorization_code") }
            jsonPath("$.redirectUris[0]") { value("https://example.com/callback") }
            jsonPath("$.scopes[0]") { value("openid") }
            jsonPath("$.tokenSettings.authorizationCodeTimeToLive") { value("PT2M") }
            jsonPath("$.tokenSettings.accessTokenTimeToLive") { value("PT10M") }
            jsonPath("$.tokenSettings.accessTokenFormat") { value("reference") }
            jsonPath("$.tokenSettings.reuseRefreshTokens") { value(false) }
            jsonPath("$.tokenSettings.refreshTokenTimeToLive") { value("PT12H") }
        }

        verify(clientService).createClient(
            CreateClientRequest(
                clientName = "Example client",
                clientAuthenticationMethods = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
                authorizationGrantTypes = setOf(
                    ClientAuthorizationGrantType.AUTHORIZATION_CODE,
                    ClientAuthorizationGrantType.REFRESH_TOKEN,
                ),
                redirectUris = setOf("https://example.com/callback"),
                scopes = setOf("openid", "profile"),
                tokenSettings = ClientTokenSettingsRequest(
                    authorizationCodeTimeToLive = Duration.ofMinutes(2),
                    accessTokenTimeToLive = Duration.ofMinutes(10),
                    accessTokenFormat = ClientAccessTokenFormat.REFERENCE,
                    reuseRefreshTokens = false,
                    refreshTokenTimeToLive = Duration.ofHours(12),
                ),
            )
        )
    }

    @Test
    fun `returns conflict when client identifier already exists`() {
        doThrow(DuplicateKeyException("Client already exists"))
            .whenever(clientService).createClient(any())

        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "clientName": "Example client",
                  "clientAuthenticationMethods": ["client_secret_basic"],
                  "authorizationGrantTypes": ["client_credentials"]
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `returns bad request when client configuration is invalid during creation`() {
        doThrow(InvalidClientConfigurationException("Invalid client configuration."))
            .whenever(clientService).createClient(any())

        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "clientName": "Example client",
                  "clientAuthenticationMethods": ["client_secret_basic"],
                  "authorizationGrantTypes": ["client_credentials"]
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `gets a client without exposing its secret`() {
        doReturn(clientDetails).whenever(clientService).findClient(any())

        mockMvc.get("/api/admin/clients/client-123") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            jsonPath("$.clientId") { value("client-123") }
            jsonPath("$.clientName") { value("Example client") }
            jsonPath("$.clientSecret") { doesNotExist() }
        }

        verify(clientService).findClient("client-123")
    }

    @Test
    fun `returns not found when client does not exist`() {
        doThrow(ClientNotFoundException("missing-client"))
            .whenever(clientService).findClient(any())

        mockMvc.get("/api/admin/clients/missing-client") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isNotFound() }
        }

        verify(clientService).findClient("missing-client")
    }

    @Test
    fun `patches client metadata`() {
        doReturn(clientDetails).whenever(clientService).updateClient(any(), any())

        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":"Example client"}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            jsonPath("$.clientId") { value("client-123") }
            jsonPath("$.clientName") { value("Example client") }
        }

        verify(clientService).updateClient("client-123", UpdateClientRequest(clientName = "Example client"))
    }

    @Test
    fun `patches set values and token settings without replacing whole sets`() {
        doReturn(clientDetails).whenever(clientService).updateClient(any(), any())

        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "scopes": {
                    "add": ["email"],
                    "remove": ["profile"]
                  },
                  "redirectUris": {
                    "add": ["https://example.com/new-callback"]
                  },
                  "tokenSettings": {
                    "accessTokenTimeToLive": "PT15M",
                    "reuseRefreshTokens": false
                  }
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
        }

        verify(clientService).updateClient(
            "client-123",
            UpdateClientRequest(
                scopes = SetChanges(add = setOf("email"), remove = setOf("profile")),
                redirectUris = SetChanges(add = setOf("https://example.com/new-callback")),
                tokenSettings = ClientTokenSettingsRequest(
                    accessTokenTimeToLive = Duration.ofMinutes(15),
                    reuseRefreshTokens = false,
                ),
            ),
        )
    }

    @Test
    fun `returns not found when updating a missing client`() {
        doThrow(ClientNotFoundException("missing-client"))
            .whenever(clientService).updateClient(any(), any())

        mockMvc.patch("/api/admin/clients/missing-client") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":"Updated name"}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `returns bad request when client configuration is invalid during update`() {
        doThrow(InvalidClientConfigurationException("Invalid client configuration."))
            .whenever(clientService).updateClient(any(), any())

        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":"Updated name"}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `rejects an empty patch`() {
        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `delete removes a client`() {
        doNothing().whenever(clientService).deleteClient(any())

        mockMvc.delete("/api/admin/clients/client-123") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isNoContent() }
        }

        verify(clientService).deleteClient("client-123")
    }

    @Test
    fun `delete returns not found for an unknown client`() {
        doThrow(ClientNotFoundException("missing-client"))
            .whenever(clientService).deleteClient(any())

        mockMvc.delete("/api/admin/clients/missing-client") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `does not create a session for an API request`() {
        doReturn(clientDetails).whenever(clientService).findClient(any())

        val result = mockMvc.get("/api/admin/clients/client-123") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            cookie { doesNotExist("SESSION") }
        }.andReturn()

        assertNull(result.request.getSession(false))
    }

    @ParameterizedTest(name = "{0} {1} without JWT returns 401")
    @CsvSource(
        "POST, /api/admin/clients",
        "GET, /api/admin/clients/client-123",
        "PATCH, /api/admin/clients/client-123",
        "DELETE, /api/admin/clients/client-123",
    )
    fun `rejects unauthenticated requests`(method: String, uri: String) {
        mockMvc.request(valueOf(method), uri) {}.andExpect {
            status { isUnauthorized() }
        }
    }

    @ParameterizedTest(name = "{0} {1} with non-admin JWT returns 403")
    @CsvSource(
        "POST, /api/admin/clients",
        "GET, /api/admin/clients/client-123",
        "PATCH, /api/admin/clients/client-123",
        "DELETE, /api/admin/clients/client-123",
    )
    fun `rejects authenticated non-admin requests`(method: String, uri: String) {
        mockMvc.request(valueOf(method), uri) {
            with(jwt().authorities(setOf(SimpleGrantedAuthority("ROLE_USER"))))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `rejects admin requests without the clients admin scope`() {
        mockMvc.get("/api/admin/clients/client-123") {
            with(jwt().authorities(setOf(SimpleGrantedAuthority("ROLE_ADMIN"))))
        }.andExpect {
            status { isForbidden() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `returns bad request when registration contains an unregistered scope`() {
        doThrow(ClientScopeNotRegisteredException(IllegalArgumentException("unknown:scope")))
            .whenever(clientService).createClient(any())

        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "clientName": "Example client",
                  "clientAuthenticationMethods": ["client_secret_basic"],
                  "authorizationGrantTypes": ["client_credentials"],
                  "scopes": ["unknown:scope"]
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `returns bad request when update contains an unregistered scope`() {
        doThrow(ClientScopeNotRegisteredException(IllegalArgumentException("unknown:scope")))
            .whenever(clientService).updateClient(any(), any())

        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"scopes":{"add":["unknown:scope"]}}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `rejects a value being both added and removed`() {
        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"scopes":{"add":["openid"],"remove":["openid"]}}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `rejects non-positive token lifetimes`() {
        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "clientName": "Example client",
                  "clientAuthenticationMethods": ["client_secret_basic"],
                  "authorizationGrantTypes": ["client_credentials"],
                  "tokenSettings": {
                    "accessTokenTimeToLive": "PT0S"
                  }
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `rejects non-positive token lifetime updates`() {
        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"tokenSettings":{"refreshTokenTimeToLive":"-PT1M"}}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `rejects invalid creation requests`() {
        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":" "}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `rejects invalid update requests`() {
        mockMvc.patch("/api/admin/clients/client-123") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":" "}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `rejects unsupported authentication method`() {
        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":"Example client","clientAuthenticationMethods":["unsupported"]}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }

    @Test
    fun `rejects unsupported grant type`() {
        mockMvc.post("/api/admin/clients") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clientName":"Example client","authorizationGrantTypes":["unsupported"]}"""
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(clientService)
    }
}
