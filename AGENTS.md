# Repository guidance

- Use JDK 25 and the Gradle wrapper. Main entry point: `src/main/kotlin/ru/razornd/identity/IdentityApplication.kt`.
- Local run: `docker compose up -d`, then `./gradlew bootRun`.
- Verify with `./gradlew test`; isolate the context test with `./gradlew test --tests 'ru.razornd.identity.IdentityApplicationTests'`. Tests start PostgreSQL, Redis, and Grafana LGTM via Testcontainers, so Docker must be available.
- Keep Testcontainers image tags in `src/test/kotlin/ru/razornd/identity/TestcontainersConfiguration.kt` aligned with `compose.yaml`.
- New Kotlin files need the Apache 2.0 header used by existing Kotlin sources.
- When IDEA tools are available, use `reformat_file` on changed code files and `lint_files` to check them for errors and warnings. Use `build_project` for IDE compilation diagnostics; run `./gradlew test` for test verification.

## Commit messages

- Follow Conventional Commits: `type(scope): description`; scope is optional. Use types such as `feat`, `fix`, `docs`, `refactor`, `test`, and `chore`—not bracketed labels like `[feat]`.
- Keep the subject concise (ideally at most 72 characters), imperative, and without a trailing period.
- Prefer adding one relevant emoji after the colon to give the history some character; omit it when none fits. Keep it optional, use at most one, and never replace the commit type (for example, `feat(auth): ✨ add OAuth client registration`).
- For non-trivial changes, separate the subject and body with a blank line. Summarize what changed and include relevant reasoning or trade-offs discussed with the user. Keep it focused; omit the body for trivial changes.
