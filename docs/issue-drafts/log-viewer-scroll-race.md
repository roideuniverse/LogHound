# LogViewer: scrollToItem race causes performMeasureAndLayout reentrancy

> **To file this:** `gh auth login` (one-time, browser flow), then
> `gh issue create --title "LogViewer: scrollToItem race causes performMeasureAndLayout reentrancy" --label bug,log-viewer,tests --body-file docs/issue-drafts/log-viewer-scroll-race.md`
> (gh strips the H1 from the body when --title is provided, or you can delete this line and the heading first.)

## Summary

`LogViewerE2eTest.typing_filter_restricts_visible_rows_to_matches` consistently fails with:

```
java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  at androidx.compose.ui.node.MeasureAndLayoutDelegate.measureAndLayout-0kLqBqw
  at androidx.compose.foundation.lazy.LazyListState.snapToItemIndexInternal
  at androidx.compose.foundation.lazy.LazyListState$scrollToItem$2.invokeSuspend
```

The crash originates from one of `LogViewerPlugin.content`'s two `LaunchedEffect(filter)` blocks
calling `listState.scrollToItem(entries.lastIndex)` while a Compose measure pass is already in
flight. Compose's reentrancy guard rejects the nested layout request.

## Why it surfaces in tests but not in normal use

- **Normal use:** the user types one character every ~100ms. Each character triggers a filter
  change, a query, an `entries` mutation, and a scroll — but they land in distinct frames, so
  no two layout passes overlap.
- **Test:** `compose.onNodeWithTag(TestTags.FILTER_INPUT).performTextInput("level:W")` types
  all 7 characters back-to-back, synchronously. Each character cancels the in-flight
  `LaunchedEffect(filter)` and starts a new one; the coroutines from these effects pile up.
  When coroutine N's `scrollToItem` runs, the main thread is still inside a layout pass driven
  by coroutine N-1's `entries` mutation. The reentrancy guard fires.

## Reproduction

```sh
./gradlew :app:jvmTest --tests \
  "com.roideuniverse.loghound.e2e.LogViewerE2eTest.typing_filter_restricts_visible_rows_to_matches"
```

Consistently failing on Compose Multiplatform 1.10.3. The other tests in the same class
(passing initial state, etc.) pass — only the rapid-typing path triggers the race.

## Affected code

`plugins/ui/log-viewer/src/main/kotlin/com/roideuniverse/loghound/plugins/logviewer/LogViewerPlugin.kt`,
two `scrollToItem` call sites:

- Line ~80 — inside `LaunchedEffect(filter)` after the initial query
- Line ~100 — inside the ingested-collect loop, when `wasAtBottom`

Both are reachable during the test's typing storm.

## Proposed fixes (pick one)

1. **`requestScrollToItem`** — Compose foundation 1.7+ ships
   `LazyListState.requestScrollToItem(index)`, which schedules the scroll for the next layout
   pass instead of acquiring the layout lock synchronously. Drop-in replacement; smallest diff.
2. **Defer one frame** — `withFrameNanos { } ; listState.scrollToItem(entries.lastIndex)`.
   Lets the current measure pass finish before scrolling. Slight visible delay (one frame).
3. **Reactive scroll** — replace the imperative calls with
   `snapshotFlow { entries.lastIndex }.distinctUntilChanged().collect { listState.scrollToItem(it) }`.
   Compose handles the timing; clearer state intent.

(1) is the cheapest. (3) is the most idiomatic.

## Why this isn't fixed in the DSL plugin slice

The DSL work (`ks/dsl-plugin-proposal`) doesn't touch `LogViewerPlugin.kt`. The test was being
masked before the DSL slice because `UuidGroupingE2eTest.kt` had a stale call site
(`UuidGroupingPlugin(pluginDbFile)` — missing the repository arg) that broke the whole test
suite's compile. Fixing that constructor call to unblock script-host tests revealed this
pre-existing race.

Tracking it separately so the DSL slice can land green.

## Acceptance

- `LogViewerE2eTest` passes 10 consecutive runs without flakes
- Real-app behaviour unchanged: rapid filter changes still tail-follow without lag
