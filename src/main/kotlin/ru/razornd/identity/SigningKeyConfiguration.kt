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

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyStore

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SigningKeyProperties::class)
class SigningKeyConfiguration {
    @Bean
    fun jwkSource(properties: SigningKeyProperties, resourceLoader: ResourceLoader): JWKSource<SecurityContext> {
        require(properties.location.isNotBlank()) { "identity.signing-key.location must be configured" }
        require(properties.activeKeyId.isNotBlank()) { "identity.signing-key.active-key-id must be configured" }

        val storePassword = properties.storePassword.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        resourceLoader.getResource(properties.location).inputStream.use { keyStore.load(it, storePassword) }

        val keySet = JWKSet.load(keyStore) { alias ->
            (properties.keyPasswords[alias] ?: properties.storePassword).toCharArray()
        }

        val activeKey = keySet.getKeyByKeyId(properties.activeKeyId)
        require(activeKey != null && activeKey.isPrivate) {
            "Active signing key '${properties.activeKeyId}' must exist in PKCS#12 and contain a private key"
        }

        return ImmutableJWKSet(keySet)
    }

    @Bean
    fun jwtEncoder(
        jwkSource: JWKSource<SecurityContext>,
        properties: SigningKeyProperties,
    ): NimbusJwtEncoder = NimbusJwtEncoder(jwkSource).apply {
        setJwkSelector { matchingKeys ->
            matchingKeys.first { it.keyID == properties.activeKeyId }
        }
    }
}
