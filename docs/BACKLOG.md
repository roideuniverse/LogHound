# LogHound Backlog

Active work is tracked in **[GitHub issues](https://github.com/roideuniverse/LogHound/issues)**.

This file keeps two things that don't belong on GitHub:

1. A record of what's been **done** in past slices, since closed issues / merged PRs
   already cover the past for issue-tracked work but the items below predate that
   migration.
2. **Decisions to *not* do something**, with the rationale, so future contributors
   don't propose them again.

When triaging a new idea, file it as a GitHub issue directly. Don't add it here.

---

## Done (pre-issue-tracker)

These shipped before active work moved to GitHub issues. Kept as a record of how
the codebase got its current shape.

- **UUID Grouping: store per-UUID `log_id` index, not just last** —
  `uuid_log(uuid, log_id PK)` populated during backfill + ingest;
  `UuidDetailController` looks up via `selectLogIdsForUuidDesc` then
  `LogRepository.queryByIds(...)`. Constant-time per UUID, no main-DB scan.
  Existing DBs auto-rebuild the per-occurrence table on next launch.

- **Hello DSL plugin: convert to `.kts` example or delete** —
  Moved to `examples/plugins/hello.kts` as a ~25-line starter template
  alongside the full `uuid-grouping.kts`. In-tree `HelloDsl.kt` and its
  `main.kt` registration removed; README points at both examples.

---

## Decided "no"

- **Bake Exposed into the host SDK** — would ship ~3–4 MB of JARs in the desktop
  bundle and pin a framework choice on plugin authors. Plugins use
  `@file:DependsOn` for whatever ORM they want; host stays neutral.
  Recorded in [`specs/plugins.spec.md`](../specs/plugins.spec.md).
