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

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.util.MultiValueMap
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

@AutoConfigureRestTestClient
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "spring.security.oauth2.authorizationserver.client.test.registration.client-id=test-client",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-secret={noop}test-client-secret",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-name=test-client",
        "spring.security.oauth2.authorizationserver.client.test.registration.client-authentication-methods=client_secret_basic,client_secret_post",
        "spring.security.oauth2.authorizationserver.client.test.registration.authorization-grant-types=authorization_code,refresh_token,client_credentials",
        "spring.security.oauth2.authorizationserver.client.test.registration.redirect-uris=http://127.0.0.1:8080/login/oauth2/code/test",
        "spring.security.oauth2.authorizationserver.client.test.registration.scopes=openid,profile",
        "spring.security.oauth2.authorizationserver.client.test.require-authorization-consent=true"
    ]
)
@Import(TestcontainersConfiguration::class, PlaywrightConfiguration::class)
@Sql("classpath:sql/create-test-user.sql")
@EnableOAuthAuthorizationTestSupport
class IdentityApplicationTests {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var client: RestTestClient

    @Test
    fun authorizationCodeFlowReturnsTokens(@Autowired page: Page) {
        val verifier = base64Url(ByteArray(32).also(SecureRandom()::nextBytes))
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val authorizationUrl = "http://localhost:$port/oauth2/authorize?response_type=code&client_id=test-client" +
                "&redirect_uri=http%3A%2F%2F127.0.0.1%3A8080%2Flogin%2Foauth2%2Fcode%2Ftest" +
                "&scope=openid%20profile&state=functional-test&nonce=functional-test" +
                "&code_challenge=$challenge&code_challenge_method=S256"

        var callbackUrl: String? = null
        page.onResponse { response ->
            response.headers()["location"]
                ?.takeIf { it.startsWith("http://127.0.0.1:8080/login/oauth2/code/test") }
                ?.let { callbackUrl = it }
        }
        page.navigate(authorizationUrl)
        page.fill("input[name=username]", "test-user")
        page.fill("input[name=password]", "test-user-passowrd")
        page.click("button[type=submit]")

        assertThat(page.getByText("Consent required")).isVisible()
        page.check("input[name=scope]")
        page.click("button#submit-consent")

        val callback = URI(callbackUrl ?: page.url())
        val code = callback.query.split('&')
            .firstOrNull { it.startsWith("code=") }
            ?.substringAfter("code=")
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?: error("Authorization code missing from redirect: ${page.url()}")

        client.post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth("test-client", "test-client-secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                MultiValueMap.fromSingleValue(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to "http://127.0.0.1:8080/login/oauth2/code/test",
                        "code_verifier" to verifier
                    )
                )
            )
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.access_token").isNotEmpty
            .jsonPath("$.refresh_token").isNotEmpty
            .jsonPath("$.id_token").isNotEmpty
            .jsonPath("$.scope").isEqualTo("openid profile")
    }

    @Test
    fun clientCredentialsGrantReturnsAccessToken() {
        client.post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth("test-client", "test-client-secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(MultiValueMap.fromSingleValue(mapOf("grant_type" to "client_credentials", "scope" to "profile")))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.access_token").isNotEmpty
            .jsonPath("$.token_type").isEqualTo("Bearer")
            .jsonPath("$.scope").isEqualTo("profile")
    }

    @Test
    fun openIdProviderConfigurationListsEndpoints() {
        client.get()
            .uri("/.well-known/openid-configuration")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.issuer").isNotEmpty
            .jsonPath("$.authorization_endpoint").isEqualTo("http://localhost:$port/oauth2/authorize")
            .jsonPath("$.token_endpoint").isEqualTo("http://localhost:$port/oauth2/token")
            .jsonPath("$.userinfo_endpoint").isEqualTo("http://localhost:$port/userinfo")
            .jsonPath("$.end_session_endpoint").isEqualTo("http://localhost:$port/connect/logout")
    }

    @Test
    fun oauthAuthorizationServerMetadataListsGrantTypes() {
        client.get()
            .uri("/.well-known/oauth-authorization-server")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.issuer").isNotEmpty
            .jsonPath("$.token_endpoint").isEqualTo("http://localhost:$port/oauth2/token")
            .jsonPath("$.grant_types_supported").value<List<String>> {
                org.assertj.core.api.Assertions.assertThat(it)
                    .contains("authorization_code", "client_credentials", "refresh_token")
            }
    }

    @Test
    fun jsonWebKeySetReturnsSigningKeys() {
        client.get()
            .uri("/oauth2/jwks")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.keys").isNotEmpty
    }

    @Test
    fun userInfoRequiresBearerToken() {
        client.get()
            .uri("/userinfo")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @WithOAuthAuthorization(registeredClientId = "test-client")
    fun refreshTokenGrantReturnsTokens(refreshToken: OAuth2RefreshToken) {
        client.post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth("test-client", "test-client-secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                MultiValueMap.fromSingleValue(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken.tokenValue
                    )
                )
            )
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.access_token").isNotEmpty
            .jsonPath("$.refresh_token").isNotEmpty
    }

    @Test
    @WithOAuthAuthorization(accessTokenValue = "introspection-test-access-token")
    fun tokenIntrospectionReturnsActiveForStoredAccessToken(accessToken: OAuth2AccessToken) {
        client.post()
            .uri("/oauth2/introspect")
            .headers { it.setBasicAuth("test-client", "test-client-secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(MultiValueMap.fromSingleValue(mapOf("token" to accessToken.tokenValue)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.active").isEqualTo(true)
            .jsonPath("$.client_id").isEqualTo("test-client")
    }

    @Test
    @WithOAuthAuthorization(accessTokenValue = "revocation-test-access-token")
    fun tokenRevocationMakesAccessTokenInactive(accessToken: OAuth2AccessToken) {
        client.post()
            .uri("/oauth2/revoke")
            .headers { it.setBasicAuth("test-client", "test-client-secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                MultiValueMap.fromSingleValue(
                    mapOf("token" to accessToken.tokenValue, "token_type_hint" to "access_token")
                )
            )
            .exchange()
            .expectStatus().isOk()

        client.post()
            .uri("/oauth2/introspect")
            .headers { it.setBasicAuth("test-client", "test-client-secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(MultiValueMap.fromSingleValue(mapOf("token" to accessToken.tokenValue)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.active").isEqualTo(false)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

}
