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

import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(
    OAuthController::class,
    properties = [
        "identity.oauth2.scopes[0].name=orders.read",
        "identity.oauth2.scopes[0].display-name=Read orders",
        "identity.oauth2.scopes[0].description=Allows the application to view your orders.",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-id=test-client",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-secret={noop}test-secret",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-name=Test application",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.test.registration.authorization-grant-types=authorization_code",
        "spring.security.oauth2.authorizationserver.client.test.registration.redirect-uris=https://client.example/callback",
        "spring.security.oauth2.authorizationserver.client.test.registration.scopes=openid,profile,orders.read",
    ],
)
@Import(OAuth2ScopeConfiguration::class)
@ImportAutoConfiguration(
    OAuth2AuthorizationServerAutoConfiguration::class,
    OAuth2AuthorizationServerJwtAutoConfiguration::class,
)
class OAuthControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var consentService: OAuth2AuthorizationConsentService

    @Test
    @WithMockUser(username = "test-user")
    fun `consent view receives client details and visible scope definitions`() {
        val consent = OAuth2AuthorizationConsent.withId("test-client", "test-user")
            .authorities {
                it.add(SimpleGrantedAuthority("SCOPE_openid"))
                it.add(SimpleGrantedAuthority("SCOPE_profile"))
            }
            .build()

        doReturn(consent).whenever(consentService).findById("test-client", "test-user")

        mockMvc.get("/oauth2/consent") {
            param("client_id", "test-client")
            param("scope", "openid profile orders.read")
            param("state", "consent-state")
        }.andExpect {
            status { isOk() }
            view { name("consent") }
            model {
                attribute("clientId", "test-client")
                attribute("clientName", "Test application")
                attribute("principalName", "test-user")
                attribute("state", "consent-state")
                attribute(
                    "scopes",
                    listOf(
                        ScopeDefinition(
                            name = "profile",
                            displayName = "Profile",
                            description = "Allows the application to access your basic profile information.",
                        ),
                        ScopeDefinition(
                            name = "orders.read",
                            displayName = "Read orders",
                            description = "Allows the application to view your orders.",
                        ),
                    ),
                )
                attribute("hiddenScopes", listOf("openid"))
                attribute("previouslyAuthorizedScopes", listOf("openid", "profile"))
            }
        }
    }
}
