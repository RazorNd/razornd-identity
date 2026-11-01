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

package ru.razornd.identity.oauth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.jackson.SecurityJacksonModules
import tools.jackson.databind.json.JsonMapper

/** Configures Redis serialization for OAuth 2.0 authorization snapshots. */
@Configuration(proxyBeanMethods = false)
class OAuth2AuthorizationRedisConfiguration {

    /** Creates the Redis template used to store [OAuth2AuthorizationData] values. */
    @Bean
    fun oauth2AuthorizationRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        mapper: JsonMapper,
    ): RedisTemplate<String, OAuth2AuthorizationData> {
        val authorizationSerializer = JacksonJsonRedisSerializer(mapper, OAuth2AuthorizationData::class.java)

        return RedisTemplate<String, OAuth2AuthorizationData>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = authorizationSerializer
        }
    }

    /** Creates the Redis template used to store consent authority sets. */
    @Bean
    fun oauth2AuthorizationConsentRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        mapperBuilder: JsonMapper.Builder,
    ): RedisTemplate<String, Set<GrantedAuthority>> {
        val mapper = mapperBuilder
            .addModules(SecurityJacksonModules.getModules(javaClass.classLoader))
            .build()

        return RedisTemplate<String, Set<GrantedAuthority>>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJacksonJsonRedisSerializer(mapper)
        }
    }
}
