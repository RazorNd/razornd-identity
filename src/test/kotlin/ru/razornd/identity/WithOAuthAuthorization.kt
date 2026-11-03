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

package ru.razornd.identity

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.springframework.beans.factory.getBean
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.*

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(WithOAuthAuthorizationParameterResolver::class)
annotation class WithOAuthAuthorization(
    val registeredClientId: String = "test-client",
    val authorizationId: String = "",
    val principalName: String = "test-user",
    val principalCredentials: String = "N/A",
    val authorizationGrantType: String = "authorization_code",
    val authorizedScopes: Array<String> = ["openid", "profile"],
    val accessTokenValue: String = "test-access-token",
    val createAccessToken: Boolean = false,
    val refreshTokenValue: String = "test-refresh-token",
    val idTokenValue: String = "test-id-token",
    val idTokenSubject: String = "user",
    val accessTokenTtlSeconds: Long = 600,
    val refreshTokenTtlSeconds: Long = 7200,
)

/** Resolves a parameter to an authorization fixture saved in the application authorization service. */
class WithOAuthAuthorizationParameterResolver : ParameterResolver {
    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        val hasOAuthAuthorizationAnnotation =
            extensionContext.requiredTestMethod.isAnnotationPresent(WithOAuthAuthorization::class.java)
        val parameterType = parameterContext.parameter.type
        val isAuthorization = OAuth2Authorization::class.java.isAssignableFrom(parameterType)
        val isOAuth2Token = OAuth2Token::class.java.isAssignableFrom(parameterType)
        return hasOAuthAuthorizationAnnotation && (isAuthorization || isOAuth2Token)

    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        val annotation = extensionContext.requiredTestMethod.getAnnotation(WithOAuthAuthorization::class.java)
        val authorizationService = SpringExtension.getApplicationContext(extensionContext)
            .getBean<OAuth2AuthorizationService>()

        val authorization = createAuthorization(annotation, extensionContext).also {
            authorizationService.save(it)
        }

        val persistedAuthorization = checkNotNull(authorizationService.findById(authorization.id)) {
            "OAuth2 authorization '${authorization.id}' was not persisted"
        }
        return resolveValue(persistedAuthorization, parameterContext.parameter.type)
    }

    private fun createAuthorization(
        annotation: WithOAuthAuthorization,
        extensionContext: ExtensionContext,
    ): OAuth2Authorization {
        val registeredClient = registeredClient(annotation, extensionContext)
        val now = Instant.now()

        val (accessToken, accessTokenClaims) = createAccessToken(annotation, now, extensionContext)

        return OAuth2Authorization.withRegisteredClient(registeredClient)
            .id(annotation.authorizationId.ifBlank { UUID.randomUUID().toString() })
            .principalName(annotation.principalName)
            .authorizationGrantType(AuthorizationGrantType(annotation.authorizationGrantType))
            .authorizedScopes(annotation.authorizedScopes.toSet())
            .attribute(Principal::class.java.name, createPrincipal(annotation))
            .token(accessToken) { metadata ->
                if (accessTokenClaims.isNotEmpty()) {
                    metadata[OAuth2Authorization.Token.CLAIMS_METADATA_NAME] = accessTokenClaims
                }
            }
            .refreshToken(createRefreshToken(annotation, now))
            .token(createIdToken(annotation))
            .build()
    }

    private fun registeredClient(
        annotation: WithOAuthAuthorization,
        extensionContext: ExtensionContext,
    ) = checkNotNull(
        SpringExtension.getApplicationContext(extensionContext)
            .getBean<RegisteredClientRepository>()
            .findByClientId(annotation.registeredClientId)
    ) {
        "Registered OAuth2 client '${annotation.registeredClientId}' is not configured"
    }

    private fun createAccessToken(
        annotation: WithOAuthAuthorization,
        issuedAt: Instant,
        extensionContext: ExtensionContext,
    ): Pair<OAuth2AccessToken, Map<String, Any>> {
        val expiresAt = issuedAt.plus(Duration.ofSeconds(annotation.accessTokenTtlSeconds))
        val claims = if (annotation.createAccessToken) {
            JwtClaimsSet.builder()
                .subject(annotation.principalName)
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("scope", annotation.authorizedScopes.joinToString(" "))
                .claim("aud", listOf(annotation.registeredClientId))
                .build()
        } else {
            null
        }
        val tokenValue = claims?.let {
            val jwtEncoder = SpringExtension.getApplicationContext(extensionContext).getBean<JwtEncoder>()
            jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), it)
            ).tokenValue
        } ?: annotation.accessTokenValue

        return OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            tokenValue,
            issuedAt,
            expiresAt,
            annotation.authorizedScopes.toSet(),
        ) to claims?.claims.orEmpty()
    }

    private fun createRefreshToken(annotation: WithOAuthAuthorization, issuedAt: Instant) = OAuth2RefreshToken(
        annotation.refreshTokenValue,
        issuedAt,
        issuedAt.plus(Duration.ofSeconds(annotation.refreshTokenTtlSeconds))
    )

    private fun createIdToken(annotation: WithOAuthAuthorization) =
        OidcIdToken.withTokenValue(annotation.idTokenValue).subject(annotation.idTokenSubject).build()

    private fun createPrincipal(annotation: WithOAuthAuthorization) =
        UsernamePasswordAuthenticationToken.authenticated(
            annotation.principalName,
            annotation.principalCredentials,
            emptyList()
        )

    private fun resolveValue(authorization: OAuth2Authorization, parameterType: Class<*>): Any {
        if (OAuth2Authorization::class.java.isAssignableFrom(parameterType)) return authorization
        val tokenType = parameterType.asSubclass(OAuth2Token::class.java)
        val typedToken = checkNotNull(authorization.getToken(tokenType)) {
            "OAuth2 token of type '${parameterType.name}' is not present in the authorization"
        }
        return typedToken.token
    }
}
