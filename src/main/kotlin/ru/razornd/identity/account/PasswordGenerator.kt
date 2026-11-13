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

package ru.razornd.identity.account

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Generates an initial password for accounts created without a caller-supplied password.
 *
 * @property length number of characters generated on the next call
 * @property alphabet characters from which generated passwords are composed
 */
interface PasswordGenerator {
    var length: Int
    var alphabet: String

    fun generate(): String
}

/** Generates passwords from a configurable alphabet using a cryptographically secure random source. */
@Component
class SecurePasswordGenerator : PasswordGenerator {
    override var length: Int = DEFAULT_LENGTH
    override var alphabet: String = DEFAULT_ALPHABET

    private val secureRandom = SecureRandom()

    override fun generate(): String {
        val configuredLength = length
        val configuredAlphabet = alphabet
        require(configuredLength > 0) { "Password length must be greater than zero" }
        require(configuredAlphabet.isNotEmpty()) { "Password alphabet must not be empty" }

        return buildString(configuredLength) {
            repeat(configuredLength) {
                append(configuredAlphabet[secureRandom.nextInt(configuredAlphabet.length)])
            }
        }
    }

    private companion object {
        const val DEFAULT_LENGTH = 32
        const val DEFAULT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-/*@!#$%&"
    }
}
