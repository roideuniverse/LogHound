# LogHound

A fast, memory-efficient Android logcat viewer built with Compose Multiplatform for Desktop. Plugin-based: every visual feature is a plugin (including the core log viewer), and every data source is a plugin (logcat, file, future others).

For the full design, read the specs first: [`specs/behavior.spec.md`](specs/behavior.spec.md), [`specs/coding.spec.md`](specs/coding.spec.md), and [`AGENTS.md`](AGENTS.md).

## Prerequisites

- **JDK 17+** on the `PATH` (or via your toolchain manager — the build uses `org.gradle.toolchains.foojay-resolver-convention` to pull a matching JDK if needed).
- **macOS, Linux, or Windows.** Compose Multiplatform Desktop targets all three; this README's `open` commands assume macOS.
- **Android `adb`** (only required to ingest from a real device). The logcat data plugin looks for `adb` in `$ANDROID_HOME` / `$ANDROID_SDK_ROOT`, then `~/Library/Android/sdk/platform-tools/adb`, then your `PATH`. Without `adb`, the plugin idles silently — the rest of the app runs fine.
- No other tooling required. SQLite is bundled via the JDBC driver; no system SQLite needed.

## Build the project

```sh
./gradlew :app:assemble
```

Compiles every module. Produces JARs under `*/build/libs/`. Useful as a sanity check; doesn't run the app.

To build only one module:

```sh
./gradlew :core-api:assemble
./gradlew :database:assemble
./gradlew :core-impl:assemble
./gradlew :plugins:ui:log-viewer:assemble
./gradlew :plugins:ui:uuid-grouping:assemble
./gradlew :plugins:data:logcat:assemble
./gradlew :plugins:data:synthetic:assemble
```

## Run the app

There are three ways to run, each with a different tradeoff. Pick based on what you're doing.

### 1. Dev mode via Gradle (fastest iteration)

```sh
./gradlew :app:run
```

Launches the app inside a Gradle JavaExec. **Compose hot reload** is wired up — most code changes are picked up without a restart. This is the right command while you're actively coding.

Tradeoffs:
- The OS-level process is named `java` (one of several Gradle JVMs in `ps`). Identify the LogHound window by its title, not by process name.
- Closing the window cleanly stops the JVM. To stop the Gradle daemon afterwards, use `./gradlew --stop` — never `pkill`.

### 2. Native `.app` bundle (clean OS integration)

```sh
./gradlew :app:createDistributable
open app/build/compose/binaries/main/app/LogHound.app
```

Builds a real macOS app bundle at `app/build/compose/binaries/main/app/LogHound.app` (or platform equivalents on Linux/Windows). The bundle ships its own JRE, so it runs without any installed JDK.

Use this when you want to:
- See LogHound show up as a real OS-integrated app (Dock entry, process named `LogHound`).
- Demo or screenshot the app outside of a Gradle session.
- Validate `packageName` / bundle ID configuration before installing.

Tradeoffs:
- No hot reload. Code changes require another `:app:createDistributable`.
- ~250–300 MB on disk because the JRE is bundled.

### 3. Installer packages (DMG / MSI / DEB)

```sh
./gradlew :app:packageDmg     # macOS
./gradlew :app:packageMsi     # Windows
./gradlew :app:packageDeb     # Linux
```

Produces an installable artifact under `app/build/compose/binaries/main/<format>/`. Used for distribution to end users.

### Bonus: fat JAR (no installer)

```sh
./gradlew :app:packageUberJarForCurrentOS
java -jar app/build/compose/jars/LogHound-<os>-<arch>-1.0.0.jar
```

A single-file executable JAR (~150–200 MB). Requires a system JDK 17+; doesn't bundle one. Lighter than an `.app` bundle if you're OK with a JDK on the target machine.

## Run the tests

```sh
./gradlew test
```

Runs every test in every module, including:
- **Unit tests** for parsers, regex extractors, filters, query parsers.
- **Integration tests** against real SQLite (per-test temp DB via `TemporaryFolder`).
- **Stress tests** that drive 100K entries through `LogRepositoryImpl` and verify pagination + filter-aware counts.
- **End-to-end tests** that render the real Compose UI (`createComposeRule`), drive log data through the real repository, observe the semantics tree, exercise scrolling and filtering, and cross-check the DB matches what was ingested.

To run just a single test layer:

```sh
./gradlew :app:jvmTest                          # only the E2E tests in app/
./gradlew :core-impl:test                       # repo tests + 100K stress
./gradlew :plugins:data:logcat:test             # threadtime parser
./gradlew :plugins:ui:uuid-grouping:test        # UUID extractor
```

A single test class:

```sh
./gradlew :app:jvmTest --tests "com.roideuniverse.loghound.e2e.LogViewerE2eTest"
```

## Stop / clean

```sh
./gradlew --stop          # shut down Gradle daemons cleanly (use this, not pkill)
./gradlew clean           # delete build/ outputs across modules
./gradlew :app:clean      # clean only one module
```

If `:app:run` left a window open after a crash, close the LogHound window (the JVM will exit). For the bundled app: quit it from the Dock.

## Where things live

```
core-api/                       interfaces + domain models (LogRepository, UIPlugin, DataPlugin, LogEntry, …)
database/                       SQLDelight schema + LogDataStore impl (depends only on core-api)
core-impl/                      LogRepositoryImpl (wraps a LogDataStore)
app/                            DI/wiring, window shell, tab management
plugins/ui/log-viewer/          tail-style log view with filter bar
plugins/ui/uuid-grouping/       UUID discovery + counts; owns its own SQLite file
plugins/data/logcat/            real `adb logcat -v threadtime` source
plugins/data/synthetic/         synthetic test/demo data source
specs/                          source of truth — read these before changing code
```

Plugin storage convention: per-plugin SQLite files at `~/.loghound/plugins/<plugin-id>.db`. Main logs DB at `~/.loghound/logs.db`.
