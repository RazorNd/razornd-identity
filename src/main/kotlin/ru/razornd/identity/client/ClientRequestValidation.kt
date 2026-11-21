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

package ru.razornd.identity.client

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import kotlin.reflect.KClass

/** Requires a supplied optional string to contain non-whitespace characters. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [NotBlankWhenPresentValidator::class])
annotation class NotBlankWhenPresent(
    val message: String = "must not be blank when provided",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NotBlankWhenPresentValidator : ConstraintValidator<NotBlankWhenPresent, String?> {
    override fun isValid(value: String?, context: ConstraintValidatorContext) =
        value == null || value.isNotBlank()
}

/** Requires a duration supported by Spring's token settings builder (at least one whole second). */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [PositiveTokenLifetimeValidator::class])
annotation class PositiveTokenLifetime(
    val message: String = "must be at least one second",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PositiveTokenLifetimeValidator : ConstraintValidator<PositiveTokenLifetime, Duration?> {
    override fun isValid(value: Duration?, context: ConstraintValidatorContext) =
        value == null || (!value.isNegative && value.seconds > 0)
}

/** Validates combinations and syntax required to register an OAuth2 client. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidClientRegistrationValidator::class])
annotation class ValidClientRegistration(
    val message: String = "client registration has invalid protocol settings",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ValidClientRegistrationValidator : ConstraintValidator<ValidClientRegistration, CreateClientRequest> {
    override fun isValid(value: CreateClientRequest?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true

        val authenticationMethods = value.clientAuthenticationMethods
        val hasValidAuthenticationMethods =
            ClientAuthenticationMethod.NONE !in authenticationMethods ||
                    authenticationMethods == setOf(ClientAuthenticationMethod.NONE)
        val authorizationCodeNeedsRedirectUri =
            ClientAuthorizationGrantType.AUTHORIZATION_CODE !in value.authorizationGrantTypes ||
                    value.redirectUris.isNotEmpty()

        return hasValidAuthenticationMethods &&
                authorizationCodeNeedsRedirectUri &&
                value.redirectUris.all(::isValidRedirectUri) &&
                value.scopes.all(::isValidScope)
    }

    private fun isValidRedirectUri(uri: String): Boolean = try {
        URI(uri).fragment == null
    } catch (_: URISyntaxException) {
        false
    }

    private fun isValidScope(scope: String) =
        scope.isNotEmpty() && scope.all { character ->
            character.code == 0x21 || character.code in 0x23..0x5B || character.code in 0x5D..0x7E
        }
}

/** Ensures a delta never both adds and removes the same set member. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [DisjointSetChangesValidator::class])
annotation class DisjointSetChanges(
    val message: String = "a value cannot be both added and removed",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class DisjointSetChangesValidator : ConstraintValidator<DisjointSetChanges, SetChanges<*>> {
    override fun isValid(value: SetChanges<*>?, context: ConstraintValidatorContext) =
        value == null || value.add.none { it in value.remove }
}

/** Requires a client update to contain at least one effective change. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidClientUpdateValidator::class])
annotation class ValidClientUpdate(
    val message: String = "at least one field must contain a change",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ValidClientUpdateValidator : ConstraintValidator<ValidClientUpdate, UpdateClientRequest> {
    override fun isValid(value: UpdateClientRequest?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true

        return value.clientName != null ||
                !value.clientAuthenticationMethods.isEmptyOrNull() ||
                !value.authorizationGrantTypes.isEmptyOrNull() ||
                !value.redirectUris.isEmptyOrNull() ||
                !value.scopes.isEmptyOrNull() ||
                value.tokenSettings?.isEmpty() == false
    }

    private fun <T> SetChanges<T>?.isEmptyOrNull() = this == null || isEmpty()
}
