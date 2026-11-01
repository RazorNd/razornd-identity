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

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

@Controller
class OAuthController(
    private val clientRepository: RegisteredClientRepository,
    private val consentService: OAuth2AuthorizationConsentService,
) {
    @GetMapping("/oauth2/consent")
    fun consent(
        @RequestParam("client_id") clientId: String,
        @RequestParam("scope") scope: String,
        @RequestParam("state") state: String,
        principal: Principal,
    ): ModelAndView {
        val client = requireNotNull(clientRepository.findByClientId(clientId))
        val previouslyAuthorizedScopes = consentService.findById(client.id, principal.name)
            ?.authorities
            ?.mapNotNull { it.authority?.removePrefix("SCOPE_") }
            ?.toSet()
            .orEmpty()

        return ModelAndView(
            "consent",
            mapOf(
                "clientId" to clientId,
                "clientName" to client.clientName,
                "principalName" to principal.name,
                "scopes" to scope.split(" "),
                "state" to state,
                "previouslyAuthorizedScopes" to (previouslyAuthorizedScopes + "openid").toList(),
            )
        )
    }
}
