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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.test.context.event.AfterTestExecutionEvent
import java.nio.file.Files
import java.nio.file.Path

@TestComponent
class PlaywrightFailureArtifactListener(
    private val beanFactory: ConfigurableListableBeanFactory,
    private val environment: Environment
) {

    private val page: Page?
        get() {
            if (!beanFactory.containsSingleton("page")) return null
            return beanFactory.getBean<Page>("page")
        }


    @Order(5)
    @EventListener(AfterTestExecutionEvent::class)
    fun closePage() {
        page?.close()
    }

    @Order(0)
    @EventListener(condition = "#event.testContext.testException != null")
    fun captureScreenshotOnFailure(event: AfterTestExecutionEvent) {
        page?.screenshot(
            Page.ScreenshotOptions()
                .setPath(saveDirectory(event).resolve("${event.source.testMethod.name}.png"))
                .setFullPage(true)
        )
    }

    @Order(10)
    @EventListener(condition = "#event.testContext.testException != null or @environment.getProperty('playwright.allways-save-video', T(Boolean), false )")
    fun saveVideoOnFailure(event: AfterTestExecutionEvent) {
        page?.video()?.saveAs(saveDirectory(event).resolve("${event.source.testMethod.name}.webm"))
    }

    private fun saveDirectory(event: AfterTestExecutionEvent) = Path.of("build/reports/playwright")
        .resolve(event.source.testClass.name)
        .also { Files.createDirectories(it) }
}
