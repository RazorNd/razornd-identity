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

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [AccountService::class])
class AccountServiceTests {

    @MockitoBean
    private lateinit var userDetailsManager: UserDetailsManager

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        doAnswer { (a: CharSequence, b: CharSequence) ->
            a.removePrefix("encoded-") == b.removePrefix("encoded-")
        }.whenever(passwordEncoder).matches(any(), any())
        doAnswer { (password: CharSequence) -> "encoded-$password" }.whenever(passwordEncoder).encode(any())
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
}
