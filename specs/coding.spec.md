# LogHound — Coding Spec

### Tech stack

- Kotlin
- Compose Multiplatform for Desktop
- SQLite, accessed through SQLDelight (the SQLite-3.24 dialect for `ON CONFLICT … DO UPDATE` UPSERT support; the JdbcSqliteDriver from `app.cash.sqldelight:sqlite-driver`)
- Gradle with Kotlin DSL
- JDK 17+

---

## Module structure

The project is a multi-module Gradle project. Confirmed modules:

- **core-api** — domain module. Interfaces, data models, and the plugin API contract only. No implementations, no UI, no SQL. Everything else depends on this; this depends on nothing in the project. Defines `LogRepository` — the high-level domain contract for log access.
- **database** — persistence layer. Owns the SQLDelight schema, generated code, and the `LogDataStore` interface plus its SQLDelight + SQLite implementation. Depends only on `core-api`. **Plugins that need raw / lower-level data access depend on this module directly**; plugins that only need domain operations stay on `core-api`.
- **core-impl** — non-persistence implementations of `core-api` contracts. Currently: `LogRepositoryImpl` (wraps a `LogDataStore`). Other future cross-cutting implementations land here. Depends on `core-api` and `database`. Plugins do **not** depend on this module.
- **app** — DI/wiring layer. Assembles and connects implementations, registers plugins, owns the window shell, tab management, and theme. Depends on `core-api`, `database`, `core-impl`, and on each registered plugin module.
- **plugins/ui/&lt;plugin-name&gt;** — one Gradle module per **UI** plugin (e.g. `plugins/ui/log-viewer`). Each module contains exactly one `UIPlugin` implementation plus its private supporting code.
- **plugins/data/&lt;plugin-name&gt;** — one Gradle module per **data** plugin (e.g. `plugins/data/logcat`, `plugins/data/synthetic`). Each module contains exactly one `DataPlugin` implementation — the source-reading + parsing + batching code that produces `LogEntry` and writes them to `LogRepository`. The `synthetic` plugin is a built-in test/demo source: it generates realistic-looking logcat lines including a reusable pool of UUIDs, so the ingest pipeline and analysis plugins (UUID Grouping, etc.) can be exercised end-to-end without a connected Android device.

**Plugin module rules** (apply to both `plugins/ui/*` and `plugins/data/*`):
- A plugin module depends only on `core-api` (and `database` if it needs raw cursor access).
- It must not depend on `app`, on `core-impl`, or on any other plugin module. Build-level isolation enforces this.
- A single plugin may implement both `UIPlugin` and `DataPlugin` if it needs UI controls and a data feed in one (e.g. a future "Logcat with device picker" plugin). Such a plugin lives in whichever directory matches its primary nature.

Placeholder plugins that exist only as a name in the sidebar may live inline in `app/` until they have real code.

---

## Data plugins (sources, parsing, ingestion)

Reading log data, parsing it, and pushing it into the database is the job of **data plugins** (`DataPlugin` — see Plugin API). There is no separate "input layer" or "ingester" concept in the architecture: each data plugin owns its own source, parser, and batching logic.

A typical data plugin's `run(repository)` loop:
1. Open its source (subprocess, file handle, network socket, etc.).
2. Read raw lines.
3. Parse each line into a `LogEntry`. Malformed lines are skipped — never crash.
4. Batch entries (default: up to 1000 lines or a short time window, whichever comes first).
5. Call `repository.append(batch)` — persistence + `ingested` broadcast handled by the repository.
6. Suspend until cancellation, then close the source cleanly.

**Constraints (apply to every data plugin):**
- Bounded buffer — never hold more than a configurable batch size in memory (default: 1000 lines).
- Inserts go to the repository in batched calls, not per line.
- The plugin runs in its own coroutine, on a background dispatcher; it never blocks the UI.
- Reconnection and error handling are the plugin's responsibility (e.g. the logcat plugin handles ADB device drops; a file plugin handles EOF on growing files).
- Parser code lives inside the plugin that needs it. It is extracted to a shared module only when a second plugin needs the same parser.

### Logcat threadtime parser (used by `plugins/data/logcat`)

Parses Android's `adb logcat -v threadtime` format:

```
MM-DD HH:MM:SS.mmm  PID  TID  PRIORITY  TAG: MESSAGE
```

Extracts: timestamp, PID, TID, priority (V/D/I/W/E/F/S), tag, message.

- Malformed lines are skipped (and counted, for diagnostics) — never cause a crash.
- Package name resolution from PID is optional and best-effort.

---

## Storage

Storage is split into two layers, each with its own contract:

1. **`LogRepository`** — high-level **domain** contract in `core-api`. What plugins normally consume.
2. **`LogDataStore`** — lower-level **persistence** contract in the `:database` module. Raw cursor reads and inserts against the underlying database.

The Repository implementation (`LogRepositoryImpl` in `core-impl`) wraps a `LogDataStore` and adds filter matching, the `ingested` broadcast, and any cross-cutting domain logic.

### `LogRepository` interface (core-api)

```kotlin
interface LogRepository {
    suspend fun append(batch: List<LogEntry>)

    suspend fun query(
        filter: LogFilter = LogFilter(),
        beforeId: Long? = null,    // entries OLDER than this id
        afterId: Long? = null,     // entries NEWER than this id
        limit: Int = 200,
    ): LogPage

    suspend fun count(filter: LogFilter = LogFilter()): Long

    val ingested: Flow<List<LogEntry>>
}
```

**Pagination — bidirectional cursor:**
- Both `beforeId` and `afterId` null → return the **latest `limit` entries** (the tail). Default when opening the Log Viewer.
- `beforeId = X` → entries with `id < X`, capped at `limit`. Scroll-up "load older".
- `afterId = X` → entries with `id > X`, capped at `limit`. Catch up if a consumer fell behind.
- The two cursors are mutually exclusive (passing both is undefined).
- Cursor is the row's stable monotonic `id`. Offset-based pagination is not used.
- Returned `LogPage.entries` are always in chronological order (id ascending) regardless of which cursor was used.

**`ingested` Flow — semantics:**
- Hot stream. Emits every appended batch as it lands; **does not replay history**.
- Subscribers that need history bootstrap with `query()` then collect `ingested` for live appends.
- Filter matching is the subscriber's responsibility — the repository broadcasts every batch as-is.

**`append` semantics:**
- An empty-batch call is a no-op: nothing is persisted and nothing is emitted on `ingested`.
- A non-empty batch is persisted to storage in a single transaction, then emitted on `ingested` as a single value. Order across `ingested` matches the order of `append` calls.

**Filtered-query semantics:**

When `filter` is the empty `LogFilter()`, the implementation issues a single page-sized read against the data store and returns `limit` rows directly.

When `filter` is non-empty, the implementation iterates page-by-page from the cursor (or from the tail when no cursor is given), applying `filter.matches(entry)` to each row, and accumulates matching entries until either `limit` matches are found or the data store is exhausted. The iteration page size is independent of the caller-requested `limit`. This guarantees that `query(filter = LogFilter(textSearch = "rare-token"), limit = 500)` returns the matches even when they sit far older than the latest 500 rows in storage. Performance is bounded by total rows scanned; future SQL filter pushdown will replace this with a single indexed query, but the contract remains the same.

### `LogDataStore` interface (:database module)

`LogDataStore` is the lower-level data-access contract. It deals in unfiltered cursor reads and bulk inserts; it knows nothing about `LogFilter` or `ingested` broadcasts.

```kotlin
interface LogDataStore {
    /**
     * Inserts each entry and returns the same entries with their auto-assigned ids
     * filled in, in the same order as `batch`. Empty input returns an empty list.
     */
    suspend fun insert(batch: List<LogEntry>): List<LogEntry>

    suspend fun selectTail(limit: Int): List<LogEntry>
    suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry>
    suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry>

    suspend fun countAll(): Long
}
```

`insert` returning the id-populated entries is load-bearing: subscribers of `LogRepository.ingested` need real ids on every emitted `LogEntry`, otherwise UI keys (`LazyColumn(items, key = { it.id })`) collide. The SQLDelight implementation reads `last_insert_rowid()` once at the end of the insert transaction and back-fills the contiguous id range onto the input list.

This contract is what plugins import when they need raw access — e.g. a future UUID-detail FTS scanner, or any plugin issuing custom SQL via the SQLDelight-generated query objects exposed alongside the implementation.

### Default implementation (`:database`, SQLDelight + SQLite)

- **Core table** — `logs(id, timestamp, pid, tid, priority, tag, message, package_name)`. Indexed on: `timestamp`, `tag`, `priority`, `pid`, `package_name`.
- WAL mode enabled for concurrent read/write performance.
- All queries are paginated — no unbounded result sets.
- Regex search uses SQLite's regex support or application-side filtering over indexed subsets.
- The `:database` module exposes a factory `createLogDataStore(databaseFile: File): LogDataStore` that opens the SQLite file, applies the schema, and returns the implementation.
- **Every `LogDataStore` method dispatches to `Dispatchers.IO`** before doing any JDBC work. This is load-bearing for UI callers: `rememberCoroutineScope()` in Compose is bound to the Main dispatcher, and a synchronous `executeAsList()` on Main blocks the next recomposition until it returns. With the dispatch in place, every caller — Main, Default, IO — automatically gets off-thread DB work.
- `LogRepositoryImpl` implements `ingested` as a hot broadcast stream: `append(batch)` first inserts to storage (id-populated entries returned), then emits the id-populated list to all current subscribers in a single value. There is no buffering of past batches.

### Plugin storage

A plugin that needs its own persisted state owns its **own SQLite file** at `~/.loghound/plugins/<plugin-id>.db`. The plugin module declares its schema with its own SQLDelight setup (depends on `app.cash.sqldelight:sqlite-driver` directly) and manages its own migrations. The app passes the plugin's dedicated DB path to the plugin's constructor.

- Plugins have read-only access to the core `logs` table — either through `LogRepository` (typed, domain-level) or through `LogDataStore` (raw, indexed cursor reads).
- Plugins have read-write access only to their own SQLite file. They do not share schemas, do not write to the main `logs.db`, and do not access each other's databases.
- A formal `PluginStorage` interface is **not** required for this pattern — each plugin handles its own SQLDelight setup. If many plugins later need shared persistence helpers (config files, key-value state), a `PluginStorage` interface may be introduced as a convenience.

**Why per-plugin DB files (not shared tables in `logs.db`):**
- Clean isolation — plugin owns its schema, migrations, and lifecycle without coordination with `:database`.
- Each plugin module depends only on `core-api` (and SQLDelight directly); no transitive dependency on `:database` for plugins that just want their own state.
- A plugin can be deleted by removing its DB file, without touching the main logs DB.

---

## Plugin API

Plugins extend LogHound. There are two orthogonal plugin contracts in `core-api` — a single plugin may implement either, both, or future ones.

**`UIPlugin`** — visual plugins that render a tab in the window shell. Includes the core log viewer.

- `id` — unique string identifier
- `name` — human-readable display name
- `content(modifier)` — a Composable that renders the plugin's UI inside the supplied `Modifier` (the tab content area passes `Modifier.fillMaxSize()`)

**`DataPlugin`** — long-running plugins that need a coroutine with repository access for the lifetime of the app. This contract is used by both **producers** (e.g. logcat) that read from a source and call `repository.append(...)`, and **observers** (e.g. UUID Grouping) that subscribe to `repository.ingested` and update their own state.

```kotlin
interface DataPlugin {
    val id: String
    val name: String
    suspend fun run(repository: LogRepository)
}
```

- `run(repository)` is a suspending function that lives inside the plugin's coroutine. It runs from app startup until cancellation. Concrete responsibilities depend on the plugin: producers ingest from sources; observers do backfill scans and subscribe to `repository.ingested`.
- Cancellation of the coroutine is the way to stop the plugin. The implementation must close subprocesses, file handles, DB drivers, etc. on cancel.
- The app starts each registered `DataPlugin` in its own coroutine on launch, on a background dispatcher.
- A plugin that needs both a tab UI and a long-running data side implements **both** `UIPlugin` and `DataPlugin`. UUID Grouping is the canonical example: its `DataPlugin` side scans logs continuously into its own SQLite file; its `UIPlugin` side renders the current state of that file when the tab is open. Closing the tab does not stop the data side.

**Data access for plugins:**

- **Domain access** via `LogRepository` (in `core-api`) — typed, paginated, filterable, with the `ingested` Flow for live updates. Default for most plugins.
- **Raw access** via `LogDataStore` (in `:database`) — direct cursor reads and inserts. Used by plugins that need indexed scans or custom SQL across the main logs DB.
- **Own state** via the plugin's own SQLite file (see Storage → Plugin storage).

The app passes the appropriate reference(s) (and the plugin's dedicated DB file path, if relevant) to each plugin at registration time. The current `UIPlugin` is deliberately the smallest interface that lets the window shell render a plugin in a tab; lifecycle hooks beyond `run`/cancellation are deferred until they are needed.

**Backfill + checkpoint pattern (for observer DataPlugins):**

Observer plugins that need to process every historical log line (UUID Grouping, future analyzers) follow this pattern:
1. On `run()`: read a checkpoint (e.g. `last_scanned_log_id`) from the plugin's own DB. Default to 0 on first ever run.
2. Page forward through the main logs (`repository.query(afterId = checkpoint, ...)`) until caught up. After each page: process entries, batch-write results into the plugin's DB in a single transaction, advance the checkpoint.
3. After backfill: collect `repository.ingested` indefinitely, applying the same processing to each new batch.
4. The page loop runs on `Dispatchers.Default` (CPU work) or `Dispatchers.IO` (IO-bound), and may parallelize across disjoint id ranges if processing per line is expensive.
5. The UI side polls or subscribes to changes in the plugin's own DB to render fresh results — independent of the data side's progress.

This pattern keeps the UI responsive on first-time scans of GB-scale logs and makes re-opens (or future enable→disable→enable cycles) cheap because the checkpoint avoids redundant work.

**Plugin loading:**
- Plugins are compiled-in Kotlin modules
- Registered at startup in the app module's entry point — the app constructs a `List<UIPlugin>` and hands it to the window shell
- No runtime class loading, no JAR discovery — adding a plugin means adding a module dependency and registering it in code

**core-api Compose dependency:**
- Because `UIPlugin.content` is a `@Composable`, `core-api` depends on `compose.runtime` and `compose.ui` (for `Modifier`). This is the only UI dependency in `core-api`; concrete UI implementations and Material theming stay in `app`.

---

## Data model

**LogEntry** — a single parsed log line:
- id (Long), timestamp (String), pid (Int), tid (Int), priority (enum), tag (String), message (String), packageName (String, nullable)

**LogPriority** — enum with levels:
- Verbose, Debug, Info, Warn, Error, Fatal, Silent
- Each has a single-character label (V, D, I, W, E, F, S) and a numeric level for comparison

**LogFilter** — a set of optional filter criteria:
- tag, minPriority, pid, tid, textSearch, regexSearch, packageName
- All non-null fields combine with AND logic
- Built by parsing the single-line filter query (see behavior spec → Filtering). A `FilterQueryParser` translates user input into a `LogFilter`; unrecognized `key:` clauses are treated as free text.

**LogPage** — a single page of query results from `LogRepository.query`:
- `entries: List<LogEntry>` — the rows for this page, in chronological order (id ascending) regardless of which cursor was used. Implementations reverse internally if needed so consumers always render in the same order.
- `hasMore: Boolean` — whether another page exists beyond this one in the same direction. The consumer uses the first/last entry's `id` as the next cursor.

---

## UI framework

Compose Multiplatform for Desktop is the rendering layer. The descriptions below are about what the UI does and what the user sees; the framework-specific composable names, layout primitives, and modifier chains are deferred to Appendix F.

**Window structure:**
- Single main window titled "LogHound" with a native menu bar, a left sidebar, a tab bar, a content area, and (future) a status bar.
- Native menu bar: File (Exit), Edit (Preferences), View (Toggle Sidebar), Help (About). Only Exit is wired in the current shell; the rest are skeleton no-ops.
- Left sidebar: 220dp wide, light-gray background, lists every registered plugin by name. Clicking a name opens or focuses a tab.
- Tab bar: top of the content area. Each tab shows the plugin name plus a close button (✕). Closing a tab falls back to the left neighbor. The Core Log Viewer tab is opened automatically on launch.
- Content area: renders the active tab's plugin UI at full size.
- Status bar (future): log counts and ingestion status.

**Detachable tabs:**
- Each tab's content is a standalone unit with no dependency on its container.
- Detaching a tab renders the same content inside a new top-level window.
- Closing a detached window re-docks the tab to the main window.

**Virtual scrolling:**
- Long lists are virtually scrolled — only visible rows plus a small buffer are in memory.
- Backed by paginated database queries; scroll position drives which page is fetched.
- Each long list shows a vertical scrollbar overlaid on its right edge.
- Each long list supports drag-select to copy text across rows.

**Log row colors (Log Viewer and UUID detail sub-tabs):**

Rows are colored by `LogEntry.priority`:
- Verbose → gray (`#666666`)
- Debug → blue (`#1976D2`)
- Info → green (`#388E3C`)
- Warn → amber (`#F57C00`)
- Error → red (`#D32F2F`)
- Fatal → red (`#D32F2F`) bold
- Silent → light gray (`#999999`)

The same color mapping is used in both Log Viewer and UUID detail views. The implementation is intentionally duplicated across the two plugin modules; it will be extracted to a shared `plugin-utils` module only when a third consumer appears.

**Jump-to-bottom button (Log Viewer):**

- A floating button appears at the bottom-right of the log list whenever the user is not within ~2 rows of the tail.
- Tapping scrolls to the most recent entry. Subsequent ingested batches resume auto-scrolling because the user is now at the tail.

**Load-older-on-scroll (Log Viewer and UUID detail sub-tabs):**

- Trigger: the first visible row index is within `LOAD_OLDER_THRESHOLD` of the start of the in-memory list. The threshold is small (10 rows) so the fetch starts before the user reaches the very top.
- Fetch: `LogRepository.query(filter = activeFilter, beforeId = entries.first().id, limit = LOAD_OLDER_PAGE_SIZE)` where the page size is 500.
- Result: prepend the returned entries to the in-memory list. `LazyColumn` with stable item keys preserves the visible scroll position across the prepend.
- While the fetch is in flight an `loadingOlder` flag is true; an item is rendered at index 0 of the `LazyColumn` showing a small "Loading older…" indicator. Reentry is guarded by the flag so concurrent triggers collapse into a single fetch.
- Exhaustion: when a fetch returns zero entries, an `olderExhausted` flag flips on and the trigger stops firing for the rest of the session (or until the filter changes, which resets it).
- For the Log Viewer, the state lives in `LogViewerPlugin.content`. For the UUID detail sub-tabs, the state lives on `UuidDetailController` (`loadingOlder`, `olderExhausted`, `loadOlder()`); the controller takes the `LogRepository` in its constructor so `loadOlder()` doesn't need it as a parameter.

**UUID Grouping sub-tab UI:**

Inside the UUID Grouping panel:
- A small tab strip at the top. The "UUIDs" tab is always present and selected by default; selecting it shows the master list with the search/sort toolbar.
- Clicking a UUID row opens (or focuses) a sub-tab labelled with the first 8 characters of the UUID followed by `…`. Each sub-tab has a close (`✕`).
- Each open sub-tab retains its scroll position when the user switches tabs.
- Each sub-tab runs an independent live subscriber that initially queries `LogRepository.query(filter = LogFilter(textSearch = uuid), limit = 500)` then follows `LogRepository.ingested` to append matching live entries. Closing the sub-tab cancels the subscriber. The `DataPlugin.run` side of UUID Grouping is unaffected by sub-tab open/close — the master backfill continues regardless.
- The detail view exposes three rendering states driven by the controller: **loading** (initial query in flight — render a centered progress indicator), **empty** (initial query complete, no matches yet — render an empty-state message), and **loaded** (one or more entries — render the list). The controller's `loading` flag is observable Compose state; it flips to `false` in a `finally` block around the initial query so it always settles regardless of cancellation or error.

**Package UID lookup (Log Viewer):**

A small input field and Find button sit above the filter bar. Typing a substring (e.g. `com.app.debug`) and pressing Find queries the connected device for matching packages and renders a small list of `(package, uid)` rows. Each row offers two copy actions: one for `package:com.app.debug` (paste into LogHound's filter bar) and one for `--uid=10231` (paste into an external `adb logcat` invocation). When no device is reachable the Find button reports "No device" and stays disabled.

**Package-name resolution (logcat plugin):**

Each `LogEntry` produced by `LogcatDataPlugin` carries the package name of the process that emitted the line. The plugin instantiates a `PackageResolver` per device connection, which:

- Runs `adb -s <device> shell ps -A` on connect to seed an in-memory `PID → package` map.
- Refreshes the map every 30 seconds so newly-launched processes are picked up.
- Cancels its background refresh loop when the parent connection is cancelled.
- Normalises process names with sub-process suffixes (e.g. `com.app.debug:remote` → `com.app.debug`) and drops kernel threads (names beginning with `[`).
- Exposes a synchronous, non-suspending `packageNameFor(pid)` query for the streaming hot path — the parser path looks the PID up in the cache without IO. PIDs not yet seen by a refresh return `null`; the next refresh fills them.

The `package_name` column on the `logs` table (already nullable, already indexed) is set from this lookup at insert time. There is no retroactive backfill of rows already inserted with `null`.

---

## Performance constraints

- Memory usage stays under 250MB regardless of total log volume
- Ingestion sustains 10,000+ lines per second
- Filter changes reflect in the UI within 200ms
- Scrolling maintains 60fps over millions of rows
- App is interactive within 3 seconds of launch

---

## Error handling

- Parser errors skip the malformed line and continue — never crash
- Database errors are surfaced in the status bar, not as modal dialogs
- ADB connection failures show a clear status and allow retry
- Plugin exceptions are caught by the core — a crashing plugin does not take down the app

---

## Appendix A — Build conventions

If you've read the main spec you already know what the modules *are*; this appendix is just the build-file recipe for each one, kept here so the project can be set up from scratch without guessing about Gradle plugin combinations.

Most modules are plain `kotlinJvm`. The interesting bits are: `core-api` uses `api` (not `implementation`) for coroutines and Compose because those types appear in its public surface; the `database` module owns the SQLDelight setup for the main logs DB; UI plugins that need their own SQLite file pick up SQLDelight too; the `app` module is the only Kotlin Multiplatform module so that Compose Desktop's `nativeDistributions` configuration applies cleanly.

Each block below is the exact content of the file in the working tree. Adding a new module of the same type means copying the matching recipe and swapping the names.

### `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.3.20"
composeMultiplatform = "1.10.3"
composeHotReload = "1.0.0"
kotlinxCoroutines = "1.10.2"
androidxLifecycle = "2.10.0"
sqldelight = "2.0.2"
junit = "4.13.2"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlin-testJunit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutinesSwing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinxCoroutines" }
kotlinx-coroutinesCore = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutinesTest = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidxLifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidxLifecycle" }
sqldelight-sqliteDriver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-coroutinesExtensions = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-sqliteDialect324 = { module = "app.cash.sqldelight:sqlite-3-24-dialect", version.ref = "sqldelight" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
composeHotReload = { id = "org.jetbrains.compose.hot-reload", version.ref = "composeHotReload" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### `core-api/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(compose.runtime)
    api(compose.ui)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
```

`api` (not `implementation`) for coroutines/compose because `LogRepository` exposes `Flow<…>` and `UIPlugin.content` exposes `Modifier`/`@Composable` in its public surface.

### `database/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.sqldelight.sqliteDriver)
    implementation(libs.sqldelight.coroutinesExtensions)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

sqldelight {
    databases {
        create("LogHoundDb") {
            packageName.set("com.roideuniverse.loghound.database.sqldelight")
        }
    }
}
```

### `core-impl/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":database"))
    implementation(libs.kotlinx.coroutinesCore)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}
```

### `plugins/ui/<name>/build.gradle.kts` (UI plugin)

Base recipe — apply SQLDelight only if the plugin owns its own DB.

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Optional: include if the plugin owns a DB
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(project(":core-api"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutinesCore)
    // Optional: include if the plugin owns a DB
    implementation(libs.sqldelight.sqliteDriver)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

// Only include this block if the plugin owns a DB.
sqldelight {
    databases {
        create("<PluginName>Db") {
            packageName.set("com.roideuniverse.loghound.plugins.<id>.sqldelight")
            // Only include `dialect(...)` if the schema uses ON CONFLICT … DO UPDATE
            // (UPSERT, requires SQLite 3.24+).
            dialect(libs.sqldelight.sqliteDialect324)
        }
    }
}
```

### `plugins/data/<name>/build.gradle.kts` (data plugin, no UI)

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.kotlinx.coroutinesCore)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
```

### `app/build.gradle.kts`

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-api"))
            implementation(project(":database"))
            implementation(project(":core-impl"))
            implementation(project(":plugins:ui:log-viewer"))
            implementation(project(":plugins:ui:uuid-grouping"))
            implementation(project(":plugins:data:logcat"))
            implementation(project(":plugins:data:synthetic"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.junit)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.roideuniverse.loghound.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LogHound"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.roideuniverse.loghound"
            }
        }
    }
}
```

### `settings.gradle.kts`

```kotlin
rootProject.name = "LogHound"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement { /* google() + mavenCentral() + gradlePluginPortal() */ }
dependencyResolutionManagement { /* google() + mavenCentral() */ }

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":core-api")
include(":database")
include(":core-impl")
include(":app")
include(":plugins:ui:log-viewer")
include(":plugins:ui:uuid-grouping")
include(":plugins:data:logcat")
include(":plugins:data:synthetic")
```

---

## Appendix B — SQL schemas (literal `.sq` files)

The project has two SQLDelight schemas — one for the main logs database, one for UUID Grouping's per-plugin database. Both are reproduced verbatim below so a regeneration that re-runs SQLDelight's annotation processor produces identical generated query classes. SQLDelight will fail the build if the generated Kotlin doesn't match these `.sq` files exactly, so any drift surfaces immediately.

### `database/src/main/sqldelight/com/roideuniverse/loghound/database/sqldelight/Logs.sq`

```sql
CREATE TABLE logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timestamp TEXT NOT NULL,
    pid INTEGER NOT NULL,
    tid INTEGER NOT NULL,
    priority TEXT NOT NULL,
    tag TEXT NOT NULL,
    message TEXT NOT NULL,
    package_name TEXT
);

CREATE INDEX logs_timestamp ON logs(timestamp);
CREATE INDEX logs_tag ON logs(tag);
CREATE INDEX logs_priority ON logs(priority);
CREATE INDEX logs_pid ON logs(pid);
CREATE INDEX logs_package_name ON logs(package_name);

insert:
INSERT INTO logs(timestamp, pid, tid, priority, tag, message, package_name)
VALUES (?, ?, ?, ?, ?, ?, ?);

selectTail:
SELECT *
FROM logs
ORDER BY id DESC
LIMIT :lim;

selectBefore:
SELECT *
FROM logs
WHERE id < :beforeId
ORDER BY id DESC
LIMIT :lim;

selectAfter:
SELECT *
FROM logs
WHERE id > :afterId
ORDER BY id ASC
LIMIT :lim;

countAll:
SELECT COUNT(*) FROM logs;

lastInsertRowid:
SELECT last_insert_rowid();
```

`pid` and `tid` are stored as `INTEGER` (Long in Kotlin) and converted at the `LogDataStore` boundary — the spec deliberately avoids `INTEGER AS Int` because that triggers a SQLDelight column-adapter requirement that complicates the driver wiring.

### `plugins/ui/uuid-grouping/src/main/sqldelight/com/roideuniverse/loghound/plugins/uuidgrouping/sqldelight/Uuids.sq`

```sql
CREATE TABLE uuids (
    uuid TEXT PRIMARY KEY NOT NULL,
    count INTEGER NOT NULL,
    first_log_id INTEGER NOT NULL,
    last_log_id INTEGER NOT NULL
);

CREATE INDEX uuids_count_desc ON uuids(count DESC);

CREATE TABLE meta (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
);

upsert:
INSERT INTO uuids(uuid, count, first_log_id, last_log_id)
VALUES (:uuid, :delta, :logId, :logId)
ON CONFLICT(uuid) DO UPDATE SET
    count = count + :delta,
    last_log_id = MAX(last_log_id, :logId);

countAll:
SELECT COUNT(*) FROM uuids;

selectByCountDesc:
SELECT * FROM uuids
WHERE uuid LIKE :search || '%'
ORDER BY count DESC, uuid ASC
LIMIT :lim OFFSET :off;

selectByUuidAsc:
SELECT * FROM uuids
WHERE uuid LIKE :search || '%'
ORDER BY uuid ASC
LIMIT :lim OFFSET :off;

countMatching:
SELECT COUNT(*) FROM uuids WHERE uuid LIKE :search || '%';

getMeta:
SELECT value FROM meta WHERE key = :key;

setMeta:
INSERT INTO meta(key, value) VALUES (:key, :value)
ON CONFLICT(key) DO UPDATE SET value = :value;
```

The UPSERT clauses require the SQLite 3.24 dialect (see Appendix A → plugin `build.gradle.kts`).

---

## Appendix C — Regex patterns (literal)

Three regular expressions the running code leans on. They're pinned here because rewording them in prose loses subtleties (whitespace handling, non-greedy groups, the optional space after the colon in the threadtime parser) that change the behavior of every log line we ingest.

### Logcat threadtime parser (`plugins/data/logcat`)

```regex
^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+([^:]+?):\s?(.*)$
```

Capture groups, in order: `timestamp`, `pid`, `tid`, `priority` (single-char label), `tag`, `message`. The tag is non-greedy (`[^:]+?`) and a single optional space after the colon (`\s?`) is consumed; the resulting tag is then `.trim()`-ed in Kotlin to strip incidental whitespace around the colon. Lines that don't match return `null` from the parser and are silently dropped (counted, not crashed).

### UUID extractor (`plugins/ui/uuid-grouping`)

```regex
[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}
```

A pre-filter skips the regex entirely on messages that contain no `'-'` character (the common case for logcat lines), since a UUID requires hyphens. Returned UUIDs are normalised to lowercase via `String.lowercase()` so that `AAA…` and `aaa…` collapse onto a single key.

### Package UID lookup parser (`plugins/ui/log-viewer`)

The Package UID lookup runs `adb shell pm list packages -U <substring>` and parses each output line:

```regex
^package:(\S+)\s+uid:(\d+)$
```

Two capture groups: the package name and the integer UID. Lines that don't match are dropped silently (the command sometimes prefixes diagnostic lines or trailing whitespace).

### `ps -A` output parser (`plugins/data/logcat`)

The package-name resolver parses `adb shell ps -A` output. The format varies by Android version, so the parser uses a positional approach rather than a fixed regex:

- Drop the first line (the header).
- Split each remaining line on runs of whitespace.
- The PID is the second column; the process NAME is the **last** column.
- Discard rows where the PID isn't an integer.
- Discard rows where the NAME starts with `[` (kernel threads).
- Strip any sub-process suffix from the NAME (`com.app.debug:remote` → `com.app.debug`).

### Filter query parser (`plugins/ui/log-viewer`)

Tokenization:
- Whitespace splits tokens, except inside double-quoted spans.
- Double quotes are removed from the token but allow embedded whitespace.
- Tokens are processed in order; output `LogFilter` keeps the first value seen per key.

Per-token rules:
- If the token has a `:` after position 0 and before its last char, split into `key` (lowercased) + `value`.
- Recognized keys: `tag`, `level`, `pid`, `tid`, `package`. Unknown keys fall through to free text (the whole token is appended).
- `level` value is parsed as a single uppercase letter (`V`/`D`/`I`/`W`/`E`/`F`/`S`) via `LogPriority.fromLabel`; failing that, by enum name (case-insensitive). On failure the whole token falls through to free text.
- `pid` and `tid` values must parse via `Int.toIntOrNull()`. Failures fall through to free text.
- All other recognized clauses set their respective `LogFilter` field.

Free-text handling:
- All non-clause tokens are joined with a single space.
- If the joined string starts with `/`, it is treated as a regex: leading `/` is stripped, a trailing `/` if present is also stripped, the remainder becomes `regexSearch`. Empty post-strip strings are dropped.
- Otherwise the joined string becomes `textSearch`. Empty strings are dropped.

---

## Appendix D — Numeric configuration

The constants the code actually uses, gathered in one place so they don't have to be hunted through plugin source files. Most of these are choices we made by feel and then froze — there's nothing magic about a 200ms refresh interval or a 100-line flush batch, but changing them changes user-visible behavior, so we treat them as part of the spec.

### Storage paths
- Main logs DB: `~/.loghound/logs.db`
- Per-plugin DB: `~/.loghound/plugins/<plugin-id>.db`
- WAL mode: enabled. `synchronous = NORMAL`.

### `LogRepositoryImpl`
- Default `query` limit: 200
- Default `MutableSharedFlow.extraBufferCapacity` for `ingested`: 64
- Page size used by `count(non-empty filter)` while streaming through the cursor: 1,000

### `LogViewerPlugin`
- Initial query limit on tab open / filter change: 500
- "At-bottom" auto-scroll-resume threshold: visible last item index ≥ entries.lastIndex − 2

### `LogcatDataPlugin`
- Flush batch size: 100 lines
- Retry interval after no devices / disconnect: 2,000 ms
- ADB resolver order:
  1. `$ANDROID_HOME/platform-tools/adb`
  2. `$ANDROID_SDK_ROOT/platform-tools/adb`
  3. `~/Library/Android/sdk/platform-tools/adb` (macOS default)
  4. `adb` (rely on `PATH`)
- ADB device picker: first device whose `adb devices` line ends with `\tdevice` (skips `unauthorized` / `offline`).

### `PackageResolver`
- Snapshot command: `adb -s <device> shell ps -A`
- Refresh interval: 30,000 ms
- Cache type: thread-safe `ConcurrentHashMap<Int, String>` (PIDs are integer-keyed; lookups are sync from the streaming path).
- Process-name normalisation: split on `:` and keep the parent (`com.app.debug:remote` → `com.app.debug`); drop kernel threads (names starting with `[`).
- On `ps` invocation failure: return an empty snapshot, leave the cache untouched, retry at the next interval.

### Package UID lookup (Log Viewer)
- ADB command: `adb shell pm list packages -U <substring>`
- ADB resolver order: same as `LogcatDataPlugin` above (the Log Viewer module duplicates the resolver code locally; will be extracted to a shared helper on third use).
- Result row limit: no explicit cap; the device's `pm list packages` output is small enough (~hundreds of rows max).
- Empty/no-device response: the Find button surfaces "No device" inline; the lookup returns an empty list rather than throwing.

### `SyntheticDataPlugin`
- UUID pool size: 100 (generated at startup with `UUID.randomUUID()`)
- Batch size: 20 entries
- Batch interval: 200 ms (≈100 lines/sec)
- Priority distribution (uniform random in `[0,100)`):
  - Info: r < 35
  - Debug: 35 ≤ r < 65
  - Verbose: 65 ≤ r < 85
  - Warn: 85 ≤ r < 95
  - Error: 95 ≤ r < 99
  - Fatal: r ≥ 99
- 60% of messages embed a UUID. The chosen index is `floor(r² × poolSize)` where r is uniform in `[0,1)`, clamped to `[0, poolSize − 1]` — produces a hot-tail distribution where a small number of UUIDs are heavily reused.
- Tag pool: `ActivityManager`, `GLThread`, `Choreographer`, `InputDispatcher`, `MyApp`, `OkHttp`, `WorkManager`, `Camera2`, `AudioFlinger`, `Zygote`.
- PID pool: `2031, 4127, 8543, 12044, 15877`. `tid = pid + (random.nextInt(8))`.
- Package mapping: see Appendix in `behavior.spec.md`.
- Message templates: see Appendix in `behavior.spec.md`.

### `UuidGroupingPlugin`
- Backfill scan `PAGE_SIZE`: 1,000
- Visible UUID list `VISIBLE_LIMIT`: 200 rows
- DB → UI refresh interval: 500 ms
- Checkpoint key in `meta` table: `last_scanned_log_id`

### `UuidDetailController`
- Initial query: `repository.query(filter = LogFilter(textSearch = uuid), limit = 500)`
- "At-bottom" auto-scroll threshold: same rule as LogViewer
- Load-older trigger threshold: 10 rows from the top of the in-memory list
- Load-older page size: 500 entries

### Compose Desktop / native distribution
- macOS bundle ID: `com.roideuniverse.loghound`
- Display name: `LogHound`
- Distribution formats: `Dmg, Msi, Deb`

---

## Appendix E — Test architecture

The test suite is organized in three layers and uses no mocks anywhere — every test runs against real SQLite (in a per-test temp directory) and, where it's a UI test, against the real Compose composables. The layers below are listed in increasing fidelity to a real running app:

1. **Unit tests** — pure functions, no I/O. Live in each module's `src/test/kotlin/...`. Examples: `LogcatThreadtimeParserTest`, `UuidExtractorTest`, `LogFilterTest`, `FilterQueryParserTest`.
2. **Integration tests** — exercise contracts against real dependencies (real SQLite via JUnit's `TemporaryFolder` rule). No fakes, no mocks anywhere in the suite. Examples: `SqlDelightLogDataStoreTest`, `LogRepositoryImplTest`. A stress test (`LogRepositoryStressTest`) drives 100K entries through the real `LogRepositoryImpl`.
3. **End-to-end tests** — render the actual plugin Compose UI via `createComposeRule()`, drive deterministic data through the real `LogRepositoryImpl + LogDataStore`, observe the semantics tree, exercise scrolling/filtering/sub-tab open. Live in `app/src/jvmTest/kotlin/.../e2e/`. Examples: `LogViewerE2eTest`, `UuidGroupingE2eTest`. Each E2E test cross-checks that UI state equals repository state.

### Determinism rules (apply to every test layer)
- No `delay`, no `Thread.sleep`, no real-clock reads, no polling.
- For UI tests: use `composeTestRule.waitUntil { predicate }` for any async wait. Never assert "exactly N rows visible" because `LazyColumn` only realises the visible viewport.
- For coroutine tests touching `MutableSharedFlow`: use `kotlinx.coroutines.test.UnconfinedTestDispatcher()` as `runTest`'s dispatcher. The default `StandardTestDispatcher` parks subscribers and creates `UncompletedCoroutinesError`.
- Drive *known* payloads via `repository.append(fixedList)`. No `Random` in test inputs unless seeded with a fixed seed.
- Per-test SQLite isolation via `TemporaryFolder` — every test gets a fresh `~/<tmp>/logs.db` and its own per-plugin DB directory.

### UI test tags

Tags are defined in plugin-internal `TestTags` / `UuidTestTags` objects. They are part of the public surface for tests; renaming requires updating tests.

**Log Viewer** (`plugins/ui/log-viewer/.../TestTags.kt`):
- `logViewer.logList` — the `LazyColumn` holding log rows
- `logViewer.logRow` — each rendered row
- `logViewer.filterInput` — the filter `BasicTextField`
- `logViewer.jumpToBottom` — the floating Jump-to-bottom `Button`
- `logViewer.packageLookupInput` — the Package UID lookup input
- `logViewer.packageLookupFind` — the Find button
- `logViewer.packageLookupResultRow` — each `(package, uid)` result row
- `logViewer.packageLookupCopyPackage` — per-row "Copy `package:…`" action
- `logViewer.packageLookupCopyUid` — per-row "Copy `--uid=…`" action
- `logViewer.loadingOlder` — the "Loading older…" row shown at the top of the log list while a load-older fetch is in flight

**UUID Grouping** (`plugins/ui/uuid-grouping/.../UuidTestTags.kt`):
- `uuidGrouping.uuidList` — the master `LazyColumn`
- `uuidGrouping.uuidRow` — each UUID row in the master list
- `uuidGrouping.tab` — every sub-tab in the strip (master + per-UUID detail tabs)
- `uuidGrouping.tabClose` — the `✕` inside non-master sub-tabs
- `uuidGrouping.detailList` — the `LazyColumn` inside a UUID detail sub-tab
- `uuidGrouping.detailRow` — each row in the detail list
- `uuidGrouping.detailLoading` — the loading indicator shown while the initial query runs
- `uuidGrouping.detailEmpty` — the empty-state message shown when the query returned zero matches
- `uuidGrouping.detailLoadingOlder` — the "Loading older…" row shown at the top of a UUID detail list while a load-older fetch is in flight

### Test commands

- Full suite: `./gradlew test` (runs all unit + integration + E2E).
- Just E2E: `./gradlew :app:jvmTest`.
- Single class: `./gradlew :app:jvmTest --tests "com.roideuniverse.loghound.e2e.LogViewerE2eTest"`.

---

## Appendix F — UI implementation patterns

How the Compose Desktop primitives realise the behaviors described in the UI framework section. This appendix is the "if you're regenerating the UI code" reference; the main body intentionally leaves these out so it reads as product behavior, not API reference.

### Virtually scrolled list with overlaid scrollbar and drag-select

```kotlin
val listState = rememberLazyListState()
Box(modifier = Modifier.fillMaxSize()) {
    SelectionContainer(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(TestTags.LOG_LIST),
        ) {
            items(items = entries, key = { it.id }) { entry -> LogRow(entry) }
        }
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(listState),
    )
}
```

The `SelectionContainer` wraps the `LazyColumn` so drag-select copy works across rows. The `VerticalScrollbar` is a sibling of the list inside a parent `Box`, aligned to the right edge.

### Jump-to-bottom button (Log Viewer)

```kotlin
val isAtBottom by remember {
    derivedStateOf {
        if (entries.isEmpty()) return@derivedStateOf true
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= entries.lastIndex - 2
    }
}
if (!isAtBottom) {
    Button(
        onClick = { coroutineScope.launch { listState.scrollToItem(entries.lastIndex) } },
        modifier = Modifier.align(Alignment.BottomEnd).testTag(TestTags.JUMP_TO_BOTTOM),
        // …colors…
    ) { Text("Jump to bottom ↓") }
}
```

### UUID Grouping sub-tabs

Per-UUID state is held in an internal `UuidDetailController` (`plugins/ui/uuid-grouping/.../internal/UuidDetailController.kt`):

```kotlin
internal class UuidDetailController(private val uuid: String) {
    val entries = mutableStateListOf<LogEntry>()
    val listState = LazyListState()
    private var job: Job? = null

    fun start(scope: CoroutineScope, repository: LogRepository) {
        if (job?.isActive == true) return
        job = scope.launch {
            val initial = repository.query(filter = LogFilter(textSearch = uuid), limit = 500)
            entries.addAll(initial.entries)
            if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
            repository.ingested.collect { batch ->
                val matched = batch.filter { it.message.contains(uuid, ignoreCase = true) }
                if (matched.isNotEmpty()) entries.addAll(matched)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
```

The plugin's `content` keeps a `Map<String, UuidDetailController>` keyed by UUID — opening a sub-tab calls `start`, closing calls `stop`. The `LazyListState` lives on the controller so scroll position survives tab switches.

### Package UID lookup

```kotlin
@Composable
fun PackageUidLookupBar(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var noDevice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Column(modifier = modifier) {
        Row { /* BasicTextField for `query` + Button for "Find" */ }
        if (noDevice) Text("No device", color = …)
        results.forEach { row ->
            Row(modifier = Modifier.testTag(TestTags.PACKAGE_LOOKUP_RESULT_ROW)) {
                Text(row.packageName)
                Text(row.uid.toString())
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(row.uid.toString())) },
                    modifier = Modifier.testTag(TestTags.PACKAGE_LOOKUP_COPY),
                ) { Text("⎘") }
            }
        }
    }
}
```

Internal helper signature:

```kotlin
internal suspend fun lookupPackageUids(query: String): List<PackageInfo> = withContext(Dispatchers.IO) {
    val adb = findAdbExecutable() ?: return@withContext emptyList()
    val proc = ProcessBuilder(adb, "shell", "pm", "list", "packages", "-U", query)
        .redirectErrorStream(true)
        .start()
    val out = proc.inputStream.bufferedReader().readText()
    proc.waitFor()
    PACKAGE_LINE_REGEX.findAll(out).map { m ->
        PackageInfo(packageName = m.groupValues[1], uid = m.groupValues[2].toInt())
    }.toList()
}
```

`findAdbExecutable()` is duplicated locally from the logcat plugin (Appendix D → ADB resolver order). On third use across plugins, this gets extracted to a shared helper.
