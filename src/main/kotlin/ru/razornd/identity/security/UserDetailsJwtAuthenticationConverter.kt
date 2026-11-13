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

package ru.razornd.identity.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AccountStatusUserDetailsChecker
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.FactorGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsChecker
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter

/**
 * Converts a validated JWT into an authentication for the user currently stored in the
 * user-details service.
 *
 * The JWT `sub` claim is treated as the username. A missing subject is rejected with a
 * [BadCredentialsException], and a subject that does not resolve to a user fails with the
 * exception from [UserDetailsService.loadUserByUsername]. The returned
 * [JwtAuthenticationToken] keeps the JWT as its token while exposing the loaded
 * [org.springframework.security.core.userdetails.UserDetails] as its principal.
 *
 * Authorities are combined from three sources:
 * - JWT scopes converted by [jwtGrantedAuthoritiesConverter] (by default, prefixed with
 *   `SCOPE_`);
 * - the user's current authorities loaded from [UserDetailsService];
 * - [FactorGrantedAuthority.BEARER_AUTHORITY], indicating bearer-token authentication.
 *
 * The loaded user's account status is checked by [accountStatusChecker] before the
 * authentication is created. Both collaborators are public and replaceable to support
 * application-specific account checks and JWT authority mapping. Configure them before
 * handling requests; do not mutate them while the converter is in use.
 *
 * @property userDetailsService loads the current user and authorities for the JWT subject
 * @property accountStatusChecker validates the loaded user's account status
 * @property jwtGrantedAuthoritiesConverter maps JWT claims, including scopes, to authorities
 */
class UserDetailsJwtAuthenticationConverter(
    private val userDetailsService: UserDetailsService,
) : Converter<Jwt, JwtAuthenticationToken> {
    var accountStatusChecker: UserDetailsChecker = AccountStatusUserDetailsChecker()
    var jwtGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>> =
        JwtGrantedAuthoritiesConverter()

    override fun convert(source: Jwt): JwtAuthenticationToken {
        val subject = source.subject ?: throw BadCredentialsException("JWT subject is missing")
        val userDetails = userDetailsService.loadUserByUsername(subject)
        accountStatusChecker.check(userDetails)

        val authorities = buildList {
            addAll(jwtGrantedAuthoritiesConverter.convert(source))
            addAll(userDetails.authorities)
            add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.BEARER_AUTHORITY))
        }

        return JwtAuthenticationToken(source, userDetails, authorities)
    }
}
