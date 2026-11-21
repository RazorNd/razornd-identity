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

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.razornd.identity.oauth.ScopeRegistry
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import org.springframework.security.oauth2.core.ClientAuthenticationMethod as AuthenticationMethod
import org.springframework.security.oauth2.core.ClientAuthenticationMethod.valueOf as authenticationMethod

/** Application operations for managing JDBC-persisted OAuth2 clients through the admin API. */
@Transactional
@Service
class ClientService(
    private val registeredClientManageRepository: RegisteredClientManageRepository,
    private val passwordEncoder: PasswordEncoder,
    private val scopeRegistry: ScopeRegistry,
) {
    /** Registers a client and returns its generated secret for one-time disclosure. */
    @Throws(ClientScopeNotRegisteredException::class, InvalidClientConfigurationException::class)
    fun createClient(request: CreateClientRequest): CreatedClient {
        val clientId = UUID.randomUUID().toString()
        val clientSecret = if (ClientAuthenticationMethod.NONE in request.clientAuthenticationMethods) {
            null
        } else {
            generateClientSecret()
        }
        val builder = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientIdIssuedAt(Instant.now())
            .clientName(request.clientName)
            .applyClientAuthenticationMethods(request.clientAuthenticationMethods)
            .applyAuthorizationGrantTypes(request.authorizationGrantTypes)
            .applyRedirectUris(request.redirectUris)
            .applyScopes(request.scopes)
            .applyTokenSettings(request.tokenSettings)
            .applyClientSecret(clientSecret)

        val registeredClient = builder.buildWithConfigurationCheck()

        registeredClientManageRepository.save(registeredClient)
        return CreatedClient(registeredClient.toClientDetails(), clientSecret)
    }

    /** Finds a client by its public client identifier. */
    @Throws(ClientNotFoundException::class)
    fun findClient(clientId: String): ClientDetails =
        registeredClientManageRepository.findClientOrThrow(clientId).toClientDetails()

    /** Applies the supplied changes, or throws if the client does not exist. */
    @Throws(
        ClientNotFoundException::class,
        ClientScopeNotRegisteredException::class,
        InvalidClientConfigurationException::class,
    )
    fun updateClient(clientId: String, request: UpdateClientRequest): ClientDetails {
        val existing = registeredClientManageRepository.findClientOrThrow(clientId)

        val updated = context(existing) {
            RegisteredClient.from(existing)
                .updateClientName(request.clientName)
                .updateClientAuthenticationMethods(request.clientAuthenticationMethods)
                .updateAuthorizationGrantTypes(request.authorizationGrantTypes)
                .updateRedirectUris(request.redirectUris)
                .updateScopes(request.scopes)
                .updateTokenSettings(request.tokenSettings)
                .buildWithConfigurationCheck()
        }

        registeredClientManageRepository.save(updated)

        return updated.toClientDetails()
    }

    /** Deletes a client by its public identifier. */
    @Throws(ClientNotFoundException::class)
    fun deleteClient(clientId: String) {
        val client = registeredClientManageRepository.findClientOrThrow(clientId)
        registeredClientManageRepository.deleteById(client.id)
    }

    private fun RegisteredClientManageRepository.findClientOrThrow(
        clientId: String
    ): RegisteredClient = findByClientId(clientId) ?: throw ClientNotFoundException(clientId)

    private fun RegisteredClient.Builder.updateClientName(
        name: String?,
    ) = apply { if (name != null) clientName(name) }

    private fun RegisteredClient.Builder.applyClientAuthenticationMethods(
        methods: Set<ClientAuthenticationMethod>,
    ) = updateClientAuthenticationMethods(methods.toAddChanges())

    private fun RegisteredClient.Builder.updateClientAuthenticationMethods(
        changes: SetChanges<ClientAuthenticationMethod>?,
    ) = clientAuthenticationMethods { methods ->
        methods += changes.orEmpty().map { authenticationMethod(it.value) }
        if (AuthenticationMethod.NONE in methods && methods.size > 1) {
            throw InvalidClientConfigurationException(
                "The none client authentication method cannot be combined with other methods.",
            )
        }
    }

    private fun RegisteredClient.Builder.applyAuthorizationGrantTypes(
        grantTypes: Set<ClientAuthorizationGrantType>,
    ) = updateAuthorizationGrantTypes(grantTypes.toAddChanges())

    private fun RegisteredClient.Builder.updateAuthorizationGrantTypes(
        changes: SetChanges<ClientAuthorizationGrantType>?,
    ) = authorizationGrantTypes { registeredGrantTypes ->
        registeredGrantTypes += changes.orEmpty().map { AuthorizationGrantType(it.value) }
    }

    private fun RegisteredClient.Builder.applyRedirectUris(
        redirectUris: Set<String>,
    ) = updateRedirectUris(redirectUris.toAddChanges())

    private fun RegisteredClient.Builder.updateRedirectUris(
        changes: SetChanges<String>?,
    ) = redirectUris { registeredUris ->
        registeredUris += changes.orEmpty()
    }

    private fun RegisteredClient.Builder.applyScopes(
        scopes: Set<String>,
    ) = updateScopes(scopes.toAddChanges())

    private fun RegisteredClient.Builder.updateScopes(
        changes: SetChanges<String>?,
    ) = scopes { registeredScopes ->
        registeredScopes += changes.orEmpty()

        validateScopes(registeredScopes)
    }

    private fun RegisteredClient.Builder.applyClientSecret(rawSecret: String?) = apply {
        passwordEncoder.encode(rawSecret)?.let { clientSecret(it) }
    }

    private fun RegisteredClient.Builder.applyTokenSettings(request: ClientTokenSettingsRequest?) = apply {
        if (request != null) {
            tokenSettings(request.applyTo(TokenSettings.builder()).build())
        }
    }

    context(existing: RegisteredClient)
    private fun RegisteredClient.Builder.updateTokenSettings(
        request: ClientTokenSettingsRequest?,
    ) = apply {
        if (request != null) {
            tokenSettings(request.applyTo(TokenSettings.withSettings(existing.tokenSettings.settings)).build())
        }
    }

    private fun RegisteredClient.Builder.buildWithConfigurationCheck() = try {
        build()
    } catch (exception: IllegalArgumentException) {
        throw InvalidClientConfigurationException(exception.message ?: "Invalid client configuration.", exception)
    }

    private fun validateScopes(registeredScopes: Set<String>) {
        try {
            scopeRegistry.requireAll(registeredScopes)
        } catch (e: IllegalArgumentException) {
            throw ClientScopeNotRegisteredException(e)
        }
    }

    private fun RegisteredClient.toClientDetails() = ClientDetails(
        clientId = clientId,
        clientName = clientName,
        clientAuthenticationMethods = clientAuthenticationMethods.mapTo(HashSet()) {
            ClientAuthenticationMethod.fromValue(it.value)
        },
        authorizationGrantTypes = authorizationGrantTypes.mapTo(HashSet()) {
            ClientAuthorizationGrantType.fromValue(it.value)
        },
        redirectUris = redirectUris,
        scopes = scopes,
        tokenSettings = tokenSettings.toClientTokenSettings(),
    )

    private fun TokenSettings.toClientTokenSettings() = ClientTokenSettings(
        authorizationCodeTimeToLive = authorizationCodeTimeToLive,
        accessTokenTimeToLive = accessTokenTimeToLive,
        accessTokenFormat = ClientAccessTokenFormat.fromValue(accessTokenFormat.value),
        reuseRefreshTokens = isReuseRefreshTokens,
        refreshTokenTimeToLive = refreshTokenTimeToLive,
    )

    fun ClientTokenSettingsRequest.applyTo(builder: TokenSettings.Builder): TokenSettings.Builder {
        authorizationCodeTimeToLive?.let(builder::authorizationCodeTimeToLive)
        accessTokenTimeToLive?.let(builder::accessTokenTimeToLive)
        accessTokenFormat?.toSpringFormat()?.let(builder::accessTokenFormat)
        reuseRefreshTokens?.let(builder::reuseRefreshTokens)
        refreshTokenTimeToLive?.let(builder::refreshTokenTimeToLive)
        return builder
    }

    private fun generateClientSecret(): String {
        val bytes = ByteArray(CLIENT_SECRET_BYTES).also(secureRandom::nextBytes)
        return urlEncoder.encodeToString(bytes)
    }

    private companion object {
        const val CLIENT_SECRET_BYTES = 32
        val secureRandom = SecureRandom()
        val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

/** Public client metadata held by the application service. */
data class ClientDetails(
    val clientId: String,
    val clientName: String,
    val clientAuthenticationMethods: Set<ClientAuthenticationMethod>,
    val authorizationGrantTypes: Set<ClientAuthorizationGrantType>,
    val redirectUris: Set<String>,
    val scopes: Set<String>,
    val tokenSettings: ClientTokenSettings,
)

/** A newly registered client and its one-time secret. */
data class CreatedClient(
    val details: ClientDetails,
    val clientSecret: String?,
)

/** Raised when the requested registered client does not exist. */
class ClientNotFoundException(clientId: String) :
    RuntimeException("OAuth2 client '$clientId' was not found.")

/** Raised when a client registration references a scope absent from the server catalog. */
class ClientScopeNotRegisteredException(cause: Exception?) : RuntimeException(cause)

/** Raised when a requested update would leave a client with invalid protocol settings. */
class InvalidClientConfigurationException(message: String, cause: Exception? = null) : RuntimeException(message, cause)
