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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration

/** Wire-level access-token format supported by Spring Authorization Server. */
enum class ClientAccessTokenFormat(@get:JsonValue val value: String) {
    SELF_CONTAINED("self-contained"),
    REFERENCE("reference");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): ClientAccessTokenFormat =
            entries.firstOrNull { it.value == value || it.name == value }
                ?: throw IllegalArgumentException("Unsupported access token format: $value")
    }

    /** Converts this API format to Spring Authorization Server's token format. */
    fun toSpringFormat(): OAuth2TokenFormat = when (this) {
        SELF_CONTAINED -> OAuth2TokenFormat.SELF_CONTAINED
        REFERENCE -> OAuth2TokenFormat.REFERENCE
    }
}

/** Token lifetimes and refresh-token behavior configured for a client. */
data class ClientTokenSettings(
    val authorizationCodeTimeToLive: Duration = Duration.ofMinutes(5),
    val accessTokenTimeToLive: Duration = Duration.ofMinutes(5),
    val accessTokenFormat: ClientAccessTokenFormat = ClientAccessTokenFormat.SELF_CONTAINED,
    val reuseRefreshTokens: Boolean = true,
    val refreshTokenTimeToLive: Duration = Duration.ofMinutes(60),
)

/** Optional token settings supplied during client creation. */
data class ClientTokenSettingsRequest(
    @field:PositiveTokenLifetime
    val authorizationCodeTimeToLive: Duration? = null,
    @field:PositiveTokenLifetime
    val accessTokenTimeToLive: Duration? = null,
    val accessTokenFormat: ClientAccessTokenFormat? = null,
    val reuseRefreshTokens: Boolean? = null,
    @field:PositiveTokenLifetime
    val refreshTokenTimeToLive: Duration? = null,
) {
    fun isEmpty() =
        authorizationCodeTimeToLive == null &&
                accessTokenTimeToLive == null &&
                accessTokenFormat == null &&
                reuseRefreshTokens == null &&
                refreshTokenTimeToLive == null
}
