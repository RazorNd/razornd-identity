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

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration

/** Configures server-owned in-memory clients and API-managed JDBC clients. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegisteredClientProperties::class)
class RegisteredClientRepositoryConfiguration {
    @Bean
    fun inMemoryRegisteredClientRepository(
        properties: RegisteredClientProperties,
    ): RegisteredClientRepository = InMemoryRegisteredClientRepository(
        properties.registeredClients() + razorndIdentityCliClient()
    )

    @Bean
    fun jdbcRegisteredClientRepository(
        jdbcOperations: JdbcOperations,
    ): RegisteredClientManageRepository = JdbcRegisteredClientManageRepository(jdbcOperations)

    @Bean
    @Primary
    fun registeredClientRepository(
        @Qualifier("inMemoryRegisteredClientRepository")
        inMemoryRegisteredClientRepository: RegisteredClientRepository,
        @Qualifier("jdbcRegisteredClientRepository")
        jdbcRegisteredClientRepository: RegisteredClientManageRepository,
    ) = CompositeRegisteredClientRepository(inMemoryRegisteredClientRepository, jdbcRegisteredClientRepository)

    private fun RegisteredClientProperties.registeredClients(): List<RegisteredClient> =
        clients.map { (id, client) -> client.toRegisteredClient(id) }

    private fun RegisteredClientProperties.Client.toRegisteredClient(id: String): RegisteredClient {
        val builder = RegisteredClient.withId(id)
            .clientId(clientId)
            .clientName(clientName ?: clientId)

        clientSecret?.let(builder::clientSecret)
        clientAuthenticationMethods.map(::ClientAuthenticationMethod).forEach(builder::clientAuthenticationMethod)
        authorizationGrantTypes.map(::AuthorizationGrantType).forEach(builder::authorizationGrantType)
        redirectUris.forEach(builder::redirectUri)
        postLogoutRedirectUris.forEach(builder::postLogoutRedirectUri)
        scopes.forEach(builder::scope)

        return builder
            .clientSettings(toClientSettings())
            .tokenSettings(toTokenSettings())
            .build()
    }

    private fun RegisteredClientProperties.Client.toClientSettings(): ClientSettings {
        val builder = ClientSettings.builder()
        requireProofKey?.let(builder::requireProofKey)
        requireAuthorizationConsent?.let(builder::requireAuthorizationConsent)
        return builder.build()
    }

    private fun RegisteredClientProperties.Client.toTokenSettings(): TokenSettings {
        val builder = TokenSettings.builder()
        authorizationCodeTimeToLive?.let(builder::authorizationCodeTimeToLive)
        accessTokenTimeToLive?.let(builder::accessTokenTimeToLive)
        accessTokenFormat?.let { builder.accessTokenFormat(OAuth2TokenFormat(it)) }
        deviceCodeTimeToLive?.let(builder::deviceCodeTimeToLive)
        reuseRefreshTokens?.let(builder::reuseRefreshTokens)
        refreshTokenTimeToLive?.let(builder::refreshTokenTimeToLive)
        return builder.build()
    }

    private fun razorndIdentityCliClient(): RegisteredClient = RegisteredClient.withId("razornd-identity-cli")
        .clientId("razornd-identity-cli")
        .clientName("RazorNd Identity CLI")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://127.0.0.1:0/oauth2/callback")
        .scope("openid")
        .scope("profile")
        .scope("accounts:admin")
        .scope("clients:admin")
        .clientSettings(
            ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(true)
                .build()
        )
        .tokenSettings(
            TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(6))
                .build()
        )
        .build()
}
