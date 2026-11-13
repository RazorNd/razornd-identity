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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/** Provides an SLF4J logger associated with the implementing class. */
interface Loggable {
    /** Logger named after the class that requested it. */
    val log: Logger
}

private class DefaultLoggable(name: KClass<*>) : Loggable {
    override val log: Logger = LoggerFactory.getLogger(name.java)
}

/** Creates a [Loggable] whose logger is named after [klass]. */
fun loggable(klass: KClass<*>): Loggable = DefaultLoggable(klass)

/** Creates a [Loggable] whose logger is named after the reified type [T]. */
inline fun <reified T> withLogger(): Loggable = loggable(T::class)
