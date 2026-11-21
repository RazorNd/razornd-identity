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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient

class CompositeRegisteredClientRepositoryTests {

    private val inMemoryClients = mock<InMemoryRegisteredClientRepository>()
    private val jdbcClients = mock<RegisteredClientManageRepository>()
    private val repository = CompositeRegisteredClientRepository(inMemoryClients, jdbcClients)

    @Test
    fun `finds client by id in JDBC repository`() {
        val jdbcClient = registeredClient("jdbc-id")
        doReturn(jdbcClient).whenever(jdbcClients).findById(jdbcClient.id)

        assertThat(repository.findById(jdbcClient.id)).isSameAs(jdbcClient)
    }

    @Test
    fun `finds client by id in in-memory repository when JDBC client is absent`() {
        val inMemoryClient = registeredClient("in-memory-id")
        doReturn(inMemoryClient).whenever(inMemoryClients).findById(inMemoryClient.id)

        assertThat(repository.findById(inMemoryClient.id)).isSameAs(inMemoryClient)
    }

    @Test
    fun `finds client by client id in JDBC repository`() {
        val jdbcClient = registeredClient("jdbc-id")
        doReturn(jdbcClient).whenever(jdbcClients).findByClientId(jdbcClient.clientId)

        assertThat(repository.findByClientId(jdbcClient.clientId)).isSameAs(jdbcClient)
    }

    @Test
    fun `finds client by client id in in-memory repository when JDBC client is absent`() {
        val inMemoryClient = registeredClient("in-memory-id")
        doReturn(inMemoryClient).whenever(inMemoryClients).findByClientId(inMemoryClient.clientId)

        assertThat(repository.findByClientId(inMemoryClient.clientId)).isSameAs(inMemoryClient)
    }

    @Test
    fun `persists API-managed clients in JDBC repository`() {
        val client = registeredClient("mutable-id")

        repository.save(client)

        verify(jdbcClients).save(client)
        verify(jdbcClients, never()).findById(client.id)
        verify(inMemoryClients, never()).save(client)
    }

    @Test
    fun `updates server-configured client only in memory`() {
        val client = registeredClient("server-id")
        doReturn(client).whenever(inMemoryClients).findById(client.id)

        repository.save(client)

        verify(inMemoryClients).save(client)
        verify(jdbcClients, never()).findById(client.id)
        verify(jdbcClients, never()).save(client)
    }

    private fun registeredClient(id: String, clientId: String = "client") = RegisteredClient.withId(id)
        .clientId(clientId)
        .clientName(clientId)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .build()
}
