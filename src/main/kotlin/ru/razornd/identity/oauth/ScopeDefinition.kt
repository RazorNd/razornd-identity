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

/** Describes an OAuth2 scope supported by this identity server. */
data class ScopeDefinition(
    /** Scope identifier sent by OAuth2 clients. */
    val name: String,
    /** English name suitable for display to an end user. */
    val displayName: String,
    /** English explanation of the access requested by the scope. */
    val description: String,
    /** Whether the scope should be shown as a permission on the consent page. */
    val consentVisible: Boolean = true,
)
