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
- The view automatically scrolls to show the newest logs as they arrive
- When the user scrolls up, auto-scroll pauses so they can read without the view jumping
- A button or scrolling to the bottom resumes auto-scroll

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

A filter bar at the top of the log view provides:

- **Tag filter** — show only logs whose tag contains the entered text
- **Log level filter** — dropdown to select minimum priority level (e.g., selecting Warning shows Warning, Error, and Fatal)
- **PID/TID filter** — show only logs from a specific process or thread ID
- **Text search** — show only logs whose message contains the entered text
- **Regex search** — a checkbox enables regex mode for the search field
- **Package name filter** — show only logs from a specific app package

All active filters combine with AND logic — a log line must match every active filter to appear.

Filters apply immediately as the user types. The status bar shows how many lines match the current filter out of the total.

---

## Log actions

- **Copy** — select one or more log lines and copy them to the clipboard via right-click context menu or keyboard shortcut
- **Export** — export the currently filtered log view to a file on disk

---

## Plugin system

LogHound is a plugin-based app. Every feature that displays logs — including the core log viewer itself — is a plugin. This ensures the plugin API is powerful enough to build any log analysis tool.

**How plugins appear to the user:**
- Each plugin occupies a tab in the main window
- The core log viewer is always the first tab and cannot be removed
- Additional plugin tabs appear after it
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
- The plugin applies a UUID regex pattern against every ingested log message
- It discovers all unique UUIDs automatically — the user does not need to know or enter a specific UUID
- It presents a list of all discovered UUIDs with the count of log lines containing each
- The UUID list is sortable by count or alphabetically
- The UUID list is searchable/filterable
- Clicking a UUID shows all log lines that contain it, in chronological order
- If 500 different UUIDs appear across the logs, the user sees 500 groups

**Use case:** A developer's app uses UUIDs as correlation IDs for operations, sessions, or requests. This plugin lets them instantly see all operations, how many log lines each produced, and drill into any one to follow its lifecycle through the logs.

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
