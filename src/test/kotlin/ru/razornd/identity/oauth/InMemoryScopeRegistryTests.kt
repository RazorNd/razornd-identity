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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.OidcScopes

class InMemoryScopeRegistryTests {

    @Test
    fun `includes standard and configured scopes`() {
        val customScope = ScopeDefinition("orders.read", "Read orders", "Allows reading orders.")
        val registry = InMemoryScopeRegistry(listOf(customScope))

        assertThat(registry.find(OidcScopes.OPENID))
            .isEqualTo(
                ScopeDefinition(
                    OidcScopes.OPENID,
                    "OpenID Connect sign-in",
                    "Allows the application to verify your identity.",
                    consentVisible = false,
                )
            )
        assertThat(registry.find("accounts:admin"))
            .isEqualTo(
                ScopeDefinition(
                    "accounts:admin",
                    "Manage accounts",
                    "Allows the application to create, view, update, and disable user accounts.",
                )
            )
        assertThat(registry.find("clients:admin"))
            .isEqualTo(
                ScopeDefinition(
                    "clients:admin",
                    "Manage OAuth2 clients",
                    "Allows the application to create, view, update, and delete OAuth2 clients.",
                )
            )
        assertThat(registry.requireAll(listOf("orders.read", OidcScopes.PROFILE)))
            .containsExactly(customScope, registry.find(OidcScopes.PROFILE))
    }

    @Test
    fun `rejects unsupported scopes`() {
        val registry = InMemoryScopeRegistry(emptyList())

        assertThatIllegalArgumentException()
            .isThrownBy { registry.requireAll(listOf("unknown")) }
            .withMessageContaining("unknown")
    }

    @Test
    fun `rejects configured standard scopes`() {
        val openid = ScopeDefinition(OidcScopes.OPENID, "Custom OpenID", "Custom description")

        assertThatIllegalArgumentException()
            .isThrownBy { InMemoryScopeRegistry(listOf(openid)) }
            .withMessageContaining("Standard OIDC scopes must not be configured")
    }

    @Test
    fun `rejects duplicate configured scopes`() {
        val scope = ScopeDefinition("orders.read", "Read orders", "Allows reading orders.")

        assertThatIllegalArgumentException()
            .isThrownBy { InMemoryScopeRegistry(listOf(scope, scope)) }
            .withMessageContaining("unique names")
    }
}
