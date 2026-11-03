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

package ru.razornd.identity.oauth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.boot.test.json.BasicJsonTester
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.jackson.CoreJacksonModule
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2DeviceCode
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.OAuth2UserCode
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.security.Principal
import java.time.Instant
import java.util.Date

@JsonTest
class OAuth2AuthorizationJsonTests {
    @Autowired
    private lateinit var json: BasicJsonTester

    @Autowired
    private lateinit var mapperBuilder: JsonMapper.Builder

    private lateinit var metadataMapper: JsonMapper
    private lateinit var authorizationDataMapper: OAuth2AuthorizationDataMapper

    @BeforeEach
    fun setUp() {
        val typeValidator = BasicPolymorphicTypeValidator.builder().also {
            CoreJacksonModule().configurePolymorphicTypeValidator(it)
            it.allowIfSubType("kotlin.collections.EmptyMap")
            it.allowIfSubType("kotlin.collections.EmptySet")
            it.allowIfSubType("kotlin.collections.EmptyList")
            it.allowIfSubType("java.net.URL")
        }.build()
        metadataMapper = mapperBuilder
            .activateDefaultTyping(typeValidator, DefaultTyping.JAVA_LANG_OBJECT, JsonTypeInfo.As.PROPERTY)
            .build()
        val client = registeredClient()
        val clients = object : RegisteredClientRepository {
            override fun save(registeredClient: RegisteredClient) = Unit
            override fun findById(id: String) = client.takeIf { id == it.id }
            override fun findByClientId(clientId: String) = client.takeIf { clientId == it.clientId }
        }
        authorizationDataMapper = OAuth2AuthorizationDataMapper(clients)
    }

    @Test
    fun `authorization data serialization matches JSON contract`() {
        val data = authorizationDataMapper.toData(authorization())
        val actual = metadataMapper.writeValueAsBytes(data)
        val expected = json.from("/oauth2-authorization.json", javaClass)

        assertThat(json.from(actual)).isEqualToJson(expected.json)
    }

    @Test
    fun `authorization metadata retains polymorphic types when deserialized`() {
        val data = metadataMapper.readValue<OAuth2AuthorizationData>(
            ClassPathResource("/oauth2-authorization.json").inputStream
        )

        val restored = authorizationDataMapper.toAuthorization(data)
        assertThat(restored).usingRecursiveComparison().isEqualTo(authorization())
    }

    private fun authorization(): OAuth2Authorization {
        val issuedAt = Instant.parse("2026-01-02T03:04:05Z")
        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            issuedAt,
            issuedAt.plusSeconds(600),
            setOf("openid"),
        )
        val refreshToken = OAuth2RefreshToken("refresh-token", issuedAt, issuedAt.plusSeconds(3600))
        val authorizationCode = OAuth2AuthorizationCode("authorization-code", issuedAt, issuedAt.plusSeconds(120))
        val idToken = OidcIdToken.withTokenValue("id-token").subject("debug-user").build()
        val deviceCode = OAuth2DeviceCode("device-code", issuedAt, issuedAt.plusSeconds(600))
        val userCode = OAuth2UserCode("user-code", issuedAt, issuedAt.plusSeconds(600))
        val authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://identity.example/oauth2/authorize")
            .clientId("debug-client")
            .redirectUri("https://client.example/callback")
            .scopes(setOf("openid"))
            .state("request-state")
            .additionalParameters(mapOf("nonce" to "debug-nonce"))
            .authorizationRequestUri("https://identity.example/oauth2/authorize?client_id=debug-client")
            .attributes(mapOf("registration_id" to "test-client"))
            .build()

        return OAuth2Authorization.withRegisteredClient(registeredClient())
            .id("authorization-id")
            .principalName("debug-user")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(setOf("openid"))
            .token(accessToken) { metadata ->
                metadata[OAuth2Authorization.Token.CLAIMS_METADATA_NAME] = linkedMapOf(
                    "nbf" to issuedAt,
                    "auth_time" to Date.from(issuedAt),
                    "iss" to URI("https://identity.example").toURL(),
                )
            }
            .refreshToken(refreshToken)
            .token(authorizationCode)
            .token(idToken)
            .token(deviceCode)
            .token(userCode)
            .attribute("state", "authorization-state")
            .attribute(
                Principal::class.java.name,
                UsernamePasswordAuthenticationToken.authenticated(
                    "debug-user",
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                ),
            )
            .attribute(OAuth2AuthorizationRequest::class.java.name, authorizationRequest)
            .build()
    }

    private fun registeredClient() = RegisteredClient.withId("client-internal-id")
        .clientId("debug-client")
        .clientSecret("{noop}secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://client.example/callback")
        .scope("openid")
        .tokenSettings(TokenSettings.builder().build())
        .build()
}
