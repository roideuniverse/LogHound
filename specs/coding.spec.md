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

- **core-api** — domain module. Interfaces, data models, and the plugin API contract only. No implementations, no UI. Everything else depends on this; this depends on nothing in the project.
- **app** — DI/wiring layer. Assembles and connects implementations, registers plugins, owns the window shell, tab management, and theme.

Where implementations (ingester, parser, storage, input sources, plugins) live is **TBD** — to be decided as the architecture takes shape. New modules are introduced only when real code makes the boundary obvious, not speculatively.

---

## Input layer

A `LogInputStream` interface provides a uniform way to consume log data from any source.

- Implementations: ADB subprocess stream, static file reader, growing file watcher
- The interface delivers batches of raw text lines to the ingester
- ADB integration spawns `adb logcat` as a child process and reads its stdout
- Growing file implementation watches for file changes and reads appended content
- Reconnection and error handling are the responsibility of each implementation

---

## Parser

Parses the standard logcat `-v threadtime` format:

```
MM-DD HH:MM:SS.mmm  PID  TID  PRIORITY  TAG: MESSAGE
```

Extracts: timestamp, PID, TID, priority level (V/D/I/W/E/F/S), tag, message.

- Malformed lines are skipped and logged, never cause a crash
- Package name resolution from PID is optional and best-effort

---

## Ingester

The ingester connects the input layer to storage:

- Reads batches from `LogInputStream`
- Passes each line through the parser
- Batch-inserts parsed entries into SQLite
- Notifies all registered plugins with each ingested batch

**Constraints:**
- Bounded buffer — never hold more than a configurable batch size in memory (default: 1000 lines)
- Inserts are batched within a single transaction for throughput
- The ingester runs on a background thread, never blocks the UI

---

## Storage

SQLite is the sole storage engine. All log data lives on disk, queried on demand.

**Core table:**
- `logs` — one row per log line with columns: id, timestamp, pid, tid, priority, tag, message, package_name
- Indexed on: timestamp, tag, priority, pid, package_name

**Operational modes:**
- WAL mode enabled for concurrent read/write performance
- All queries are paginated — no unbounded result sets
- Regex search uses SQLite's regex support or application-side filtering over indexed subsets

**Plugin storage:**
- Each plugin can create its own tables, namespaced by plugin ID to avoid collisions
- Plugins have read-only access to the core `logs` table
- Plugins have read-write access to their own scoped tables

---

## Plugin API

Every visual feature is a plugin, including the core log viewer.

**UIPlugin contract** (current — covers the rendering surface only):
- `id` — unique string identifier
- `name` — human-readable display name
- `content(modifier)` — a Composable that renders the plugin's UI inside the supplied `Modifier` (the tab content area passes `Modifier.fillMaxSize()`)

The lifecycle/data side of the plugin contract (`onActivate`, `onDeactivate`, `onLogsIngested`, scoped storage, `PluginContext` queries) will be reintroduced when the ingester and storage layers land. The current `UIPlugin` is deliberately the smallest interface that lets the window shell render a plugin in a tab. `core-api/PluginContext` already defines the future query surface.

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
