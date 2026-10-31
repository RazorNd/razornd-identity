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

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Redis-backed authorization store with secondary token-to-authorization keys.
 *
 * Token indexes point to an authorization ID, not to a particular authorization snapshot. Therefore, an index for a
 * token retained across updates resolves to the latest snapshot for that authorization; the index cannot distinguish
 * which version of the snapshot the token belonged to.
 *
 * When saving an updated authorization, indexes for removed tokens are deleted before the new snapshot is saved, and
 * indexes for new tokens are written afterward. This avoids intentionally resolving a removed token to the updated
 * authorization in the ordinary sequential case. These Redis operations are not atomic, however: concurrent saves or
 * lookups overlapping an update can briefly observe mismatched index and snapshot state. This is considered a low
 * probability consistency risk.
 *
 * TODO: Retain expired token indexes and authorization snapshots for a finite grace period (for example, one day)
 * after token expiration, so expired tokens remain available for introspection without being stored indefinitely.
 */
@Service
class RedisOAuth2AuthorizationService(
    private val repository: RedisOAuth2AuthorizationDataRepository,
    private val authorizationMapper: OAuth2AuthorizationDataMapper,
) : OAuth2AuthorizationService {

    var tokenExpirationDuration: Duration = Duration.ofMinutes(10)

    /** Saves the authorization snapshot and synchronizes its token indexes. */
    override fun save(authorization: OAuth2Authorization) {
        val authorizationData = authorizationMapper.toData(authorization)

        val expiration = authorizationData.tokens
            .mapNotNull { it.expiresAt }
            .maxOrNull()
            ?.let { Expiration.At(it) } ?: Expiration.After(tokenExpirationDuration)

        retrieveUnusedTokens(authorizationData, this.repository.findById(authorizationData.id)).forEach {
            repository.deleteTokenIndex(it)
        }

        repository.save(authorizationData, expiration)

        authorizationData.indexedTokens.forEach { (token, expiration) ->
            repository.saveTokenIndex(token, authorizationData.id, expiration)
        }
    }

    /** Removes the stored authorization and the token indexes recorded in its snapshot. */
    override fun remove(authorization: OAuth2Authorization) {
        val storedAuthorizationData = repository.findById(authorization.id) ?: return

        storedAuthorizationData.indexedTokens.tokenSet.forEach(repository::deleteTokenIndex)

        repository.deleteById(authorization.id)
    }

    /** Finds an authorization by its ID. */
    override fun findById(id: String): OAuth2Authorization? {
        return repository.findById(id)?.let(authorizationMapper::toAuthorization)
    }

    /** Finds an authorization by token value. */
    override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
        // TODO: Use tokenType to distinguish token indexes and validate the token type.
        return repository.findByToken(token)?.let(authorizationMapper::toAuthorization)
    }

    private fun retrieveUnusedTokens(
        newData: OAuth2AuthorizationData,
        previous: OAuth2AuthorizationData?
    ) = previous?.indexedTokens?.tokenSet.orEmpty() - newData.indexedTokens.tokenSet

    private data class TokenIndex(val token: String, val expiration: Expiration)

    private val OAuth2AuthorizationData.state: String? get() = attributes["state"] as? String

    private val OAuth2AuthorizationData.indexedTokens get() = (tokens.map { it.toIndex() } + indexedState).filterNotNull()

    private val OAuth2AuthorizationData.indexedState
        get() = state?.let {
            TokenIndex(
                it,
                Expiration.After(tokenExpirationDuration)
            )
        }

    private val Collection<TokenIndex>.tokenSet get() = map { it.token }.toSet()

    private fun AuthorizationTokenData.toIndex() = TokenIndex(
        value,
        expiresAt?.asExpiration() ?: Expiration.After(tokenExpirationDuration)
    )

    private fun Instant.asExpiration() = Expiration.At(this)
}
