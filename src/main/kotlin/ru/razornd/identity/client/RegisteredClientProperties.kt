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

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration for additional server-owned OAuth2 clients under `identity.oauth2.clients`.
 *
 * Each map key is the registered client's internal repository ID; [Client.clientId] is the
 * OAuth2 client identifier presented by the client. The built-in CLI client is configured
 * separately in [RegisteredClientRepositoryConfiguration].
 */
@ConfigurationProperties("identity.oauth2")
class RegisteredClientProperties {
    /** Additional clients keyed by their internal registered-client ID. */
    var clients: Map<String, Client> = emptyMap()

    /**
     * Configuration for one server-owned OAuth2 client.
     *
     * Optional client and token settings are only applied when specified; otherwise the
     * Spring Authorization Server defaults are retained.
     */
    data class Client(
        var clientId: String,
        var clientSecret: String? = null,
        var clientName: String? = null,
        var clientAuthenticationMethods: Set<String> = emptySet(),
        var authorizationGrantTypes: Set<String> = emptySet(),
        var redirectUris: Set<String> = emptySet(),
        var postLogoutRedirectUris: Set<String> = emptySet(),
        var scopes: Set<String> = emptySet(),

        var requireProofKey: Boolean? = null,
        var requireAuthorizationConsent: Boolean? = null,

        /** Optional token settings; null values leave the corresponding server default unchanged. */
        var authorizationCodeTimeToLive: Duration? = null,
        var accessTokenTimeToLive: Duration? = null,
        var accessTokenFormat: String? = null,
        var deviceCodeTimeToLive: Duration? = null,
        var reuseRefreshTokens: Boolean? = null,
        var refreshTokenTimeToLive: Duration? = null
    )
}
