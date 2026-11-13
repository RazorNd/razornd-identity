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

package ru.razornd.identity.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsChecker
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.jwt.Jwt

class UserDetailsJwtAuthenticationConverterTests {
    private val userDetailsService = mock<UserDetailsService>()
    private val converter = UserDetailsJwtAuthenticationConverter(userDetailsService)

    @Test
    fun `loads user by JWT subject and uses authorities from user details`() {
        val username = "admin"
        val userDetails = User.withUsername(username)
            .password("{noop}password")
            .authorities("USER_READ", "USER_WRITE")
            .build()
        whenever(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails)

        val authentication = converter.convert(jwt(username, "USER_ADMIN"))

        verify(userDetailsService).loadUserByUsername(username)
        assertThat(authentication.principal).isSameAs(userDetails)
        assertThat(authentication.name).isEqualTo(username)
        assertThat(authentication.authorities.map { it.authority })
            .containsExactlyInAnyOrder("SCOPE_USER_ADMIN", "USER_READ", "USER_WRITE", "FACTOR_BEARER")
    }

    @Test
    fun `rejects a disabled user`() {
        val username = "disabled-user"
        val userDetails = User.withUsername(username)
            .password("{noop}password")
            .authorities("USER_READ")
            .disabled(true)
            .build()
        whenever(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails)

        assertThrows(DisabledException::class.java) {
            converter.convert(jwt(username))
        }
    }

    @Test
    fun `uses a custom account status checker`() {
        val username = "disabled-user"
        val userDetails = User.withUsername(username)
            .password("{noop}password")
            .authorities("USER_READ")
            .disabled(true)
            .build()
        whenever(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails)
        val customChecker = mock<UserDetailsChecker>()
        converter.accountStatusChecker = customChecker

        val authentication = converter.convert(jwt(username))

        verify(customChecker).check(userDetails)
        assertThat(authentication.principal).isSameAs(userDetails)
    }

    @Test
    fun `uses a custom JWT authorities converter`() {
        val username = "admin"
        val userDetails = User.withUsername(username)
            .password("{noop}password")
            .authorities("USER_READ")
            .build()
        whenever(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails)
        val token = jwt(username, "IGNORED_SCOPE")
        val customConverter = mock<Converter<Jwt, Collection<GrantedAuthority>>>()
        whenever(customConverter.convert(token)).thenReturn(listOf(SimpleGrantedAuthority("CUSTOM_SCOPE")))
        converter.jwtGrantedAuthoritiesConverter = customConverter

        val authentication = converter.convert(token)

        verify(customConverter).convert(token)
        assertThat(authentication.authorities.map { it.authority })
            .containsExactlyInAnyOrder("CUSTOM_SCOPE", "USER_READ", "FACTOR_BEARER")
    }

    @Test
    fun `propagates username not found when the JWT subject is not a user`() {
        val subject = "unknown-user"
        whenever(userDetailsService.loadUserByUsername(subject))
            .thenThrow(UsernameNotFoundException("User not found"))

        assertThrows(UsernameNotFoundException::class.java) {
            converter.convert(jwt(subject))
        }

        verify(userDetailsService).loadUserByUsername(subject)
    }

    private fun jwt(subject: String, scope: String? = null): Jwt {
        val builder = Jwt.withTokenValue("access-token")
            .header("alg", "none")
            .subject(subject)
        if (scope != null) builder.claim("scope", scope)
        return builder.build()
    }
}
