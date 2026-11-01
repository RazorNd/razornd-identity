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

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Service

/** Redis-backed service for OAuth 2.0 authorization consents. */
@Service
class RedisOAuth2AuthorizationConsentService(
    private val repository: RedisOAuth2AuthorizationConsentRepository,
    private val registeredClients: RegisteredClientRepository,
) : OAuth2AuthorizationConsentService {

    override fun save(consent: OAuth2AuthorizationConsent) {
        if (consent.authorities.isEmpty()) {
            remove(consent)
            return
        }

        repository.save(consent.registeredClientId, consent.principalName, consent.authorities)
    }

    override fun remove(consent: OAuth2AuthorizationConsent) {
        repository.deleteById(consent.registeredClientId, consent.principalName)
    }

    override fun findById(registeredClientId: String, principalName: String): OAuth2AuthorizationConsent? {
        val authorities = repository.findById(registeredClientId, principalName) ?: return null
        val registeredClient = checkNotNull(registeredClients.findById(registeredClientId)) {
            "Registered OAuth2 client '$registeredClientId' was not found"
        }

        return OAuth2AuthorizationConsent.withId(registeredClient.id, principalName)
            .authorities { it.addAll(authorities) }
            .build()
    }
}
