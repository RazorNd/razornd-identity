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
import org.springframework.data.redis.core.ValueOperations
import org.springframework.stereotype.Repository
import org.springframework.security.core.GrantedAuthority

/** Redis-backed storage for OAuth 2.0 authorization consent authorities. */
@Repository
class RedisOAuth2AuthorizationConsentRepository(
    private val redis: RedisOperations<String, Set<GrantedAuthority>>,
) {

    var keyPrefix = "razornd:identity:oauth2"

    private val consents: ValueOperations<String, Set<GrantedAuthority>> get() = redis.opsForValue()

    fun findById(registeredClientId: String, principalName: String): Set<GrantedAuthority>? =
        consents[key(registeredClientId, principalName)]

    fun save(
        registeredClientId: String,
        principalName: String,
        authorities: Set<GrantedAuthority>,
    ) {
        consents.set(key(registeredClientId, principalName), authorities)
    }

    fun deleteById(registeredClientId: String, principalName: String) {
        redis.delete(key(registeredClientId, principalName))
    }

    private fun key(registeredClientId: String, principalName: String) =
        "$keyPrefix:consent:$registeredClientId:$principalName"
}
