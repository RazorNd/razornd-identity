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

package ru.razornd.identity.account

import jakarta.validation.Valid
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/** Admin endpoints for managing user accounts. */
@RestController
@RequestMapping("/api/admin/accounts")
class AdminAccountController(
    private val accountService: AccountService,
) {
    @PostMapping
    fun createAccount(
        @Valid @RequestBody request: CreateAccountRequest
    ): ResponseEntity<CreatedAccountResponse> {
        val (account, generatedPassword) = try {
            accountService.createAccount(request.username, request.password, request.enabled)
        } catch (_: DuplicateKeyException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{username}")
            .buildAndExpand(account.username)
            .toUri()

        return ResponseEntity.created(location).body(account.toCreatedResponse(generatedPassword))
    }

    @GetMapping("/{username}")
    fun getAccount(@PathVariable username: String): ResponseEntity<AccountResponse> {
        val account = accountService.findAccount(username) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toResponse())
    }

    @PatchMapping("/{username}")
    fun updateAccount(
        @PathVariable username: String,
        @Valid @RequestBody request: UpdateAccountRequest,
    ): ResponseEntity<AccountResponse> {
        if (request.password == null && request.enabled == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be provided")
        }

        val account = accountService.updateAccount(username, request.password, request.enabled)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toResponse())
    }

    @DeleteMapping("/{username}")
    fun disableAccount(@PathVariable username: String): ResponseEntity<Unit> =
        if (accountService.disableAccount(username)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }

    private fun AccountDetails.toResponse() = AccountResponse(username, enabled)

    private fun AccountDetails.toCreatedResponse(
        generatedPassword: String?
    ): CreatedAccountResponse = CreatedAccountResponse(
        username = username,
        enabled = enabled,
        generatedPassword = generatedPassword,
    )
}
