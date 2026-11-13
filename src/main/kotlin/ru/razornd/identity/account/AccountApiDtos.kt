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

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Request to create an account; omitting [password] asks the service to generate one. */
data class CreateAccountRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val username: String,
    @field:Size(min = 8)
    val password: String? = null,
    val enabled: Boolean = true,
)

/** Partial update request; at least one field must be supplied. */
data class UpdateAccountRequest(
    @field:Size(min = 8)
    val password: String? = null,
    val enabled: Boolean? = null,
)

/** Public account representation; never includes a stored password. */
data class AccountResponse(
    val username: String,
    val enabled: Boolean,
)

/** Creation response that includes generated credentials once, when applicable. */
data class CreatedAccountResponse(
    val username: String,
    val enabled: Boolean,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val generatedPassword: String? = null,
)
