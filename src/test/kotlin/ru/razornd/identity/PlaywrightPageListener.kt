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

import com.microsoft.playwright.Page
import org.springframework.boot.test.context.TestComponent
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.test.context.event.AfterTestExecutionEvent
import org.springframework.test.context.event.BeforeTestClassEvent
import org.springframework.test.context.event.annotation.AfterTestExecution
import org.springframework.test.context.event.annotation.BeforeTestClass
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@TestComponent
class PlaywrightPageListener(
    private val pagesHolder: PlaywrightConfiguration.PagesHolder
) {

    @BeforeTestClass
    fun clearPreviousReports(event: BeforeTestClassEvent) {
        val directory = reportDirectory(event.testContext.testClass)
        if (!Files.exists(directory)) return

        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Order(5)
    @AfterTestExecution
    fun closePage() {
        pagesHolder.closeAllPages()
    }

    @Order(0)
    @AfterTestExecution
    fun captureScreenshotOnFailure(event: AfterTestExecutionEvent) {
        if (event.testContext.testException == null) return
        pagesHolder.pages.forEachIndexed { index, page ->
            page.screenshot(
                Page.ScreenshotOptions()
                    .setPath(saveDirectory(event).resolve("${event.source.testMethod.name}-$index.png"))
                    .setFullPage(true)
            )
        }
    }

    @Order(10)
    @AfterTestExecution("#event.testContext.testException != null or @environment.getProperty('playwright.allways-save-video', T(Boolean), false )")
    fun saveVideoOnFailure(event: AfterTestExecutionEvent) {
        pagesHolder.pages.forEachIndexed { index, page ->
            page.video()?.saveAs(saveDirectory(event).resolve("${event.source.testMethod.name}-$index.webm"))
        }
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @AfterTestExecution
    fun clearPageHolder() {
        pagesHolder.clear()
    }

    private fun saveDirectory(event: AfterTestExecutionEvent) = reportDirectory(event.source.testClass)
        .also { Files.createDirectories(it) }

    private fun reportDirectory(testClass: Class<*>) = Path.of("build/reports/playwright")
        .resolve(testClass.name)
}
