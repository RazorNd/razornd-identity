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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@WebMvcTest(AccountController::class)
class AccountControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var accountService: AccountService

    @Test
    @WithMockUser
    fun `changes password when form is valid`() {
        mockMvc.post("/change-password") {
            param("oldPassword", "current-pass")
            param("newPassword", "new-password")
            param("repeatPassword", "new-password")
            with(csrf())
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/")
        }

        verify(accountService).changePasswordForCurrentUser("user", "current-pass", "new-password")
    }

    @Test
    @WithMockUser(username = "test-user")
    fun `rejects old password that does not match the stored password`() {
        doThrow(InvalidCurrentPasswordException())
            .whenever(accountService)
            .changePasswordForCurrentUser(any(), any(), any())

        mockMvc.post("/change-password") {
            param("oldPassword", "wrong-password")
            param("newPassword", "new-password")
            param("repeatPassword", "new-password")
            with(csrf())
        }.andExpect {
            status { isBadRequest() }
            view { name("change-password") }
            model { attributeHasFieldErrors("changePasswordForm", "oldPassword") }
        }

        verify(accountService).changePasswordForCurrentUser("test-user", "wrong-password", "new-password")
    }

    @Test
    @WithMockUser(username = "test-user")
    fun `shows change password form`() {
        mockMvc.get("/change-password").andExpect {
            status { isOk() }
            view { name("change-password") }
            model { attribute("username", "test-user") }
        }
    }

    @Nested
    inner class ChangePasswordValidationTests {
        @ParameterizedTest
        @CsvSource(
            "<blank>,new-password,new-password,oldPassword",
            "current-password,<blank>,<blank>,newPassword|repeatPassword",
            "current-password,short,short,newPassword",
            "current-password,new-password,<blank>,repeatPassword",
            "current-password,new-password,different-password,repeatPassword",
        )
        @WithMockUser
        fun `rejects invalid password forms`(
            oldPassword: String,
            newPassword: String,
            repeatPassword: String,
            expectedFields: String,
        ) {
            mockMvc.post("/change-password") {
                param("oldPassword", oldPassword.blankIfSentinel())
                param("newPassword", newPassword.blankIfSentinel())
                param("repeatPassword", repeatPassword.blankIfSentinel())
                with(csrf())
            }.andExpect {
                status { isBadRequest() }
                view { name("change-password") }
                model {
                    expectedFields.split('|').forEach { field ->
                        attributeHasFieldErrors("changePasswordForm", field)
                    }
                }
            }

            verify(accountService, never()).changePasswordForCurrentUser(any(), any(), any())
        }

        private fun String.blankIfSentinel() = if (this == "<blank>") "" else this
    }
}
