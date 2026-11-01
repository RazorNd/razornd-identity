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

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

@Controller
class AccountController(private val userDetailsManager: UserDetailsManager) {

    private val passwordEncoder = createDelegatingPasswordEncoder()

    @GetMapping("/change-password")
    fun changePassword(principal: Principal) = ModelAndView(
        "change-password",
        mapOf("username" to principal.name, "changePasswordForm" to ChangePasswordForm())
    )

    @PostMapping("/change-password")
    fun changePassword(
        @AuthenticationPrincipal
        principal: UserDetails,
        @Valid @ModelAttribute("changePasswordForm") form: ChangePasswordForm,
        bindingResult: BindingResult
    ): ModelAndView {
        if (bindingResult.hasErrors()) return changePasswordWithError(principal)

        val currentUser = userDetailsManager.loadUserByUsername(principal.username)

        return when {
            form.newPassword != form.repeatPassword -> {
                bindingResult.rejectValue("repeatPassword", "password.mismatch", "The passwords do not match.")
                changePasswordWithError(principal)
            }

            !passwordEncoder.matches(form.oldPassword, currentUser.password) -> {
                bindingResult.rejectValue("oldPassword", "password.invalid", "The current password is incorrect.")
                changePasswordWithError(principal)
            }

            else -> {
                userDetailsManager.changePassword(form.oldPassword, passwordEncoder.encode(form.newPassword))
                ModelAndView("redirect:/")
            }
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
