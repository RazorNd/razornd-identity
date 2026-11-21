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

import jakarta.validation.Valid
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/** Admin endpoints for managing registered OAuth2 clients. */
@RestController
@RequestMapping("/api/admin/clients")
class AdminClientController(
    private val clientService: ClientService,
) {
    @PostMapping
    fun createClient(
        @Valid @RequestBody request: CreateClientRequest,
    ): ResponseEntity<CreatedClientResponse> {
        val created = try {
            clientService.createClient(request)
        } catch (_: DuplicateKeyException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        val details = created.details
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{clientId}")
            .buildAndExpand(details.clientId)
            .toUri()

        return ResponseEntity.created(location).body(details.toCreatedResponse(created.clientSecret))
    }

    @GetMapping("/{clientId}")
    fun getClient(@PathVariable clientId: String): ResponseEntity<ClientResponse> =
        ResponseEntity.ok(clientService.findClient(clientId).toResponse())

    @PatchMapping("/{clientId}")
    fun updateClient(
        @PathVariable clientId: String,
        @Valid @RequestBody request: UpdateClientRequest,
    ): ResponseEntity<ClientResponse> {
        return ResponseEntity.ok(clientService.updateClient(clientId, request).toResponse())
    }

    @DeleteMapping("/{clientId}")
    fun deleteClient(@PathVariable clientId: String): ResponseEntity<Unit> {
        clientService.deleteClient(clientId)
        return ResponseEntity.noContent().build()
    }

    private fun ClientDetails.toResponse() = ClientResponse(
        clientId = clientId,
        clientName = clientName,
        clientAuthenticationMethods = clientAuthenticationMethods,
        authorizationGrantTypes = authorizationGrantTypes,
        redirectUris = redirectUris,
        scopes = scopes,
        tokenSettings = tokenSettings,
    )

    private fun ClientDetails.toCreatedResponse(clientSecret: String?) = CreatedClientResponse(
        clientId = clientId,
        clientSecret = clientSecret,
        clientName = clientName,
        clientAuthenticationMethods = clientAuthenticationMethods,
        authorizationGrantTypes = authorizationGrantTypes,
        redirectUris = redirectUris,
        scopes = scopes,
        tokenSettings = tokenSettings,
    )
}
