# LogHound Backlog

Lightweight tracker for things noted but not scheduled. Each entry: a one-line title,
the context that motivated it, and rough size. Promote items into GitHub issues when
they're ready to work on (drafts can live in `docs/issue-drafts/` first).

Conventions:
- **Size:** S (under an hour), M (afternoon), L (multi-session), XL (cross-cutting)
- **Tags** in italics — module, area, or theme

---

## Bugs

- **LogViewer scrollToItem race in tests** — `LogViewerE2eTest.typing_filter_…` fails
  with `performMeasureAndLayout called during measure layout` when `performTextInput`
  fires multiple filter changes back-to-back. Pre-existing; surfaced once the
  UuidGroupingE2eTest compile error was fixed. Three fix paths in the issue draft
  (requestScrollToItem / withFrameNanos / snapshotFlow). Real-app behavior unaffected.
  See [`docs/issue-drafts/log-viewer-scroll-race.md`](issue-drafts/log-viewer-scroll-race.md).
  *Size:* M *Tags:* compose-mp, log-viewer, tests

- **`LocalClipboardManager` deprecation** — `plugins/ui/log-viewer/.../PackageUidLookup.kt`
  uses the deprecated composition local; the suspending replacement is `LocalClipboard`.
  Build warning today, will be a hard error in some future Compose Multiplatform release.
  *Size:* S *Tags:* compose-mp, log-viewer

---

## Enhancements

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

- **Multi-device support** — connect to more than one ADB device and label
  log streams per source. Touches `plugins/data/logcat`, schema (add
  `device_id` column), and Log Viewer filter syntax (`device:…`).
  *Size:* L *Tags:* logcat, schema

---

## Decided "no" (for context)

- **Bake Exposed into the host SDK** — would ship ~3–4 MB of JARs in the desktop
  bundle and pin a framework choice on plugin authors. Plugins use
  `@file:DependsOn` for whatever ORM they want; host stays neutral.
  Recorded in [`specs/plugins.spec.md`](../specs/plugins.spec.md).
