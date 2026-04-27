# LogHound — App Behavior Spec

LogHound is a desktop Android logcat viewer for developers who work with apps that produce GB-scale log output. It replaces Android Studio's built-in Logcat, which crashes or becomes unresponsive under heavy log volume.

### Target users

- Android developers debugging apps with high log output
- QA engineers analyzing test session logs
- Backend/mobile teams tracing distributed operations via log correlation IDs

### Platforms

- macOS, Windows, Linux

---

## Log input

The user can connect to logs in three ways:

- **Live device stream** — connect to a USB or wireless Android device via ADB and stream logcat output in real time
- **Open a log file** — load a saved logcat file from disk for post-hoc analysis
- **Watch a growing file** — observe a log file that another process is actively writing to, similar to `tail -f`

The app handles all three seamlessly. The user picks a source and the logs begin appearing. If a live connection drops, the app indicates the disconnection and allows reconnection.

---

## Log viewing

The main log view shows a scrollable list of log entries. Each entry displays: timestamp, process ID (PID), thread ID (TID), log priority level, tag, and message.

**Scrolling behavior:**
- The view automatically scrolls to show the newest logs as they arrive.
- When the user scrolls up, auto-scroll pauses so they can read without the view jumping.
- A "Jump to bottom" button appears in the lower-right corner of the log list whenever the user is not at the tail. Clicking it snaps to the latest entry and resumes auto-scroll on subsequent appends.
- A vertical scrollbar is visible on the right edge of the log list (and the same on any other long list, e.g. the UUID Grouping list and any UUID detail sub-tab).

**Display options:**
- Log lines are color-coded by priority level: Verbose=gray, Debug=blue, Info=green, Warning=amber, Error=red, Fatal=red bold
- A toggle switches between single-line (truncated) and multi-line (wrapped) display
- Timestamps can be displayed as absolute (12:01:03.445) or relative (+2.3s from previous line)

**Performance:**
- Scrolling remains smooth (60fps) even with millions of log lines
- The app uses constant memory regardless of how many logs are loaded — only visible lines are in memory
- The app must never put enough memory pressure on the OS to trigger system warnings or process killing

---

## Filtering

A single compact filter input sits at the top of the log view, modeled on Android Studio's Logcat filter bar. The user types a query and the view filters immediately as they type. There is no separate field per filter type; everything lives in one line.

**Query syntax:**
- Bare words match against the message text — `crash` shows lines whose message contains "crash"
- `key:value` clauses constrain a specific field. Recognized keys:
  - `tag:` — log tag contains the value
  - `level:` — minimum priority level (`V`, `D`, `I`, `W`, `E`, `F` or full names `verbose`/`debug`/...). Matches that level and above.
  - `pid:` — exact process ID
  - `tid:` — exact thread ID
  - `package:` — package name contains the value
- Quoted values allow spaces — `tag:"My Service"`
- Multiple clauses combine with AND — `tag:Activity level:W crash` shows Warning+ lines from tag "Activity" whose message contains "crash"
- A leading `/` switches the free-text portion to regex — `/^E\/.*timeout/`
- An empty input shows all logs

The input shows a placeholder hint when empty (e.g. `tag:Activity level:W package:mine`). The status bar shows matching lines out of total.

---

## Log actions

- **Copy** — drag-select one or more log lines (the log list is wrapped in a selection container) and copy them to the clipboard via the standard keyboard shortcut (`Cmd+C` on macOS, `Ctrl+C` on Windows/Linux). The same drag-select-to-copy behavior applies to the UUID Grouping list and to UUID detail sub-tabs.
- **Export** *(not yet implemented)* — export the currently filtered log view to a file on disk.

---

## Package UID lookup

Above the filter bar in the Log Viewer there's a small input field and a Find button. The user types all or part of a package name (e.g. `com.app.debug`); pressing Find queries the connected device for matching packages and shows a small list of `(package, uid)` rows underneath the input. When no device is reachable the Find button reports "No device" and stays disabled.

Each result row has two copy actions:

- **Copy `package:…`** — copies `package:com.app.debug` to the clipboard. Paste straight into the LogHound filter bar to scope the Log Viewer to that app.
- **Copy `--uid=…`** — copies `--uid=10231` to the clipboard. Paste into a terminal `adb logcat …` invocation to filter outside LogHound.

The first action assumes the in-app package filter works, which depends on the package-name resolution described below.

## Package-name resolution

Each ingested log line is annotated with the package name of the process that produced it. Resolution happens automatically while the logcat plugin is running:

- On device connect, the plugin takes a one-time snapshot of running processes and builds a `PID → package` map.
- The map refreshes every 30 seconds so newly-launched apps appear without restarting the session.
- Log lines whose PID isn't yet in the map are ingested with a `null` package; they get attributed on the next refresh (visible to the UI as soon as the next refresh completes — there is no retroactive backfill of older rows).
- Process names that include a sub-process suffix (e.g. `com.app.debug:remote`) are normalised to the parent package (`com.app.debug`).

Once a log line has a package, it stays — the package field is part of every persisted row, so the filter bar's `package:` clause and SQL queries work on it directly.

---

## Plugin system

LogHound is a plugin-based app. Every feature that displays logs — including the core log viewer itself — is a plugin. This ensures the plugin API is powerful enough to build any log analysis tool.

**Window shell:**
- A left sidebar lists every registered plugin by name. Clicking a plugin opens it as a tab in the content area, or focuses its existing tab if one is already open — plugins are never opened twice.
- A tab bar runs across the top of the content area. Each tab shows the plugin name and a close button (✕). Closing a tab falls back to focusing the left neighbor; the plugin remains available in the sidebar and can be reopened.
- The Core Log Viewer tab opens automatically on launch.
- The selected tab fills the remaining content area with the plugin's UI.
- A native menu bar provides: **File** (Exit), **Edit** (Preferences), **View** (Toggle Sidebar), **Help** (About). Menu items beyond Exit are skeleton no-ops in the current shell and will be wired up as features land.

**Detachable tabs (future):**
- Any tab can be detached from the main window into its own floating window by right-clicking the tab or dragging it out
- A detached tab can be re-docked by closing the floating window

**Plugin capabilities:**
- A plugin can query the ingested logs with filters and pagination
- A plugin can maintain its own data derived from the logs
- A plugin receives new logs incrementally as they are ingested — it does not need to rescan all logs
- A plugin provides its own UI within its tab

---

## Built-in plugins

### Core Log Viewer (built-in, always present)

The primary log viewing interface described in the sections above: real-time streaming view, filtering, scrolling, color coding, copy, export.

### UUID Grouping (built-in)

An analytical discovery tool that finds all UUID-shaped strings across the logs and groups log lines by them.

**Behavior:**
- The plugin applies a UUID regex pattern against every ingested log message and against every historical log on first run.
- It discovers all unique UUIDs automatically — the user does not need to know or enter a specific UUID.
- It presents a list of all discovered UUIDs with the count of log lines containing each.
- The UUID list is sortable by count or alphabetically and is searchable/filterable.
- The plugin always runs in the background — it processes ingested logs whether or not its tab is open. Closing the tab does not stop the work.
- Historical scans are checkpointed: on first run it scans every log; on subsequent runs (and after enable→disable→enable cycles in the future) it resumes from the last scanned log and only processes the gap.
- The UI remains responsive while a backfill is in progress. A progress indicator shows scan status and current UUID count. Partial results are immediately browseable.

**Click action:** clicking a UUID in the list opens (or focuses) a **sub-tab inside the UUID Grouping panel** showing every log line that contains that UUID, in chronological order. Multiple UUIDs can be opened in parallel — each gets its own sub-tab with a close (`✕`). Switching sub-tabs preserves scroll position. The sub-tab list reuses the Log Viewer's priority colors, vertical scrollbar, and drag-select-to-copy behavior, and stays live as new matching log lines are ingested.

The sub-tab labels show the first 8 characters of the UUID followed by `…`. The "UUIDs" sub-tab is always the leftmost and returns to the master list.

The detail view is implemented today via `LogRepository.query(filter = LogFilter(textSearch = uuid))` which scans cursor-paginated pages and matches in Kotlin. This is fine for synthetic data and single-device sessions; for huge logs (10M+ lines) a future full-text-search index on the main logs DB will make drill-down sub-millisecond. Adding that FTS index is a performance follow-up — it doesn't change UX.

**Scale targets:**
- Must remain responsive at **≥ 100,000 unique UUIDs** with **≥ 50,000,000 ingested log lines** scanned (e.g. 100K UUIDs × ~500 occurrences each).
- Designed to scale to ~1M UUIDs and 100M+ lines on the same architecture.
- Memory footprint of the UI is constant (renders only visible rows). On-disk plugin state grows linearly with unique UUIDs, not with occurrences.

**Detail tab loading state:** opening a UUID detail sub-tab shows a small loading indicator centered in the panel while the initial query runs against the logs database. Once the query completes the panel switches to either the list of matching log lines (sorted chronologically, with live updates appended as new matches arrive) or, if zero rows matched, a quiet "No log lines for this UUID" empty-state message. Live updates continue to populate the list in both cases — if the empty state turns up a match later, the list takes over.

**Use case:** A developer's app uses UUIDs as correlation IDs for operations, sessions, or requests. This plugin lets them instantly see all operations, how many log lines each produced, and drill into any one to follow its lifecycle through the logs.

### Synthetic data (development convenience)

For working on LogHound itself without a connected device, a built-in synthetic data source produces realistic-looking logcat lines at about 100 per second. The data deliberately includes a small reusable pool of UUIDs distributed unevenly — a handful are "hot" with many occurrences, most are rare — so the UUID Grouping plugin has interesting structure to reveal even on a fresh database.

Synthetic data is on by default during development; in real-device sessions you turn it off. The exact tag pool, message templates, and randomness knobs are pinned in the coding spec (Appendix D) so the demo data stays reproducible across regenerations.

---

## Status bar

The bottom of the window shows:
- Total number of log lines ingested
- Number of lines matching current filter (if filtering is active)
- Ingestion status (e.g., "Streaming...", "Ingesting...", "Complete", "Disconnected")

---

## Non-functional behavior

- The app starts and is interactive within a few seconds
- Filter changes feel responsive — results appear within a fraction of a second
- Ingestion does not need to be instantaneous but must be steady and reliable — it never stalls, never corrupts data, never crashes
- The app works with log sessions ranging from a few lines to tens of millions of lines without behavior changes

---

## Future plugin ideas (not in v1)

- Crash/ANR analyzer — group by exception type and stack trace similarity
- Performance profiler — detect timing patterns and slow operations
- Network request tracer — correlate request/response log pairs
- Custom regex grouping — user-defined patterns for any ID format, not just UUIDs
- Log diffing — compare logs between two sessions side by side
- AI-powered analysis — surface anomalies and patterns
