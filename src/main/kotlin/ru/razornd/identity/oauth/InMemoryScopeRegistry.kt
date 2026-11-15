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

package ru.razornd.identity.oauth

import org.springframework.security.oauth2.core.oidc.OidcScopes

/**
 * In-memory [ScopeRegistry] combining built-in OIDC scopes with configured custom scopes.
 *
 * Built-in OIDC scope definitions are owned by the application and cannot be overridden.
 *
 * @throws IllegalArgumentException if configured scope names are duplicated or use a built-in OIDC name.
 */
class InMemoryScopeRegistry(configuredScopes: Collection<ScopeDefinition>) : ScopeRegistry {

    private val scopes: Map<String, ScopeDefinition>

    init {
        val reservedNames = standardScopes.map { it.name }.toSet()
        val configuredNames = configuredScopes.map { it.name }

        require(configuredNames.size == configuredNames.toSet().size) {
            "Configured OAuth2 scopes must have unique names"
        }
        require(configuredNames.none { it in reservedNames }) {
            "Standard OIDC scopes must not be configured: ${
                configuredNames.filter { it in reservedNames }.joinToString()
            }"
        }

        scopes = (standardScopes + configuredScopes).associateBy { it.name }
    }

    override fun find(name: String): ScopeDefinition? = scopes[name]

    override fun requireAll(names: Collection<String>): List<ScopeDefinition> {
        val unknownScopes = names.filterNot(scopes::containsKey)
        require(unknownScopes.isEmpty()) {
            "Unsupported OAuth2 scopes: ${unknownScopes.joinToString()}"
        }

        return names.map(scopes::getValue)
    }

    private companion object {
        val standardScopes = listOf(
            ScopeDefinition(
                name = OidcScopes.OPENID,
                displayName = "OpenID Connect sign-in",
                description = "Allows the application to verify your identity.",
                consentVisible = false,
            ),
            ScopeDefinition(
                name = OidcScopes.PROFILE,
                displayName = "Profile",
                description = "Allows the application to access your basic profile information.",
            ),
            ScopeDefinition(
                name = OidcScopes.EMAIL,
                displayName = "Email address",
                description = "Allows the application to access your email address and whether it is verified.",
            ),
            ScopeDefinition(
                name = OidcScopes.ADDRESS,
                displayName = "Address",
                description = "Allows the application to access your address.",
            ),
            ScopeDefinition(
                name = OidcScopes.PHONE,
                displayName = "Phone number",
                description = "Allows the application to access your phone number and whether it is verified.",
            ),
        )
    }
}
