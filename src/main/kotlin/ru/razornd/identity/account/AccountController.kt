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

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

/** Handles browser-based account self-service operations. */
@Controller
class AccountController(private val accountService: AccountService) {
    @GetMapping("/change-password")
    fun changePassword(principal: Principal) = ModelAndView(
        "change-password",
        mapOf("username" to principal.name, "changePasswordForm" to ChangePasswordForm())
    )

    /**
     * Validates the submitted form, delegates the password change, and binds an invalid-current-
     * password error to the form when the service rejects the current password.
     */
    @PostMapping("/change-password")
    fun changePassword(
        @AuthenticationPrincipal
        principal: UserDetails,
        @Valid @ModelAttribute("changePasswordForm") form: ChangePasswordForm,
        bindingResult: BindingResult
    ): ModelAndView {
        if (bindingResult.hasErrors()) return changePasswordWithError(principal)

        return try {
            accountService.changePasswordForCurrentUser(principal.username, form.oldPassword, form.newPassword)
            ModelAndView("redirect:/")
        } catch (_: InvalidCurrentPasswordException) {
            bindingResult.rejectValue("oldPassword", "password.invalid", "The current password is incorrect.")
            changePasswordWithError(principal)
        }
    }

    private fun changePasswordWithError(principal: UserDetails) = ModelAndView(
        "change-password",
        mapOf(
            "username" to principal.username,
        ),
        HttpStatus.BAD_REQUEST
    )
}
