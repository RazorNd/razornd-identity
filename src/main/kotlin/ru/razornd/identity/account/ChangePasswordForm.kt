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

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordForm(
    @field:NotBlank(message = "Enter your current password.")
    val oldPassword: String = "",
    @field:NotBlank(message = "Enter a new password.")
    @field:Size(min = 8, message = "The new password must be at least 8 characters long.")
    val newPassword: String = "",
    @field:NotBlank(message = "Confirm your new password.")
    val repeatPassword: String = "",
)
