# LogHound — Coding Spec

### Tech stack

- Kotlin
- Compose Multiplatform for Desktop
- SQLite (via SQLDelight or Exposed)
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

### `LogDataStore` interface (:database module)

`LogDataStore` is the lower-level data-access contract. It deals in unfiltered cursor reads and bulk inserts; it knows nothing about `LogFilter` or `ingested` broadcasts.

```kotlin
interface LogDataStore {
    suspend fun insert(batch: List<LogEntry>)

    suspend fun selectTail(limit: Int): List<LogEntry>
    suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry>
    suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry>

    suspend fun countAll(): Long
}
```

This contract is what plugins import when they need raw access — e.g. UUID Grouping running its own indexed scan, or any plugin issuing custom SQL via the SQLDelight-generated query objects exposed alongside the implementation.

### Default implementation (`:database`, SQLDelight + SQLite)

- **Core table** — `logs(id, timestamp, pid, tid, priority, tag, message, package_name)`. Indexed on: `timestamp`, `tag`, `priority`, `pid`, `package_name`.
- WAL mode enabled for concurrent read/write performance.
- All queries are paginated — no unbounded result sets.
- Regex search uses SQLite's regex support or application-side filtering over indexed subsets.
- The `:database` module exposes a factory `createLogDataStore(databaseFile: File): LogDataStore` that opens the SQLite file, applies the schema, and returns the implementation.

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

**LogPage** — a single page of query results from `LogStore.query`:
- `entries: List<LogEntry>` — the rows for this page, in chronological order (id ascending) regardless of which cursor was used. Implementations reverse internally if needed so consumers always render in the same order.
- `hasMore: Boolean` — whether another page exists beyond this one in the same direction. The consumer uses the first/last entry's `id` as the next cursor.

---

## UI framework

Compose Multiplatform for Desktop.

**Window structure:**
- Single main window titled "LogHound" with a native menu bar, a left sidebar, a tab bar, a content area, and (future) a status bar
- Native menu bar: File (Exit), Edit (Preferences), View (Toggle Sidebar), Help (About). Only Exit is wired in the current shell; the rest are skeleton no-ops.
- Left sidebar: 220dp wide, light-gray background, lists every registered plugin by name. Clicking a name opens or focuses a tab.
- Tab bar: top of the content area. Each tab shows the plugin name plus a close button (✕). Closing a tab falls back to the left neighbor. The Core Log Viewer tab is opened automatically on launch.
- Content area: renders the active tab's `UIPlugin.content(Modifier.fillMaxSize())`.
- Status bar (future): log counts and ingestion status.

**Detachable tabs:**
- Each tab's content is a standalone composable with no dependency on its container
- Detaching a tab renders the same composable inside a new `Window`
- Closing a detached window re-docks the tab to the main window

**Virtual scrolling:**
- Log lists use Compose `LazyColumn`
- Backed by paginated database queries — only visible rows plus a small buffer are in memory
- Scroll position drives which page of data is fetched

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
