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

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.beans.factory.getBean
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.context.junit.jupiter.SpringExtension

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(WithOAuthAuthorizationConsentExtension::class)
annotation class WithOAuthAuthorizationConsent(
    val registeredClientId: String = "test-client",
    val principalName: String = "test-user",
    val scopes: Array<String>,
)

class WithOAuthAuthorizationConsentExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val annotation = context.requiredTestMethod.getAnnotation(WithOAuthAuthorizationConsent::class.java)
            ?: return
        val applicationContext = SpringExtension.getApplicationContext(context)
        val clientRepository = applicationContext.getBean<RegisteredClientRepository>()
        val client = checkNotNull(clientRepository.findByClientId(annotation.registeredClientId)) {
            "Registered OAuth2 client '${annotation.registeredClientId}' is not configured"
        }
        val consent = OAuth2AuthorizationConsent.withId(client.id, annotation.principalName)
            .apply { annotation.scopes.forEach { scope -> scope(scope) } }
            .build()
        applicationContext.getBean<OAuth2AuthorizationConsentService>().save(consent)
    }
}
