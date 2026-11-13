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

import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.razornd.identity.Loggable
import ru.razornd.identity.withLogger

/**
 * Implements account operations independently of the web or API adapter.
 *
 * @property userDetailsManager loads account credentials and applies password changes
 * @property passwordEncoder verifies and encodes passwords using the configured password format
 */
@Service
@Transactional
class AccountService(
    private val userDetailsManager: UserDetailsManager,
    private val passwordEncoder: PasswordEncoder,
    private val passwordGenerator: PasswordGenerator,
) {
    /** Authorities assigned to accounts created without explicit role selection. */
    var defaultRoles: Set<String> = setOf("USER")

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

        log.atInfo()
            .addKeyValue("username", username)
            .log("Change password for username '$username'")
    }

    /** Creates an account, generating an initial password when [password] is absent. */
    fun createAccount(username: String, password: String?, enabled: Boolean): CreatedAccount {
        val generatedPassword = if (password == null) passwordGenerator.generate() else null

        val account = User.withUsername(username)
            .password(password ?: generatedPassword)
            .passwordEncoder(passwordEncoder::encode)
            .roles(*defaultRoles.toTypedArray())
            .disabled(!enabled)
            .build()
            .also(userDetailsManager::createUser)
            .toAccount()

        log.atInfo()
            .addKeyValue("username", username)
            .addKeyValue("enabled", enabled)
            .log("Account created for username '$username'")

        return CreatedAccount(account = account, generatedPassword = generatedPassword)
    }

    /** Returns account details for [username], or `null` if no such account exists. */
    fun findAccount(username: String): AccountDetails? = findUser(username)?.toAccount()

    /** Updates the supplied fields for [username], or returns `null` if no account exists. */
    fun updateAccount(username: String, password: String?, enabled: Boolean?): AccountDetails? =
        updateUser(username) { builder ->
            if (password != null)
                builder.password(password)
                    .passwordEncoder(passwordEncoder::encode)
            if (enabled != null)
                builder.disabled(!enabled)

            log.atInfo()
                .addKeyValue("username", username)
                .addKeyValue("enabled", enabled)
                .log("Update account for username '$username'")
        }

    /** Disables [username] without deleting its account; returns `false` if it does not exist. */
    fun disableAccount(username: String): Boolean = updateUser(username) {
        it.disabled(true)
        log.atInfo()
            .addKeyValue("username", username)
            .log("Disable account for username '$username'")
    } != null

    private fun findUser(username: String) = try {
        userDetailsManager.loadUserByUsername(username)
    } catch (exception: UsernameNotFoundException) {
        log.atDebug()
            .addKeyValue("username", username)
            .log("User '$username' not found", exception)
        null
    }

    private fun updateUser(username: String, builder: (User.UserBuilder) -> Unit): AccountDetails? {
        val user = User.withUserDetails(findUser(username) ?: return null)
            .apply { builder(this) }
            .build()

        userDetailsManager.updateUser(user)

        return user.toAccount()
    }

    private fun UserDetails.toAccount() = AccountDetails(username, isEnabled)

    companion object : Loggable by withLogger<AccountService>()
}

/** Indicates that the supplied current password does not match the user's stored password. */
class InvalidCurrentPasswordException : RuntimeException("The current password is incorrect.")

/** Non-sensitive account state returned by account management operations. */
data class AccountDetails(
    val username: String,
    val enabled: Boolean,
)

/** Account creation result; [generatedPassword] is non-null only for generated credentials. */
data class CreatedAccount(
    val account: AccountDetails,
    val generatedPassword: String?,
)
