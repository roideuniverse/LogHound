# LogHound Backlog

Lightweight tracker for things noted but not scheduled. Each entry: a title,
the context that motivated it, and rough size. Most entries are short; entries
that are ready to file as a GitHub issue can be longer (full repro, fix paths,
acceptance criteria) so `gh issue create --body-file` can pull them straight.

Conventions:
- **Size:** S (under an hour), M (afternoon), L (multi-session), XL (cross-cutting)
- **Tags** in italics — module, area, or theme
- A leading `~~` strikethrough marks "done" entries; keep them as a record

---

## Bugs

- **LogViewer scrollToItem race in tests** — `LogViewerE2eTest.typing_filter_…`
  consistently fails with:

  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
    at androidx.compose.ui.node.MeasureAndLayoutDelegate.measureAndLayout-0kLqBqw
    at androidx.compose.foundation.lazy.LazyListState.snapToItemIndexInternal
    at androidx.compose.foundation.lazy.LazyListState$scrollToItem$2.invokeSuspend
  ```

  The crash originates from one of `LogViewerPlugin.content`'s two
  `LaunchedEffect(filter)` blocks calling `listState.scrollToItem(entries.lastIndex)`
  while a Compose measure pass is already in flight. Compose's reentrancy guard
  rejects the nested layout request.

  **Why it surfaces in tests but not in normal use.** In normal use the user types
  one character every ~100ms; each character triggers a filter change, query,
  `entries` mutation, and a scroll, but they land in distinct frames so layout
  passes don't overlap. In the test, `compose.onNodeWithTag(TestTags.FILTER_INPUT)
  .performTextInput("level:W")` types all 7 characters back-to-back, synchronously.
  Each character cancels the in-flight `LaunchedEffect(filter)` and starts a new
  one; coroutines from these effects pile up. When coroutine N's `scrollToItem`
  runs, the main thread is still inside a layout pass driven by coroutine N-1's
  `entries` mutation. The reentrancy guard fires.

  **Reproduction:**

  ```sh
  ./gradlew :app:jvmTest --tests \
    "com.roideuniverse.loghound.e2e.LogViewerE2eTest.typing_filter_restricts_visible_rows_to_matches"
  ```

  Consistently failing on Compose Multiplatform 1.10.3. Other tests in the same
  class (passing initial state, etc.) pass — only the rapid-typing path triggers
  the race.

  **Affected code.** `plugins/ui/log-viewer/src/main/kotlin/com/roideuniverse/loghound/plugins/logviewer/LogViewerPlugin.kt`,
  two `scrollToItem` call sites:
  - Line ~80 — inside `LaunchedEffect(filter)` after the initial query
  - Line ~100 — inside the ingested-collect loop, when `wasAtBottom`

  Both are reachable during the test's typing storm.

  **Proposed fixes (pick one):**

  1. **`requestScrollToItem`** — Compose foundation 1.7+ ships
     `LazyListState.requestScrollToItem(index)`, which schedules the scroll for the
     next layout pass instead of acquiring the layout lock synchronously. Drop-in
     replacement; smallest diff.
  2. **Defer one frame** — `withFrameNanos { } ; listState.scrollToItem(entries.lastIndex)`.
     Lets the current measure pass finish before scrolling. Slight visible delay
     (one frame).
  3. **Reactive scroll** — replace the imperative calls with
     `snapshotFlow { entries.lastIndex }.distinctUntilChanged().collect { listState.scrollToItem(it) }`.
     Compose handles the timing; clearer state intent.

  (1) is the cheapest. (3) is the most idiomatic.

  **Acceptance:** `LogViewerE2eTest` passes 10 consecutive runs without flakes;
  real-app behaviour unchanged (rapid filter changes still tail-follow without lag).

  *Size:* M *Tags:* compose-mp, log-viewer, tests

- **`LocalClipboardManager` deprecation** — `plugins/ui/log-viewer/.../PackageUidLookup.kt`
  uses the deprecated composition local; the suspending replacement is `LocalClipboard`.
  Build warning today, will be a hard error in some future Compose Multiplatform release.
  *Size:* S *Tags:* compose-mp, log-viewer

- **Plugin script-load failures are silent** — when a `.kts` plugin fails to compile
  under the script host (missing import, type error, runtime exception during
  the `plugin { }` builder), the host writes `[plugin-script] foo.kts failed to load:`
  to stderr and silently drops the plugin. Users running the bundled native app
  never see the terminal. Surface load failures as a visible "Plugin failed: foo"
  entry in the sidebar with the error text in the body.
  *Size:* M *Tags:* plugin-dsl, ux, error-surfacing

---

## Enhancements

- **FTS5 index on `logs.message` for fast substring filter** — `LogFilter.textSearch`
  (and the script's UUID detail-tab query) is the dominant slow path at scale:
  `LogRepositoryImpl.query` scans pages of 1000 rows and applies `filter.matches`
  in Kotlin until it collects `limit` matches. At 6M rows, opening a UUID detail
  tab can take many seconds.

  **Low-count UUIDs are the worst case**, not the best — a UUID with 3 total
  occurrences in 6M rows never reaches the 500-match limit, so the scan walks
  the entire DB before returning. High-count UUIDs at least short-circuit early.

  The fix is `behavior.spec.md`'s noted follow-up — an FTS5 virtual table on
  the message column. Xerial JDBC ships FTS5; SQLDelight can either generate
  against an FTS5 schema or we hand-write the queries. Plumbing:
  `selectByMessageMatch(query, limit)` + a trigger keeping the FTS table in
  sync on insert. Sub-millisecond detail-tab loads at any scale, including the
  sparse-match case.
  *Size:* L *Tags:* logs, sql, performance, fts

- ~~**UUID Grouping: store per-UUID `log_id` index, not just last**~~ — **Done.**
  `uuid_log(uuid, log_id PK)` populated during backfill + ingest;
  `UuidDetailController` looks up via `selectLogIdsForUuidDesc` then
  `LogRepository.queryByIds(...)`. Constant-time per UUID, no main-DB scan.
  Existing DBs auto-rebuild the per-occurrence table on next launch (one-shot
  cost paid the first time the upgraded plugin sees an old DB).
  Wider FTS path below still relevant for arbitrary `LogFilter.textSearch` queries.

- **UUID Grouping backfill: cut per-row allocation** — `UuidGroupingPlugin.backfill()`
  hydrates a full `LogEntry` per row scanned (id, timestamp, pid, tid, priority, tag,
  message, packageName). At 6M rows this is the dominant source of GC churn during
  the one-time scan (~800 MB working set observed). Cheap fix: add
  `selectIdAndMessageAfter(afterId, limit)` projecting only the two columns the regex
  needs, plus `WHERE message LIKE '%-%'` to skip rows that can't contain a UUID.
  Should drop per-row alloc by 60–70% with zero architectural change.
  *Size:* S *Tags:* uuid-grouping, performance, sql

- **UUID Grouping backfill: SQL-only via UDF + ATTACH** — bigger version of the above.
  Register a JDBC user-defined function on the SQLite connection that returns matched
  UUIDs from a message; ATTACH `logs.db` onto `uuid-grouping.db` so a single
  `INSERT INTO uuids SELECT …` can run server-side without hydrating any Kotlin
  objects. ~10× memory reduction, but introduces UDFs and cross-DB attaches that
  no other plugin uses today. Wait until a second consumer would benefit.
  *Size:* L *Tags:* uuid-grouping, performance, sql, architecture

- **Use `value class` for ID types** — Kotlin value classes (with `@JvmInline`)
  are zero-cost at runtime and prevent passing a `tag` where a `deviceId` is
  expected. Convention going forward: any new identifier type should be a
  `value class` from day one. `DeviceId(val raw: String)` lands with the
  multi-device data layer (no migration). Wholesale migration of existing
  `Long` log ids → `LogId` and `String` UUIDs → `Uuid` touches every call
  site and is best done as a separate slice.
  *Size:* M (full sweep), S (per type going forward) *Tags:* core-api, types

- **Migrate Compose deps to `libs.versions.toml`** — replace `compose.runtime`,
  `compose.foundation`, `compose.material3`, `compose.ui`, and `compose.desktop.uiTestJUnit4`
  inline references with catalog entries. Avoids IDE warnings about string-style
  accessors, keeps Compose versions in one file. `compose.desktop.currentOs` stays
  as-is (it resolves the platform-specific desktop bundle at apply time).
  Catalog entries already added; build files not yet migrated.
  *Size:* S *Tags:* gradle, dx

- **DSL: `padding` modifier verb** — empty / loading states in detail tabs sit
  flush-left because the DSL has no per-element padding. Today scripts wrap in a
  `section { }` (which forces toolbar styling) or eat the alignment. A
  `padding(horizontal, vertical) { … }` wrapping verb would let scripts pad an
  arbitrary subtree without inheriting section's surface.
  *Size:* S *Tags:* plugin-dsl, ux

- **DSL: `loadOlder` callback for `list`** — built-in plugins fire a callback when
  the user scrolls within ~10 rows of the top, paginating older entries on demand.
  The DSL `list` verb has no equivalent. Adding it as a `list(items, key,
  onScrolledNearTop) { … }` parameter would let scripts replicate Log Viewer's
  scroll-up history fetch.
  *Size:* M *Tags:* plugin-dsl, scrolling

- **DSL: tail-following auto-scroll** — Log Viewer auto-scrolls to the latest entry
  when the user is parked at the bottom and a new entry arrives. The DSL `list`
  doesn't expose any scroll control, so script-based log viewers can't replicate
  this. Either add an `autoScrollToBottom` flag on `list`, or expose a
  `listController` analogous to `tabController` with `requestScrollToBottom()`,
  `requestScrollToItem(idx)`, and a `firstVisibleIndex` state.
  *Size:* M *Tags:* plugin-dsl, scrolling

- **Detail tab pagination in scripted plugins** — example UUID Grouping `.kts` queries
  `repo.query(filter = LogFilter(textSearch = uuid), limit = 500)` once per opened
  tab. Built-in plugin paginates older entries on scroll-up via `UuidDetailController`
  with `loadOlder()`. Once the DSL grows the `loadOlder` callback above, port the
  example script to use it.
  *Size:* S *Tags:* plugin-dsl, examples

- ~~**Hello DSL plugin: convert to `.kts` example or delete**~~ — **Done.**
  Moved to `examples/plugins/hello.kts` as a ~25-line starter template
  alongside the full `uuid-grouping.kts`. In-tree `HelloDsl.kt` and its
  `main.kt` registration removed. README points at both examples.

- **Window state persistence** — sidebar-visible toggle, currently active tab,
  window size and position all reset on launch. A small SQLite or JSON file in
  `~/.loghound/state.json` would persist these. Avoid heavy state libraries; a
  10-line saved-on-close struct is enough.
  *Size:* S *Tags:* app, ux

- **Plugin enable/disable toggle** — a user can't temporarily disable a loaded
  `.kts` plugin without removing the file. A right-click menu on the sidebar
  entry → "Disable" → re-enabled later → makes triage easier when one plugin
  misbehaves. Can be disk-state in the same `~/.loghound/state.json` as window
  state.
  *Size:* M *Tags:* plugin-dsl, ux

- **Logcat disconnect notification** — `LogcatDataPlugin` retries silently on
  device disconnect (USB unplug, adb server restart). User has no visual signal
  that the live stream stopped. Status bar text or a sidebar dot would help.
  Behaviour is already in `behavior.spec.md`; it's just not wired up.
  *Size:* M *Tags:* logcat, ux

- **CI on pull requests** — no `.github/workflows/` today. A minimal job that
  runs `:plugin-dsl:test :app:jvmTest --tests "…PluginScriptHostTest" :core-impl:test`
  on every PR would catch regressions in the DSL contract and the script-host
  loader without requiring a UI runner.
  *Size:* S *Tags:* ci, dx

- **Sessions: end & archive, load on demand** — live `logs.db` only ever
  contains the *current* session. "End session" snapshots the live state
  to disk and resets to empty. Past sessions are browseable on explicit
  demand, never mixed into the live view.

  **Data layer:**
  - `sessions` registry table (`id`, `name`, `started_at`, `ended_at`,
    `archive_dir`) in a small `~/.loghound/sessions.db`.
  - "End session" copies `~/.loghound/logs.db` to
    `~/.loghound/archives/<session-id>/logs.db`, also copies each
    plugin's `~/.loghound/plugins/<id>.db` into the archive dir, then
    truncates the live DB and the plugin DBs via the clear-store hook.
  - Default session name is the start timestamp (`2026-05-05 14:32`);
    user can rename in the picker. Optional one-line description.

  **UI** (mirrors the multi-device pill pattern):

  *Status-bar session pill* — always visible, bottom of the window
  beside the device pill. Format: `⏱ <name> · <duration> · <line-count>
  [▾]`. Click `[▾]` → quick menu with: current session details (name
  editable inline, started-at, lines, size, devices), an `End & Start
  New Session` button, and a "Recent archives" list (3-5 most recent)
  with a `Browse all archives…` link.

  *Sessions sidebar plugin* — full management surface. Two sections:
  - **Current** — name (editable), started_at, line count, size on
    disk, contributing devices, primary actions (`End & Archive`,
    `Cancel & Reset` as escape-hatch for "drop without archiving").
  - **Archived sessions** — searchable, sortable list. Each row:
    name, start→end timestamps, duration, line count, size, device
    count. Per-row actions: `Open` · `Rename` · `Export…` · `Delete`.
    "Export" zips the archive directory for sharing/backup.

  *Archive sub-tabs* — clicking `Open` on an archive opens a read-only
  sub-tab inside the Sessions plugin (same `tabController` + `tabs`
  pattern as UUID Grouping). The sub-tab reuses the Log Viewer UI but
  binds to the archive's `logs.db` instead of the live one, with
  rendering dimmed/italic and a banner reading
  `⚠ Viewing archive · <date range> · <line count>`. Multiple archives
  can be open in parallel as sibling sub-tabs.

  *End & Start New confirm dialog* — preview of what will be archived
  (line count, size), an editable name field pre-filled with the
  current session's name, and a reminder that "ADB keeps streaming" so
  the user knows ingest doesn't pause.

  *Empty/first-launch state* — Sessions plugin shows "This is your
  first session…" guidance plus "Archived sessions: None yet." Status
  bar shows `⏱ New session · 0 lines`.

  *Live ingest never stops* — adb keeps writing to the live DB even
  while the user browses an archive. Switching back to the live tab
  resumes the user's live view of accumulated lines.

  **Plugin SDK:**
  - Reuses the clear-store hook (`DataPlugin.clearStore()`) — fired when
    the host ends a session. Plugins drop their derived state; the new
    session starts clean.
  - For plugins that want to view archives (UUID Grouping in archive
    mode, etc.), the host opens the archive's plugin DB read-only and
    passes it to the plugin via the Archive sub-tab path. Most plugins
    can ignore archive viewing initially — only the Archive Viewer
    plugin needs to render historical data.

  **Boundaries:**
  - Sessions are orthogonal to devices: an archive can contain logs from
    multiple devices; a single device contributes to whichever session
    is live when its lines arrive.
  - V1 shows live OR an archive at a time, not both side-by-side. Cross-
    session comparison is a future detached-window enhancement.
  - Archives are read-only — no editing, no resuming. Delete via the
    picker if disk fills up.

  *Size:* L *Tags:* sessions, app, plugin-dsl, core-api, ux

- **In-app "Clear logs" menu item + plugin clear-store hook** — `File → Clear
  logs…` with a confirmation dialog. Deletes `~/.loghound/logs.db` (+ `-shm` /
  `-wal`) after closing the live `LogDataStore` connection, then reopens on a
  fresh schema. Plugins also need to drop their derived state when the source
  is wiped — otherwise e.g. UUID Grouping ends up with UUIDs pointing at log
  IDs that no longer exist. Two pieces:

  1. Add `suspend fun clearStore() = Unit` to the `DataPlugin` interface
     (default no-op for plugins with no persistent state). Built-in
     `UuidGroupingPlugin` overrides it to drop its `uuids.db`.
  2. For DSL plugins: either an `onClear { … }` block in the `plugin { }`
     builder, OR have the host auto-wipe `pluginDataDir()` for any plugin
     that opted into it (cleaner — convention not callback). Plugins
     observe via a "store cleared" signal so they can re-initialise.

  Confirmation dialog should show what will be wiped (logs.db size, list of
  plugin DBs by id) so the user isn't surprised. Today's workaround is
  `rm -f ~/.loghound/logs.db*` and `rm -rf ~/.loghound/plugins/*.db*` while
  the app is closed.
  *Size:* M *Tags:* app, ux, plugin-dsl, core-api

---

## Features

- **Plugin author skill doc** — once `.kts` drop-in stabilises, ship a single-page
  Markdown skill doc that an AI agent can be given to author plugins. Should
  list every verb with one-line example, the `pluginDataDir()` convention, the
  `@file:DependsOn` setup for SQLite/Exposed plugins, and a worked clone of UUID
  Grouping. Defer until the verb surface stops changing weekly.
  *Size:* M *Tags:* plugin-dsl, docs

- **Plugin sandboxing** — today a `.kts` plugin runs with full JVM authority
  (file I/O, network, process spawning). Worth revisiting when there's any
  scenario where a plugin author isn't fully trusted by the user.
  *Size:* XL *Tags:* plugin-dsl, security

- **Hot reload of `.kts` plugins** — drop a file, see it without relaunch.
  Requires watching the directory and re-evaluating changed scripts safely
  (cancel old `data { }` coroutine, swap UI content). Nice-to-have; cold start
  is fast enough that a relaunch is barely noticeable today.
  *Size:* L *Tags:* plugin-dsl, dx

- **Multi-device support** — connect to more than one ADB device at once and
  keep their streams distinguishable end-to-end. Four layers:

  1. **Data:** `LogcatDataPlugin` runs an `adb logcat -v threadtime` per
     connected device (poll `adb devices` for the active set, restart on
     disconnect). `logs` schema gains a `device_id TEXT NOT NULL` column,
     and `LogEntry.deviceId: DeviceId` (value class, non-null) — every log
     entry has a source. Implications:
     - **File ingest** (open a saved logcat file, watch a growing file)
       synthesises a deviceId from the file path (e.g. `file:session.log`).
       The user can rename via the device list.
     - **`inject_logs.py`** gains a required `--device-id <id>` flag with
       a sensible default (e.g. `inject:<filename>`).
     - Existing `~/.loghound/logs.db` from before this change is wiped at
       upgrade — fresh schema, no migration code. Document the one-time
       `rm -f ~/.loghound/logs.db*` step in the release notes for this
       change.
     - Index on `(device_id, id)` for scoped scans.
  2. **Filter:** `LogFilter` gains a `deviceId: String?` field; filter bar
     parses `device:<serial-or-name>`. Existing clauses (`tag:`, `level:`)
     compose AND as today. Built-in plugins and DSL scripts both reach
     this through `repo.query(filter = LogFilter(deviceId = …))`.
  3. **UI:** Log Viewer presents per-device sub-tabs (master "All" plus one
     per connected device, similar pattern to UUID Grouping's drill-down).
     Each sub-tab applies an implicit `device:` filter and the user's
     explicit filter on top. Closing a tab leaves the device connected;
     reopening from the device list restores it.
  4. **Plugin SDK surface:** plugins (built-in *and* DSL `.kts`) need
     first-class device awareness, not have to re-derive it. Add:

     - `data class Device(val id: String, val label: String)` to `core-api`
     - `LogRepository.devices: Flow<Set<Device>>` — observable connected
       set. Plugins observe via `repo.devices.collect { … }` or
       `snapshotFlow` adapter.
     - `LogFilter.deviceId` (already in layer 2) — same field plugins use.
     - DSL `.kts` plugins inherit all of the above through the existing
       script-host default imports (`LogFilter`, `LogRepository`).
     - Optional: a `deviceController` analogous to `tabController` for
       plugins that want per-device sub-tabs in the same shape that
       UUID Grouping uses today. Adds three states (open devices, active,
       activate/close methods); reuse the existing `tabs(...)` verb so
       no new UI primitive needed.

  This way a plugin author can write a Network Tracer or Performance
  Profiler that automatically scopes per device with no special code,
  matching the built-in Log Viewer's behavior.

  Unlocks the per-device UUID Grouping filter below.
  *Size:* XL *Tags:* logcat, schema, log-viewer, plugin-dsl, ux

- **UUID Grouping: filter by device** — once `logs` carries a `device_id`,
  the plugin's `uuid_log` table should mirror it (`(uuid, log_id, device_id)`
  with index on `(device_id, uuid)`). Master view gets a device picker that
  scopes both the `uuids`-summary list and the per-UUID detail. Useful when
  a single workflow correlates across devices and you want to see only the
  one you care about. Depends on multi-device support — without `device_id`,
  there's nothing to filter on.
  *Size:* M *Tags:* uuid-grouping, ux *Depends:* multi-device support

- **Detachable tabs into floating windows** — `behavior.spec.md` lists this as
  a future plugin shell capability: any tab can be detached by right-click or
  drag-out, and re-docked by closing the floating window. Touches `app/App.kt`
  and the tab management infrastructure.
  *Size:* L *Tags:* app, plugin-shell

- **Export filtered logs to file** — `behavior.spec.md` declares this as a
  Log Viewer "not yet implemented" action. Should write the currently visible
  filter result to a `.log` file in user-chosen location. Probably file picker
  via Compose Multiplatform's file dialog wrappers.
  *Size:* M *Tags:* log-viewer, export

- **Performance benchmark at scale targets** — `behavior.spec.md` declares
  ≥ 100K UUIDs / ≥ 50M lines for UUID Grouping and "tens of millions of lines
  without behavior changes" for the app overall. We have no automated check
  that we still hit those bars after each refactor. Add a benchmark suite that
  ingests a synthetic 50M-line corpus, measures backfill time, peak heap, and
  steady-state memory; record in CI artefacts.
  *Size:* L *Tags:* tests, performance

---

## Decided "no" (for context)

- **Bake Exposed into the host SDK** — would ship ~3–4 MB of JARs in the desktop
  bundle and pin a framework choice on plugin authors. Plugins use
  `@file:DependsOn` for whatever ORM they want; host stays neutral.
  Recorded in [`specs/plugins.spec.md`](../specs/plugins.spec.md).
