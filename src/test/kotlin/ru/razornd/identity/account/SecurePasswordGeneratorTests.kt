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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SecurePasswordGeneratorTests {
    private val passwordGenerator = SecurePasswordGenerator()

    @Test
    fun `generates passwords with the expected length and characters`() {
        val password = passwordGenerator.generate()

        assertThat(password).hasSize(passwordGenerator.length)
        assertThat(password.all { it in passwordGenerator.alphabet }).isTrue()
    }

    @Test
    fun `generates different passwords on successive calls`() {
        val first = passwordGenerator.generate()
        val second = passwordGenerator.generate()

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `uses configured length and alphabet`() {
        passwordGenerator.length = 12
        passwordGenerator.alphabet = "xy"

        assertThat(passwordGenerator.generate()).matches("[xy]{12}")
    }

    @Test
    fun `rejects a non-positive configured length`() {
        passwordGenerator.length = 0

        assertThatThrownBy { passwordGenerator.generate() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Password length must be greater than zero")
    }

    @Test
    fun `rejects an empty configured alphabet`() {
        passwordGenerator.alphabet = ""

        assertThatThrownBy { passwordGenerator.generate() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Password alphabet must not be empty")
    }
}
