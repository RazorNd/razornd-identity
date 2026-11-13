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

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.stereotype.Service

/**
 * Implements account operations independently of the web or API adapter.
 *
 * @property userDetailsManager loads account credentials and applies password changes
 * @property passwordEncoder verifies and encodes passwords using the configured password format
 */
@Service
class AccountService(
    private val userDetailsManager: UserDetailsManager,
    private val passwordEncoder: PasswordEncoder,
) {
    /**
     * Changes the password for the currently authenticated user after verifying their current
     * password against the stored credentials.
     *
     * @param username username of the currently authenticated user
     * @param oldPassword current raw password supplied by the user
     * @param newPassword new raw password to encode and store
     * @throws InvalidCurrentPasswordException if [oldPassword] does not match the stored password
     */
    fun changePasswordForCurrentUser(username: String, oldPassword: String, newPassword: String) {
        val userDetails = userDetailsManager.loadUserByUsername(username)
        if (!passwordEncoder.matches(oldPassword, userDetails.password)) {
            throw InvalidCurrentPasswordException()
        }

        userDetailsManager.changePassword(oldPassword, passwordEncoder.encode(newPassword))
    }
}

/** Indicates that the supplied current password does not match the user's stored password. */
class InvalidCurrentPasswordException : RuntimeException("The current password is incorrect.")
