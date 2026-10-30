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

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.security.jackson.SecurityJacksonModules
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
class SessionConfiguration {
    @Bean
    fun springSessionDefaultRedisSerializer(): RedisSerializer<Any> {
        val mapper = JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(javaClass.classLoader))
            .build()

        return GenericJacksonJsonRedisSerializer(mapper)
    }
}
