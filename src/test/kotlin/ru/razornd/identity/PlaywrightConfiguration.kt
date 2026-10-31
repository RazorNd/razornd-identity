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
import org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.core.env.Environment
import java.nio.file.Files

@TestConfiguration(proxyBeanMethods = false)
@Import(PlaywrightPageListener::class)
class PlaywrightConfiguration {
    @Bean(destroyMethod = "close")
    fun playwright(): Playwright = Playwright.create()

    @Bean(destroyMethod = "close")
    fun browser(playwright: Playwright): Browser = playwright.chromium().launch()

    @Bean
    @Scope(SCOPE_PROTOTYPE)
    fun page(browser: Browser, environment: Environment, pagesHolder: PagesHolder): Page {
        val port = environment.getRequiredProperty("local.server.port")
        val videoDirectory = Files.createTempDirectory("identity-playwright-")
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setBaseURL("http://localhost:$port/")
                .setRecordVideoDir(videoDirectory)
                .setRecordVideoSize(640, 480)
                .setViewportSize(640, 480)
        )
        return context.newPage().apply { setDefaultTimeout(5_000.0) }.also { pagesHolder.addPage(it) }
    }

    @Bean
    fun pagesHolder() = PagesHolder()

    class PagesHolder {
        private val storage = mutableListOf<Page>()

        val pages: List<Page> get() = storage.toList()

        internal fun addPage(page: Page) {
            storage.add(page)
        }

        fun closeAllPages() = storage.forEach { it.close() }

        fun clear() = storage.clear()
    }
}
