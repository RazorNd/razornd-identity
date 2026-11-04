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

import org.springframework.boot.security.autoconfigure.web.StaticResourceLocation.*
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.provisioning.JdbcUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import javax.sql.DataSource

@Configuration
class SecurityConfiguration {
    @Bean
    fun userDetailsManager(dataSource: DataSource) = JdbcUserDetailsManager(dataSource).apply {
        setEnableUpdatePassword(true)
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.oauth2AuthorizationServer { authorizationServer ->
            http.securityMatcher(authorizationServer.endpointsMatcher)
            authorizationServer.oidc { }
            authorizationServer.authorizationEndpoint { endpoint ->
                endpoint.consentPage("/oauth2/consent")
            }
        }

        http {
            authorizeHttpRequests {
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer { jwt { } }
            exceptionHandling {
                defaultAuthenticationEntryPointFor(
                    entryPoint = LoginUrlAuthenticationEntryPoint("/login"),
                    preferredMatcher = MediaTypeRequestMatcher(MediaType.TEXT_HTML).apply {
                        setIgnoredMediaTypes(setOf(MediaType.ALL))
                    }
                )
            }
        }

        return http.build()
    }

    @Bean
    @Order(SecurityFilterProperties.BASIC_AUTH_ORDER)
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher(NegatedRequestMatcher(PathRequest.toStaticResources().at(CSS, IMAGES, JAVA_SCRIPT)))
            authorizeHttpRequests {
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }
            cors { }
            csrf { }
            formLogin {
                loginPage = "/login"
                permitAll()
            }
            logout {
                logoutUrl = "/logout"
            }
            passwordManagement { }
        }

        return http.build()
    }
}
