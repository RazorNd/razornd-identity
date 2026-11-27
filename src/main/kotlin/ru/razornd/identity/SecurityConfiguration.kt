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
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.DisabledException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.JdbcUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsUtils
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import ru.razornd.identity.oauth.ScopeRegistry
import ru.razornd.identity.security.UserDetailsJwtAuthenticationConverter
import javax.sql.DataSource
import kotlin.reflect.jvm.jvmName

@Configuration
class SecurityConfiguration {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun userDetailsManager(dataSource: DataSource) = JdbcUserDetailsManager(dataSource).apply {
        setEnableUpdatePassword(true)
    }

    @Bean
    fun apiJwtAuthenticationConverter(userDetailsService: UserDetailsService) =
        UserDetailsJwtAuthenticationConverter(userDetailsService)

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity, scopeRegistry: ScopeRegistry): SecurityFilterChain {
        val publicMetadataEndpoints = listOf(
            "/.well-known/openid-configuration",
            "/.well-known/oauth-authorization-server",
            "/oauth2/jwks",
        )
        val corsEndpoints = publicMetadataEndpoints + listOf("/oauth2/token", "/userinfo")
        val corsPaths = OrRequestMatcher(
            corsEndpoints.map { PathPatternRequestMatcher.withDefaults().matcher(it) }
        )
        http.oauth2AuthorizationServer { authorizationServer ->
            http.securityMatcher(
                OrRequestMatcher(
                    authorizationServer.endpointsMatcher,
                    { request -> CorsUtils.isPreFlightRequest(request) && corsPaths.matches(request) },
                )
            )
            authorizationServer.oidc { oidc ->
                oidc.providerConfigurationEndpoint { endpoint ->
                    endpoint.providerConfigurationCustomizer { configuration ->
                        configuration.scopes { scopes ->
                            scopes.clear()
                            scopes.addAll(scopeRegistry.findAll().map { it.name })
                        }
                    }
                }
            }
            authorizationServer.authorizationEndpoint { endpoint ->
                endpoint.consentPage("/oauth2/consent")
            }
        }

        http {
            authorizeHttpRequests {
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer { jwt { } }
            cors {
                configurationSource = UrlBasedCorsConfigurationSource().apply {
                    val metadataCors = CorsConfiguration().apply {
                        allowedOrigins = listOf("*")
                        allowedMethods = listOf("GET")
                    }
                    publicMetadataEndpoints.forEach { registerCorsConfiguration(it, metadataCors) }
                    registerCorsConfiguration("/oauth2/token", CorsConfiguration().apply {
                        allowedOrigins = listOf("*")
                        allowedMethods = listOf("POST")
                        allowedHeaders = listOf("Content-Type")
                    })
                    registerCorsConfiguration("/userinfo", CorsConfiguration().apply {
                        allowedOrigins = listOf("*")
                        allowedMethods = listOf("GET", "POST")
                        allowedHeaders = listOf("Authorization", "Content-Type")
                    })
                }
            }
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
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        apiJwtAuthenticationConverter: UserDetailsJwtAuthenticationConverter,
    ): SecurityFilterChain {
        http {
            securityMatcher("/api/**")
            authorizeHttpRequests {
                authorize("/api/admin/accounts/**", hasAllAuthorities("ROLE_ADMIN", "SCOPE_accounts:admin"))
                authorize("/api/admin/clients/**", hasAllAuthorities("ROLE_ADMIN", "SCOPE_clients:admin"))
                authorize("/api/admin/**", hasRole("ADMIN"))
                authorize(anyRequest, authenticated)
            }
            csrf { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            oauth2ResourceServer {
                jwt {
                    jwtAuthenticationConverter = apiJwtAuthenticationConverter
                }
            }
        }

        return http.build()
    }

    @Bean
    @Order(SecurityFilterProperties.BASIC_AUTH_ORDER)
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher(
                NegatedRequestMatcher(
                    OrRequestMatcher(
                        PathRequest.toStaticResources().at(CSS, IMAGES, JAVA_SCRIPT),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                    )
                )
            )
            authorizeHttpRequests {
                authorize("/error", permitAll)
                authorize(HttpMethod.GET, "/login", permitAll)
                authorize(HttpMethod.GET, "/account-disabled", permitAll)
                authorize(anyRequest, authenticated)
            }
            cors { }
            csrf { }
            formLogin {
                loginPage = "/login"
                authenticationFailureHandler = ExceptionMappingAuthenticationFailureHandler().apply {
                    setDefaultFailureUrl("/login?error")
                    setExceptionMappings(
                        mapOf(DisabledException::class.jvmName to "/account-disabled")
                    )
                }
            }
            logout {
                logoutUrl = "/logout"
            }
            passwordManagement { }
        }

        return http.build()
    }
}
