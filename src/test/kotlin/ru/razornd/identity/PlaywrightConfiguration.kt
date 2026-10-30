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

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy
import org.springframework.core.env.Environment
import java.nio.file.Files

@TestConfiguration(proxyBeanMethods = false)
@Import(PlaywrightFailureArtifactListener::class)
class PlaywrightConfiguration {
    @Bean(destroyMethod = "close")
    fun playwright(): Playwright = Playwright.create()

    @Bean(destroyMethod = "close")
    fun browser(playwright: Playwright): Browser = playwright.chromium().launch()

    @Bean
    @Lazy
    fun page(browser: Browser, environment: Environment): Page {
        val port = environment.getRequiredProperty("local.server.port")
        val videoDirectory = Files.createTempDirectory("identity-playwright-")
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setBaseURL("http://localhost:$port/")
                .setRecordVideoDir(videoDirectory)
                .setRecordVideoSize(640, 480)
                .setViewportSize(640, 480)
        )
        return context.newPage().apply { setDefaultTimeout(5_000.0) }
    }
}
