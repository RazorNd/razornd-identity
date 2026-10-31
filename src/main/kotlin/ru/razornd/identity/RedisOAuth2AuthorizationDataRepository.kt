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

import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit
import org.springframework.data.redis.core.types.Expiration as RedisExpiration

/** Stores authorization snapshots and token-to-authorization indexes in Redis. */
@Repository
class RedisOAuth2AuthorizationDataRepository(
    private val redis: RedisOperations<String, OAuth2AuthorizationData>,
    private val indexes: StringRedisTemplate,
) {
    var keyPrefix = "razornd:identity:oauth2"

    private val authorizations: ValueOperations<String, OAuth2AuthorizationData> get() = redis.opsForValue()

    private val tokens: ValueOperations<String, String> get() = indexes.opsForValue()

    /** Returns the authorization snapshot with the given ID, or `null` if it is not stored. */
    fun findById(id: String): OAuth2AuthorizationData? = authorizations[authorizationKey(id)]

    /** Resolves a token index to its authorization snapshot, or returns `null` if either entry is absent. */
    fun findByToken(token: String): OAuth2AuthorizationData? = tokens[tokenKey(token)]?.let { findById(it) }

    /** Stores an authorization snapshot with the given expiration. */
    fun save(data: OAuth2AuthorizationData, expiration: Expiration) {
        authorizations.set(
            authorizationKey(data.id),
            data,
            expiration.asRedis()
        )
    }

    /** Deletes the authorization snapshot with the given ID. */
    fun deleteById(id: String) {
        redis.delete(authorizationKey(id))
    }

    /** Stores an index from [token] to [authorizationId] with the given expiration. */
    fun saveTokenIndex(token: String, authorizationId: String, expiration: Expiration) {
        tokens.set(tokenKey(token), authorizationId, expiration.asRedis())
    }

    /** Deletes the index for [token]. */
    fun deleteTokenIndex(token: String) {
        indexes.delete(tokenKey(token))
    }

    private fun Expiration.asRedis() = when (this) {
        is Expiration.After -> RedisExpiration.from(expiredDuration)
        is Expiration.At -> RedisExpiration.unixTimestamp(expiredAt.epochSecond, TimeUnit.SECONDS)
    }

    private fun authorizationKey(id: String) = "$keyPrefix:authorization:$id"
    private fun tokenKey(value: String) = "$keyPrefix:token:$value"
}
