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

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpMethod.valueOf
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.*
import ru.razornd.identity.SecurityConfiguration

@Import(SecurityConfiguration::class)
@ImportAutoConfiguration(
    UserDetailsServiceAutoConfiguration::class,
    OAuth2AuthorizationServerJwtAutoConfiguration::class,
    OAuth2AuthorizationServerAutoConfiguration::class
)
@MockitoBean(types = [UserDetailsService::class, RegisteredClientRepository::class])
@WebMvcTest(AdminAccountController::class)
class AdminAccountControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var accountService: AccountService

    val adminAuthorities = setOf(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Test
    fun `creates an enabled account and returns generated password`() {
        doReturn(
            CreatedAccount(AccountDetails("new-user", true), "generated-password")
        ).whenever(accountService).createAccount(any(), anyOrNull(), any())

        mockMvc.post("/api/admin/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "username": "new-user"
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isCreated() }
            header { string("Location", "http://localhost/api/admin/accounts/new-user") }
            jsonPath("$.username") { value("new-user") }
            jsonPath("$.enabled") { value(true) }
            jsonPath("$.generatedPassword") { value("generated-password") }
        }

        verify(accountService).createAccount("new-user", null, true)
    }

    @Test
    fun `returns conflict when username already exists`() {
        doThrow(DuplicateKeyException("Username already exists"))
            .whenever(accountService)
            .createAccount(any(), anyOrNull(), any())

        mockMvc.post("/api/admin/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "username": "existing-user"
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isConflict() }
        }

        verify(accountService).createAccount("existing-user", null, true)
    }


    @Test
    fun `does not return a password supplied by the caller`() {
        doReturn(
            CreatedAccount(AccountDetails("new-user", false), null)
        ).whenever(accountService).createAccount(any(), anyOrNull(), any())

        mockMvc.post("/api/admin/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "username": "new-user",
                  "password": "supplied-password",
                  "enabled": false
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.enabled") { value(false) }
            jsonPath("$.generatedPassword") { doesNotExist() }
        }

        verify(accountService).createAccount("new-user", "supplied-password", false)
    }

    @Test
    fun `gets an account by username`() {
        doReturn(AccountDetails("existing-user", false))
            .whenever(accountService)
            .findAccount(any())

        mockMvc.get("/api/admin/accounts/existing-user") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            jsonPath("$.username") { value("existing-user") }
            jsonPath("$.enabled") { value(false) }
        }

        verify(accountService).findAccount("existing-user")
    }

    @Test
    fun `does not create a session for an API request`() {
        doReturn(AccountDetails("existing-user", true))
            .whenever(accountService)
            .findAccount(any())

        val result = mockMvc.get("/api/admin/accounts/existing-user") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            cookie { doesNotExist("SESSION") }
        }.andReturn()

        assertNull(result.request.getSession(false))
        verify(accountService).findAccount("existing-user")
    }

    @Test
    fun `returns not found when account does not exist`() {
        doReturn(null)
            .whenever(accountService)
            .findAccount(any())

        mockMvc.get("/api/admin/accounts/missing-user") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isNotFound() }
        }

        verify(accountService).findAccount("missing-user")
    }

    @Test
    fun `patches account fields`() {
        doReturn(AccountDetails("existing-user", false))
            .whenever(accountService)
            .updateAccount(any(), anyOrNull(), anyOrNull())

        mockMvc.patch("/api/admin/accounts/existing-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "password": "new-password",
                  "enabled": false
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            jsonPath("$.username") { value("existing-user") }
            jsonPath("$.enabled") { value(false) }
        }

        verify(accountService).updateAccount("existing-user", "new-password", false)
    }

    @Test
    fun `patches enabled without changing the password`() {
        doReturn(AccountDetails("existing-user", false))
            .whenever(accountService)
            .updateAccount(any(), anyOrNull(), anyOrNull())

        mockMvc.patch("/api/admin/accounts/existing-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "enabled": false
                }
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isOk() }
            jsonPath("$.enabled") { value(false) }
        }

        verify(accountService).updateAccount("existing-user", null, false)
    }

    @Test
    fun `rejects a patch without changes`() {
        mockMvc.patch("/api/admin/accounts/existing-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {}
            """.trimIndent()
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isBadRequest() }
        }

        verify(accountService, never()).updateAccount(any(), any(), any())
    }

    @Test
    fun `delete disables an account`() {
        doReturn(true)
            .whenever(accountService)
            .disableAccount(any())

        mockMvc.delete("/api/admin/accounts/existing-user") {
            with(jwt().authorities(adminAuthorities))
        }.andExpect {
            status { isNoContent() }
        }

        verify(accountService).disableAccount("existing-user")
    }

    @ParameterizedTest(name = "{0} {1} without JWT returns 401")
    @CsvSource(
        "POST, /api/admin/accounts",
        "GET, /api/admin/accounts/existing-user",
        "PATCH, /api/admin/accounts/existing-user",
        "DELETE, /api/admin/accounts/existing-user",
    )
    fun `rejects unauthenticated requests`(method: String, uri: String) {
        val httpMethod = valueOf(method)
        mockMvc.request(httpMethod, uri) {}.andExpect {
            status { isUnauthorized() }
        }
    }

    @ParameterizedTest(name = "{0} {1} with non-admin JWT returns 403")
    @CsvSource(
        "POST, /api/admin/accounts",
        "GET, /api/admin/accounts/existing-user",
        "PATCH, /api/admin/accounts/existing-user",
        "DELETE, /api/admin/accounts/existing-user",
    )
    fun `rejects authenticated non-admin requests`(method: String, uri: String) {
        val httpMethod = valueOf(method)
        mockMvc.request(httpMethod, uri) {
            with(jwt().authorities(setOf(SimpleGrantedAuthority("ROLE_USER"))))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Nested
    inner class RequestValidationTests {
        @ParameterizedTest
        @CsvSource(
            "<blank>,<null>,true",
            "123456789012345678901234567890123456789012345678901,<null>,true",
            "valid-user,short,false",
            "<null>,valid-password,<null>"
        )
        fun `rejects invalid account creation requests`(username: String, password: String, enabled: String) {
            mockMvc.post("/api/admin/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "username": ${username.jsonValue()},
                      "password": ${password.jsonValue()},
                      "enabled": ${enabled.jsonBooleanValue()}
                    }
                """.trimIndent()
                with(jwt().authorities(adminAuthorities))
            }.andExpect {
                status { isBadRequest() }
            }

            verifyNoInteractions(accountService)
        }

        @ParameterizedTest
        @CsvSource(
            "short,false",
            "1234567,true",
            "<null>,<null>",
        )
        fun `rejects invalid account update requests`(password: String, enabled: String) {
            mockMvc.patch("/api/admin/accounts/existing-user") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "password": ${password.jsonValue()},
                      "enabled": ${enabled.jsonBooleanValue()}
                    }
                """.trimIndent()
                with(jwt().authorities(adminAuthorities))
            }.andExpect {
                status { isBadRequest() }
            }

            verifyNoInteractions(accountService)
        }

        private fun String.nullableValue() = if (this == "<blank>") "" else this

        private fun String.jsonValue() = if (this == "<null>") "null" else "\"${this.nullableValue()}\""

        private fun String.jsonBooleanValue() = if (this == "<null>") "null" else this
    }
}
