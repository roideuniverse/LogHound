#!/usr/bin/env python3
"""Inject logcat threadtime entries into LogHound's SQLite DB.

Reads logcat lines from a file or stdin, parses the threadtime format,
and inserts them directly into the LogHound logs database. The DB must
already exist (launch the app once to create the schema, or point --db
at an existing database file).

Examples:

  # Replay a saved session captured earlier with `adb logcat -v threadtime`
  scripts/inject_logs.py --input session.log

  # Stream live captures into the DB without running the app
  adb logcat -v threadtime | scripts/inject_logs.py

  # Custom DB path
  scripts/inject_logs.py --db /tmp/test-logs.db --input fixture.log

Caveat: the LogHound app's `ingested` Flow only fires for in-process
appends. Rows added by this script while the app is running won't show
up in the UI live — the data is on disk, but the app needs to re-run
its initial query (close + reopen the tab, or relaunch) to pick them up.
"""
from __future__ import annotations

import argparse
import re
import signal
import sqlite3
import sys
from pathlib import Path
from typing import Optional, TextIO

# Mirrors plugins/data/logcat/.../LogcatThreadtimeParser.kt and Appendix C.
THREADTIME_RE = re.compile(
    r"^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+"
    r"(\d+)\s+(\d+)\s+([VDIWEFS])\s+([^:]+?):\s?(.*)$"
)

# Priority labels are stored in SQLite as the enum NAME, matching
# core-api/.../LogPriority.kt and what LogRepositoryImpl writes.
PRIORITY_NAMES = {
    "V": "Verbose",
    "D": "Debug",
    "I": "Info",
    "W": "Warn",
    "E": "Error",
    "F": "Fatal",
    "S": "Silent",
}

BATCH_SIZE = 100
INSERT_SQL = (
    "INSERT INTO logs"
    "(timestamp, pid, tid, priority, tag, message, package_name) "
    "VALUES (?, ?, ?, ?, ?, ?, ?)"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Inject logcat threadtime entries into LogHound's SQLite DB.",
    )
    parser.add_argument(
        "--db",
        default=str(Path.home() / ".loghound" / "logs.db"),
        help="Path to logs.db (default: ~/.loghound/logs.db)",
    )
    parser.add_argument(
        "--input",
        help="Logcat file to read (default: stdin)",
    )
    return parser.parse_args()


def open_source(path: Optional[str]) -> TextIO:
    return open(path) if path else sys.stdin


def main() -> int:
    args = parse_args()

    db_path = Path(args.db)
    if not db_path.exists():
        print(f"error: db not found at {db_path}", file=sys.stderr)
        print(
            "hint: launch LogHound once to create the schema, "
            "or pass --db <path/to/existing.db>",
            file=sys.stderr,
        )
        return 1

    src = open_source(args.input)

    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA synchronous = NORMAL")

    counters = {"inserted": 0, "skipped": 0}

    def flush_and_close() -> None:
        try:
            conn.commit()
        finally:
            conn.close()

    def on_sigint(_sig, _frame):  # type: ignore[no-untyped-def]
        print(
            f"\nflushing… ({counters['inserted']} inserted, "
            f"{counters['skipped']} skipped)",
            file=sys.stderr,
        )
        flush_and_close()
        sys.exit(130)

    signal.signal(signal.SIGINT, on_sigint)

    cur = conn.cursor()
    try:
        for line in src:
            line = line.rstrip("\n")
            match = THREADTIME_RE.match(line)
            if not match:
                counters["skipped"] += 1
                continue
            timestamp, pid_str, tid_str, prio, tag, message = match.groups()
            cur.execute(
                INSERT_SQL,
                (
                    timestamp,
                    int(pid_str),
                    int(tid_str),
                    PRIORITY_NAMES[prio],
                    tag.strip(),
                    message,
                    None,
                ),
            )
            counters["inserted"] += 1
            if counters["inserted"] % BATCH_SIZE == 0:
                conn.commit()
        flush_and_close()
    except Exception as exc:
        conn.rollback()
        conn.close()
        print(f"error: {exc}", file=sys.stderr)
        return 1
    finally:
        if args.input:
            src.close()

    print(
        f"inserted {counters['inserted']} rows; "
        f"skipped {counters['skipped']} unparseable lines"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
