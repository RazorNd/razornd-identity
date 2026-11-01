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

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

@Controller
class MainController {
    @GetMapping("/login")
    fun login() = ModelAndView("login")

    @GetMapping("/login", params = ["error"])
    fun loginError() = ModelAndView("login", mapOf("error" to true))

    @GetMapping("/")
    fun home(principal: Principal) = ModelAndView("home", mapOf("username" to principal.name))

}
