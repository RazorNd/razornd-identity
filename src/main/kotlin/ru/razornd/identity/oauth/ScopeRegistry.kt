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

/** Looks up the scope definitions supported by this identity server. */
interface ScopeRegistry {
    /** Returns the definition for [name], or `null` when the scope is unsupported. */
    fun find(name: String): ScopeDefinition?

    /**
     * Returns definitions for all [names], in the same order.
     *
     * @throws IllegalArgumentException if any scope is unsupported.
     */
    fun requireAll(names: Collection<String>): List<ScopeDefinition>
}
