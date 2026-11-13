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

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Validates that a [ChangePasswordForm]'s new password and confirmation match.
 *
 * When they differ, the constraint violation is associated with the `repeatPassword` property.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [PasswordsMatchValidator::class])
annotation class PasswordsMatch(
    val message: String = "The passwords do not match.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** Implements [PasswordsMatch] and reports mismatches on the confirmation field. */
class PasswordsMatchValidator : ConstraintValidator<PasswordsMatch, ChangePasswordForm> {
    override fun isValid(value: ChangePasswordForm?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.newPassword == value.repeatPassword) return true

        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
            .addPropertyNode("repeatPassword")
            .addConstraintViolation()
        return false
    }
}
