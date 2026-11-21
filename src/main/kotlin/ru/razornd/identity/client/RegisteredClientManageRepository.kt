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

import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository

/**
 * Repository for clients managed through the admin API.
 *
 * Implementations persist API-managed clients independently from server-owned in-memory clients
 * and support deletion by the registered client's internal repository ID.
 */
interface RegisteredClientManageRepository : RegisteredClientRepository {

    /** Deletes the registered client with the given internal repository ID. */
    fun deleteById(id: String)

}
