# LogHound

A fast, memory-efficient Android logcat viewer built with Compose Multiplatform for Desktop. Plugin-based: every visual feature is a plugin (including the core log viewer), and every data source is a plugin (logcat, file, future others).

For the full design, read the specs first: [`specs/behavior.spec.md`](specs/behavior.spec.md), [`specs/coding.spec.md`](specs/coding.spec.md), [`specs/plugins.spec.md`](specs/plugins.spec.md) (DSL plugin authoring), and [`AGENTS.md`](AGENTS.md).

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

## Replay or stream logs without the app

`scripts/inject_logs.py` is a small Python 3 tool that writes log entries directly into `~/.loghound/logs.db`. It needs only `python3` — no Gradle, no app process.

```sh
# Replay a saved logcat capture into the DB
scripts/inject_logs.py --input session.log

# Stream live captures into the DB without running the app
adb logcat -v threadtime | scripts/inject_logs.py

# Custom DB path
scripts/inject_logs.py --db /tmp/test-logs.db --input fixture.log
```

Then open LogHound — the Log Viewer shows the rows on initial query, and UUID Grouping's data side backfills any new UUIDs from its checkpoint on launch.

**Caveat:** rows added by the script *while* the app is open won't appear live in the UI — the app's `ingested` Flow only fires for in-process appends. Close + reopen the affected tab (or relaunch the app) to pick them up.

The script must be run against a database that already has the LogHound schema. Launching the app once creates `~/.loghound/logs.db`; after that the script can run any time, with or without the app.

### Long captures (hours)

For hours-long captures, two practical concerns:

- **`adb` pipes can break.** A USB hiccup, device reboot, or `adb` server restart will exit `adb logcat` and the pipe closes. A multi-hour capture shouldn't depend on a single pipe staying alive.
- **DB grows.** A chatty Android device emits 50–500 lines/sec at roughly 150 bytes/row on disk. Multi-hour captures land at hundreds of MB to a few GB in `~/.loghound/logs.db`. SQLite handles it fine — keep an eye with `du -h ~/.loghound/logs.db`.

Recommended robust setup — `tmux` session running a restart-on-disconnect loop:

```sh
# Start a tmux session so the capture survives closing your terminal
tmux new -s loghound-capture

# Inside tmux, run a loop that reconnects if adb drops out
while true; do
  date "+%F %T  starting capture"
  adb logcat -v threadtime | scripts/inject_logs.py
  echo "$(date '+%F %T')  pipe closed — retrying in 5s"
  sleep 5
done
```

Detach with **Ctrl+B then D**. Re-attach later with `tmux attach -t loghound-capture`. Stop the capture cleanly with Ctrl+C inside the loop (the script's `SIGINT` handler flushes pending rows before exiting); the loop's `while true` will then try to restart, so kill the tmux session (`tmux kill-session -t loghound-capture`) when you're truly done.

If you don't want tmux, `nohup` works too:

```sh
nohup bash -c 'while true; do adb logcat -v threadtime | scripts/inject_logs.py; sleep 5; done' \
  > /tmp/loghound-injector.log 2>&1 &
```

### Verifying the capture is working

Three signals, none of which require the app:

1. **The script's own progress output** — every 10,000 rows by default the script prints a line to stderr like `… inserted 120000 rows (3 skipped)`. Tune the cadence with `--progress-every N` (e.g. `--progress-every 1000` to print more frequently for low-traffic devices, or `--progress-every 0` to silence). If you don't see those messages tick by during a long capture, something's stuck.
2. **Row count via `sqlite3`** — pure read, safe while the script is writing (WAL handles concurrent readers). Run it twice, a minute apart; the number should grow:
   ```sh
   sqlite3 ~/.loghound/logs.db 'SELECT COUNT(*) FROM logs'
   ```
3. **File size growth** — `du -h ~/.loghound/logs.db`. Should grow over time at roughly 100–500 bytes/row × your device's log rate.

You can also open LogHound at any point during the capture; the Log Viewer's initial query will show whatever was committed up to that moment.

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
plugin-dsl/                     small DSL for "vibe-coded" plugins (verbs, theme, tabs, scripting host)
app/                            DI/wiring, window shell, tab management
plugins/ui/log-viewer/          tail-style log view with filter bar
plugins/ui/uuid-grouping/       UUID discovery + counts; owns its own SQLite file
plugins/data/logcat/            real `adb logcat -v threadtime` source
examples/plugins/               example .kts plugins (drop into ~/.loghound/plugins/)
specs/                          source of truth — read these before changing code
```

Plugin storage convention: per-plugin SQLite files at `~/.loghound/plugins/<plugin-id>.db`. Main logs DB at `~/.loghound/logs.db`.

## DSL plugins (`.kts` drop-ins)

Beyond the in-tree built-in plugins, LogHound loads `.kts` scripts from `~/.loghound/plugins/` at launch. Each script declares one plugin via the DSL:

```kotlin
plugin {
    id = "my-grouper"
    name = "My Grouper"

    val rows = stateOf<List<String>>(emptyList())

    data { repo ->
        repo.ingested.collect { batch ->
            rows.value = (rows.value + batch.map { it.message }).takeLast(50)
        }
    }

    ui {
        column {
            text("${rows.value.size} recent lines")
            list(items = rows.value, key = { it }) { msg -> text(msg) }
        }
    }
}
```

The DSL exposes ~13 verbs (`column`, `row`, `list`, `text`, `textField`, `button`, `clickable`, `tabs`, …) plus a `theme { … }` builder for visual overrides. See [`specs/plugins.spec.md`](specs/plugins.spec.md) for the full surface and [`examples/plugins/uuid-grouping.kts`](examples/plugins/uuid-grouping.kts) for a worked example that mirrors the built-in UUID Grouping plugin (master list + sort toggle + per-UUID detail tabs).

Drop a script in:

```sh
mkdir -p ~/.loghound/plugins
cp examples/plugins/uuid-grouping.kts ~/.loghound/plugins/
```

Then relaunch — the new plugin appears in the sidebar under whatever `name` the script declared.
