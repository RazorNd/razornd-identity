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

import java.time.Duration
import java.time.Instant

/** Describes when a Redis entry expires. */
sealed interface Expiration {
    /** Expires at an absolute instant. */
    data class At(val expiredAt: Instant) : Expiration

    /** Expires after the given duration from the time it is written. */
    data class After(val expiredDuration: Duration) : Expiration
}
