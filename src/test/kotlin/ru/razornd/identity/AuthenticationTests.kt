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

package ru.razornd.identity

import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import ru.razornd.identity.oauth.ScopeRegistry

@Import(SecurityConfiguration::class)
@ImportAutoConfiguration(
    UserDetailsServiceAutoConfiguration::class,
    OAuth2AuthorizationServerJwtAutoConfiguration::class,
    OAuth2AuthorizationServerAutoConfiguration::class
)
@MockitoBean(types = [UserDetailsService::class, RegisteredClientRepository::class, ScopeRegistry::class])
@WebMvcTest(MainController::class)
class AuthenticationTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Test
    fun `authenticates enabled user with correct password`() {
        doReturn(user("test-user", enabled = true))
            .whenever(userDetailsService)
            .loadUserByUsername("test-user")

        mockMvc.post("/login") {
            param("username", "test-user")
            param("password", "correct-password")
            with(csrf())
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/")
        }
    }

    @Test
    fun `rejects user with incorrect password`() {
        doReturn(user("test-user", enabled = true))
            .whenever(userDetailsService)
            .loadUserByUsername("test-user")

        mockMvc.post("/login") {
            param("username", "test-user")
            param("password", "wrong-password")
            with(csrf())
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/login?error")
        }
    }

    @Test
    fun `rejects disabled user`() {
        doReturn(user("disabled-user", enabled = false))
            .whenever(userDetailsService)
            .loadUserByUsername("disabled-user")

        mockMvc.post("/login") {
            param("username", "disabled-user")
            param("password", "correct-password")
            with(csrf())
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/account-disabled")
        }
    }

    private fun user(username: String, enabled: Boolean) = User.withUsername(username)
        .password("{noop}correct-password")
        .roles("USER")
        .disabled(!enabled)
        .build()
}
