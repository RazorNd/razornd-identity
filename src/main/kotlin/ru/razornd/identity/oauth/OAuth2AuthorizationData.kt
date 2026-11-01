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

import org.springframework.security.core.Authentication
import org.springframework.security.jackson.SecurityJacksonModules
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Component
import ru.razornd.identity.oauth.AuthorizationTokenData.Kind
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.Principal
import java.time.Instant

/** JSON-serializable snapshot of an authorization, with its registered client referenced by ID. */
data class OAuth2AuthorizationData(
    val id: String,
    val registeredClientId: String,
    val principalName: String,
    val authorizationGrantType: String,
    val authorizedScopes: Set<String>,
    val attributes: Map<String, Any> = emptyMap(),
    val principal: JsonNode? = null,
    val authorizationRequest: AuthorizationRequestData? = null,
    val tokens: List<AuthorizationTokenData> = emptyList(),
)

/** JSON-serializable representation of an OAuth 2.0 authorization request. */
data class AuthorizationRequestData(
    val authorizationUri: String,
    val clientId: String,
    val redirectUri: String? = null,
    val scopes: Set<String> = emptySet(),
    val state: String? = null,
    val additionalParameters: Map<String, Any> = emptyMap(),
    val authorizationRequestUri: String,
    val attributes: Map<String, Any> = emptyMap(),
)

/** JSON-serializable representation of an authorization token and its metadata. */
data class AuthorizationTokenData(
    val kind: Kind,
    val value: String,
    val issuedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val tokenType: String? = null,
    val scopes: Set<String> = emptySet(),
    val claims: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap(),
) {
    enum class Kind {
        ACCESS,
        REFRESH,
        AUTHORIZATION_CODE,
        ID,
        DEVICE_CODE,
        USER_CODE,
    }
}

/** Converts between Spring Authorization Server's model and [OAuth2AuthorizationData]. */
@Component
class OAuth2AuthorizationDataMapper(private val registeredClients: RegisteredClientRepository) {

    private val principalMapper = JsonMapper.builder()
        .addModules(SecurityJacksonModules.getModules(javaClass.classLoader))
        .build()

    /** Converts a Spring authorization to its Redis representation. */
    fun toData(authorization: OAuth2Authorization) = OAuth2AuthorizationData(
        id = authorization.id,
        registeredClientId = authorization.registeredClientId,
        principalName = authorization.principalName,
        authorizationGrantType = authorization.authorizationGrantType.value,
        authorizedScopes = authorization.authorizedScopes,
        attributes = authorization.attributes.filterKeys { it !in PROCESSED_ATTRIBUTES },
        principal = authorization.principal?.let { principalMapper.valueToTree(it) },
        authorizationRequest = authorization.authorizationRequestData,
        tokens = authorization.toTokensData(),
    )

    /** Reconstructs a Spring authorization, resolving its registered client by ID. */
    fun toAuthorization(data: OAuth2AuthorizationData): OAuth2Authorization {
        val client = checkNotNull(registeredClients.findById(data.registeredClientId)) {
            "Registered client '${data.registeredClientId}' was not found"
        }

        return OAuth2Authorization.withRegisteredClient(client)
            .id(data.id)
            .principalName(data.principalName)
            .authorizationGrantType(AuthorizationGrantType(data.authorizationGrantType))
            .authorizedScopes(data.authorizedScopes).attributes { attributes -> attributes.putAll(data.attributes) }
            .principal(data.principal)
            .authorizationRequest(data.authorizationRequest)
            .also { data.tokens.forEach { token -> it.addToken(token) } }
            .build()
    }

    private fun OAuth2Authorization.toTokensData() = sequenceOf(
        OAuth2AccessToken::class to Kind.ACCESS,
        OAuth2RefreshToken::class to Kind.REFRESH,
        OAuth2AuthorizationCode::class to Kind.AUTHORIZATION_CODE,
        OidcIdToken::class to Kind.ID,
        OAuth2DeviceCode::class to Kind.DEVICE_CODE,
        OAuth2UserCode::class to Kind.USER_CODE,
    )
        .mapNotNull { (type, kind) -> getToken(type.java)?.toData(kind) }
        .toList()

    private fun <T : OAuth2Token> OAuth2Authorization.Token<T>.toData(
        kind: Kind,
    ) = AuthorizationTokenData(
        kind = kind,
        value = token.tokenValue,
        issuedAt = token.issuedAt,
        expiresAt = token.expiresAt,
        scopes = (token as? OAuth2AccessToken)?.scopes.orEmpty(),
        tokenType = (token as? OAuth2AccessToken)?.tokenType?.value,
        claims = (token as? OidcIdToken)?.claims.orEmpty(),
        metadata = metadata,
    )

    private fun OAuth2AuthorizationRequest.toData() = AuthorizationRequestData(
        authorizationUri = authorizationUri,
        clientId = clientId,
        redirectUri = redirectUri,
        scopes = scopes,
        state = state,
        additionalParameters = additionalParameters,
        authorizationRequestUri = authorizationRequestUri,
        attributes = attributes,
    )

    private fun AuthorizationRequestData.toAuthorizationRequest() = OAuth2AuthorizationRequest
        .authorizationCode()
        .authorizationUri(authorizationUri)
        .clientId(clientId)
        .redirectUri(redirectUri)
        .scopes(scopes)
        .state(state)
        .additionalParameters(additionalParameters)
        .authorizationRequestUri(authorizationRequestUri)
        .attributes(attributes)
        .build()

    private fun OAuth2Authorization.Builder.principal(
        jsonNode: JsonNode?
    ) = if (jsonNode == null) this else attribute(
        PRINCIPAL_ATTRIBUTE,
        principalMapper.treeToValue(jsonNode, Authentication::class.java),
    )

    private fun OAuth2Authorization.Builder.authorizationRequest(
        requestData: AuthorizationRequestData?
    ) = if (requestData == null) this else attribute(
        AUTHORIZATION_REQUEST_ATTRIBUTE,
        requestData.toAuthorizationRequest()
    )

    private fun OAuth2Authorization.Builder.addToken(data: AuthorizationTokenData) {
        val token: OAuth2Token = when (data.kind) {
            Kind.ACCESS -> OAuth2AccessToken(
                when (data.tokenType) {
                    OAuth2AccessToken.TokenType.DPOP.value -> OAuth2AccessToken.TokenType.DPOP
                    else -> OAuth2AccessToken.TokenType.BEARER
                },
                data.value,
                data.issuedAt,
                data.expiresAt,
                data.scopes,
            )

            Kind.REFRESH -> OAuth2RefreshToken(data.value, data.issuedAt, data.expiresAt)
            Kind.AUTHORIZATION_CODE -> OAuth2AuthorizationCode(
                data.value,
                requireNotNull(data.issuedAt),
                requireNotNull(data.expiresAt),
            )

            Kind.ID -> OidcIdToken(
                data.value,
                data.issuedAt,
                data.expiresAt,
                data.claims,
            )

            Kind.DEVICE_CODE -> OAuth2DeviceCode(
                data.value,
                requireNotNull(data.issuedAt),
                requireNotNull(data.expiresAt),
            )

            Kind.USER_CODE -> OAuth2UserCode(
                data.value,
                requireNotNull(data.issuedAt),
                requireNotNull(data.expiresAt),
            )
        }
        token(token) { metadata ->
            metadata.putAll(data.metadata)
        }
    }

    private val OAuth2Authorization.principal get() = getAttribute<Any>(PRINCIPAL_ATTRIBUTE) as? Authentication

    private val OAuth2Authorization.authorizationRequestData
        get() = getAttribute<OAuth2AuthorizationRequest>(AUTHORIZATION_REQUEST_ATTRIBUTE)?.toData()

    private companion object {
        val PRINCIPAL_ATTRIBUTE: String = Principal::class.java.name
        val AUTHORIZATION_REQUEST_ATTRIBUTE: String = OAuth2AuthorizationRequest::class.java.name

        val PROCESSED_ATTRIBUTES: Set<String> = setOf(
            PRINCIPAL_ATTRIBUTE,
            AUTHORIZATION_REQUEST_ATTRIBUTE,
        )
    }
}
