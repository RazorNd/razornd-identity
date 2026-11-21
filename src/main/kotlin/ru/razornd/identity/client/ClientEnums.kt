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
import org.springframework.security.oauth2.core.ClientAuthenticationMethod as SpringClientAuthenticationMethod

/** OAuth2 client authentication methods supported by Spring Authorization Server. */
enum class ClientAuthenticationMethod(@get:JsonValue val value: String) {
    CLIENT_SECRET_BASIC("client_secret_basic"),
    CLIENT_SECRET_POST("client_secret_post"),
    CLIENT_SECRET_JWT("client_secret_jwt"),
    PRIVATE_KEY_JWT("private_key_jwt"),
    TLS_CLIENT_AUTH("tls_client_auth"),
    SELF_SIGNED_TLS_CLIENT_AUTH("self_signed_tls_client_auth"),
    NONE("none");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String) = requireNotNull(entries.firstOrNull { it.value == value || it.name == value }) {
            "Unsupported client authentication method: $value"
        }
    }
}

/** OAuth2 grant types supported by Spring Authorization Server. */
enum class ClientAuthorizationGrantType(@get:JsonValue val value: String) {
    AUTHORIZATION_CODE("authorization_code"),
    CLIENT_CREDENTIALS("client_credentials"),
    REFRESH_TOKEN("refresh_token"),
    DEVICE_CODE("urn:ietf:params:oauth:grant-type:device_code"),
    JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer"),
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String) = requireNotNull(entries.firstOrNull { it.value == value || it.name == value }) {
            "Unsupported authorization grant type: $value"
        }
    }
}
