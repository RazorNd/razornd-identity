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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [AccountService::class])
class AccountServiceTests {

    @MockitoBean
    private lateinit var userDetailsManager: UserDetailsManager

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var passwordGenerator: PasswordGenerator

    @Autowired
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        accountService.defaultRoles = setOf("USER")
        doAnswer { (a: CharSequence, b: CharSequence) ->
            a.removePrefix("encoded-") == b.removePrefix("encoded-")
        }.whenever(passwordEncoder).matches(any(), any())
        doAnswer { (password: CharSequence) -> "encoded-$password" }.whenever(passwordEncoder).encode(any())
        doReturn("generated-password").whenever(passwordGenerator).generate()
    }

    @Test
    fun `changes password after validating the current password`() {
        val userDetails = User.withUsername("test-user").password("encoded-old-password").build()

        doReturn(userDetails).whenever(userDetailsManager).loadUserByUsername(any())

        accountService.changePasswordForCurrentUser("test-user", "old-password", "new-password")

        verify(userDetailsManager).loadUserByUsername("test-user")
        verify(passwordEncoder).matches("old-password", "encoded-old-password")
        verify(passwordEncoder).encode("new-password")
        verify(userDetailsManager).changePassword("old-password", "encoded-new-password")
    }

    @Test
    fun `throws when the current password does not match`() {
        val userDetails = User.withUsername("test-user").password("encoded-old-password").build()
        doReturn(userDetails).whenever(userDetailsManager).loadUserByUsername(any())

        assertThatThrownBy {
            accountService.changePasswordForCurrentUser("test-user", "wrong-password", "new-password")
        }
            .isExactlyInstanceOf(InvalidCurrentPasswordException::class.java)
            .hasMessage("The current password is incorrect.")

        verify(passwordEncoder, never()).encode(any())
        verify(userDetailsManager, never()).changePassword(any(), any())
    }

    @Test
    fun `propagates a missing user when changing the current password`() {
        doThrow(UsernameNotFoundException("Account not found"))
            .whenever(userDetailsManager)
            .loadUserByUsername(any())

        assertThatThrownBy {
            accountService.changePasswordForCurrentUser("missing-user", "old-password", "new-password")
        }.isExactlyInstanceOf(UsernameNotFoundException::class.java)

        verify(passwordEncoder, never()).matches(any(), any())
        verify(userDetailsManager, never()).changePassword(any(), any())
    }

    @Test
    fun `returns account details when creating with a supplied password`() {
        val created = accountService.createAccount("new-user", "plain-password", enabled = false)

        assertThat(created)
            .usingRecursiveComparison()
            .isEqualTo(CreatedAccount(AccountDetails("new-user", false), null))
        verify(passwordGenerator, never()).generate()
    }

    @Test
    fun `creates a user with an encoded password and configured default roles`() {
        accountService.defaultRoles = setOf("SUPPORT")

        accountService.createAccount("new-user", "plain-password", enabled = false)

        val userCaptor = argumentCaptor<UserDetails>()
        verify(userDetailsManager).createUser(userCaptor.capture())
        assertThat(userCaptor.singleValue)
            .usingRecursiveComparison()
            .isEqualTo(
                User.withUsername("new-user")
                    .password("encoded-plain-password")
                    .authorities("ROLE_SUPPORT")
                    .disabled(true)
                    .build()
            )
        verify(passwordEncoder).encode("plain-password")
    }

    @Test
    fun `returns account details and generated password when password is absent`() {
        val created = accountService.createAccount("generated-user", null, enabled = true)

        assertThat(created)
            .usingRecursiveComparison()
            .isEqualTo(
                CreatedAccount(
                    AccountDetails("generated-user", true),
                    "generated-password"
                )
            )
    }

    @Test
    fun `creates a user with the encoded generated password`() {
        val created = accountService.createAccount("generated-user", null, enabled = true)
        val generatedPassword = checkNotNull(created.generatedPassword)

        val userCaptor = argumentCaptor<UserDetails>()
        verify(userDetailsManager).createUser(userCaptor.capture())
        assertThat(userCaptor.singleValue)
            .usingRecursiveComparison()
            .isEqualTo(
                User.withUsername("generated-user")
                    .password("encoded-$generatedPassword")
                    .roles("USER")
                    .build()
            )
        verify(passwordEncoder).encode(generatedPassword)
        verify(passwordGenerator).generate()
    }

    @Test
    fun `returns the account state for an existing username`() {
        val user = User.withUsername("existing-user")
            .password("encoded-password")
            .authorities("user")
            .disabled(true)
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        val account = accountService.findAccount("existing-user")

        assertThat(account).isEqualTo(AccountDetails("existing-user", false))
    }

    @Test
    fun `loads the requested username when finding an account`() {
        val user = User.withUsername("existing-user")
            .password("encoded-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        accountService.findAccount("existing-user")

        verify(userDetailsManager).loadUserByUsername("existing-user")
    }

    @Test
    fun `returns null when the account to find does not exist`() {
        doThrow(UsernameNotFoundException("Account not found"))
            .whenever(userDetailsManager)
            .loadUserByUsername(any())

        val account = accountService.findAccount("missing-user")

        assertThat(account).isNull()
    }

    @Test
    fun `returns updated account details`() {
        val user = User.withUsername("existing-user")
            .password("encoded-old-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        val updated = accountService.updateAccount("existing-user", "new-password", false)

        assertThat(updated)
            .usingRecursiveComparison()
            .isEqualTo(AccountDetails("existing-user", false))
    }

    @Test
    fun `updates the user with the new password and enabled state`() {
        val user = User.withUsername("existing-user")
            .password("encoded-old-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        accountService.updateAccount("existing-user", "new-password", false)

        val userCaptor = argumentCaptor<UserDetails>()
        verify(userDetailsManager).updateUser(userCaptor.capture())
        assertThat(userCaptor.singleValue)
            .usingRecursiveComparison()
            .isEqualTo(
                User.withUsername("existing-user")
                    .password("encoded-new-password")
                    .authorities("user")
                    .disabled(true)
                    .build()
            )
        verify(passwordEncoder).encode("new-password")
    }

    @Test
    fun `returns account details when updating only enabled`() {
        val user = User.withUsername("existing-user")
            .password("encoded-existing-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        val updated = accountService.updateAccount("existing-user", password = null, enabled = false)

        assertThat(updated)
            .usingRecursiveComparison()
            .isEqualTo(AccountDetails("existing-user", false))
    }

    @Test
    fun `preserves password and authorities when updating only enabled`() {
        val user = User.withUsername("existing-user")
            .password("encoded-existing-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        accountService.updateAccount("existing-user", password = null, enabled = false)

        val userCaptor = argumentCaptor<UserDetails>()
        verify(userDetailsManager).updateUser(userCaptor.capture())

        assertThat(userCaptor.singleValue)
            .usingRecursiveComparison()
            .isEqualTo(
                User.withUsername("existing-user")
                    .password("encoded-existing-password")
                    .authorities("user")
                    .disabled(true)
                    .build()
            )
        verify(passwordEncoder, never()).encode(any())
    }

    @Test
    fun `returns account details when updating only password`() {
        val user = User.withUsername("existing-user")
            .password("encoded-existing-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        val updated = accountService.updateAccount("existing-user", password = "new-password", enabled = null)

        assertThat(updated)
            .usingRecursiveComparison()
            .isEqualTo(AccountDetails("existing-user", true))
    }

    @Test
    fun `preserves enabled state when updating only password`() {
        val user = User.withUsername("existing-user")
            .password("encoded-existing-password")
            .authorities("user")
            .disabled(true)
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        accountService.updateAccount("existing-user", password = "new-password", enabled = null)

        val userCaptor = argumentCaptor<UserDetails>()
        verify(userDetailsManager).updateUser(userCaptor.capture())
        assertThat(userCaptor.singleValue)
            .usingRecursiveComparison()
            .isEqualTo(
                User.withUsername("existing-user")
                    .password("encoded-new-password")
                    .authorities("user")
                    .disabled(true)
                    .build()
            )
        verify(passwordEncoder).encode("new-password")
    }

    @Test
    fun `returns null when updating an account that does not exist`() {
        doThrow(UsernameNotFoundException("Account not found"))
            .whenever(userDetailsManager)
            .loadUserByUsername(any())

        val updated = accountService.updateAccount("missing-user", "new-password", false)

        assertThat(updated).isNull()
    }

    @Test
    fun `does not update UserDetailsManager when the account does not exist`() {
        doThrow(UsernameNotFoundException("Account not found"))
            .whenever(userDetailsManager)
            .loadUserByUsername(any())

        accountService.updateAccount("missing-user", "new-password", false)

        verify(userDetailsManager, never()).updateUser(any())
    }

    @Test
    fun `returns true when disabling an existing account`() {
        val user = User.withUsername("existing-user")
            .password("encoded-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        val disabled = accountService.disableAccount("existing-user")

        assertThat(disabled).isTrue()
    }

    @Test
    fun `updates the account as disabled instead of deleting it`() {
        val user = User.withUsername("existing-user")
            .password("encoded-password")
            .authorities("user")
            .build()
        doReturn(user).whenever(userDetailsManager).loadUserByUsername(any())

        accountService.disableAccount("existing-user")

        val userCaptor = argumentCaptor<UserDetails>()
        verify(userDetailsManager).updateUser(userCaptor.capture())
        assertThat(userCaptor.singleValue)
            .usingRecursiveComparison()
            .isEqualTo(
                User.withUsername("existing-user")
                    .password("encoded-password")
                    .authorities("user")
                    .disabled(true)
                    .build()
            )
        verify(userDetailsManager, never()).deleteUser(any())
    }

    @Test
    fun `returns false when disabling a missing account`() {
        doThrow(UsernameNotFoundException("Account not found"))
            .whenever(userDetailsManager)
            .loadUserByUsername(any())

        val disabled = accountService.disableAccount("missing-user")

        assertThat(disabled).isFalse()
    }

    @Test
    fun `does not update or delete when disabling a missing account`() {
        doThrow(UsernameNotFoundException("Account not found"))
            .whenever(userDetailsManager)
            .loadUserByUsername(any())

        accountService.disableAccount("missing-user")

        verify(userDetailsManager, never()).updateUser(any())
        verify(userDetailsManager, never()).deleteUser(any())
    }
}
