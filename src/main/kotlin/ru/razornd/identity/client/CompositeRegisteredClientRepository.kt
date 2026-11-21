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

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository

/**
 * Serves server-configured clients from memory and API-managed clients from JDBC.
 *
 * Writes to server-configured clients stay in memory; writes to API-managed clients are
 * persisted in JDBC. This keeps server-owned clients out of the API-managed repository.
 */
class CompositeRegisteredClientRepository(
    private val inMemoryRegisteredClientRepository: RegisteredClientRepository,
    private val jdbcRegisteredClientRepository: RegisteredClientManageRepository,
) : RegisteredClientRepository {

    override fun save(registeredClient: RegisteredClient) {
        if (inMemoryRegisteredClientRepository.findById(registeredClient.id) != null) {
            inMemoryRegisteredClientRepository.save(registeredClient)
        } else {
            jdbcRegisteredClientRepository.save(registeredClient)
        }
    }

    override fun findById(id: String) =
        jdbcRegisteredClientRepository.findById(id) ?: inMemoryRegisteredClientRepository.findById(id)

    override fun findByClientId(clientId: String) =
        jdbcRegisteredClientRepository.findByClientId(clientId)
            ?: inMemoryRegisteredClientRepository.findByClientId(clientId)
}
