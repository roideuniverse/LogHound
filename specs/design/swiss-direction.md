# Modern Swiss Utility Direction

> **Status:** chosen design direction (2026-05-19).
>
> This is the PRD that Google Stitch generated from
> [`design-brief.md`](../design-brief.md), reproduced verbatim for the
> historical record. Two other directions were generated alongside it
> ("Linear Minimalist" and "IDE Classic") — they were not chosen.
>
> Implementation is incremental, one concern per PR:
>
> 1. ✅ Tokens in `design/LogHoundDesign.kt` ([#32](https://github.com/roideuniverse/LogHound/pull/32) — colors only; fonts deferred to step 2)
> 2. ✅ Bundle Instrument Sans + JetBrains Mono and switch font families (this PR)
> 3. Badge-pill priority renderer in log rows (visual replacement for colored text)
> 4. Hover / click-to-expand row states
> 5. Sub-tab strip layout updates to match Swiss heights / borders
> 6. Multi-device sub-tabs + status-bar pill ([issue #26](https://github.com/roideuniverse/LogHound/issues/26))
> 7. Sessions plugin + status-bar pill ([issue #21](https://github.com/roideuniverse/LogHound/issues/21))
> 8. Filter Builder popover ([new issue when scoped])
>
> Each PR should land green against the existing screens before the next
> begins; if a Swiss decision conflicts with shipped behavior, the spec
> wins — file an issue, don't paper over.

---

## Original PRD

## Product Overview

**The Pitch:** LogHound is a native-feeling macOS utility that tames Android logcat output. It transforms chaotic terminal streams into a scannable, filterable, and highly organized typographic interface.

**For:** Android engineers and QA testers who need to parse thousands of lines of log data quickly without visual fatigue.

**Device:** desktop

**Design Direction:** A flat, 'borderless' Modern Swiss utility focusing on strict typographic hierarchy, subtle background color shifts, and high data density. Tactile pills and sophisticated monospace typography guide the eye.

**Inspired by:** Proxyman, Linear, Apple Instruments.

---

## Screens

- **Log Viewer:** Live streaming logcat feed with device and severity filtering
- **UUID Grouping:** Logs clustered by transaction or request UUIDs
- **Sessions:** Historical log archives from previous debugging runs
- **Filter Builder:** Advanced query constructor for complex log isolation

---

## Key Flows

**Filter Live Stream:** Isolate a specific crash on a specific device.

1. User is on Log Viewer -> sees active stream from 2 devices
2. User clicks 'Pixel 8' sub-tab -> view filters immediately to only Pixel 8 logs
3. User types "Exception" in toolbar -> logs filter to matching strings, highlighting matches in `#FBEAEE`

---

<details>
<summary>Design System</summary>

## Color Palette

- **Primary:** `#000000` - Active states, primary text
- **Background:** `#FFFFFF` - Main log view area
- **Surface:** `#F7F7F7` - Sidebar, toolbar, status bar
- **Text:** `#1A1A1A` - Standard UI text
- **Muted:** `#8C8C8C` - Timestamps, inactive tabs, borders
- **Accent Blue:** `#0066FF` - Pixel 8 identifier, informational logs
- **Accent Orange:** `#FF6B00` - Emulator identifier, warnings
- **Accent Red:** `#E52E2E` - Error logs, critical failures
- **Accent Green:** `#00A04C` - Success states, verbose logs

## Typography

Distinctive, highly legible typography designed for prolonged reading of technical data.

- **Headings:** Instrument Sans, 600, 14px
- **Body UI:** Instrument Sans, 500, 13px
- **Small UI:** Instrument Sans, 500, 11px
- **Log Data:** JetBrains Mono, 400, 12px (Line height 20px)
- **Log Meta:** JetBrains Mono, 500, 11px

**Style notes:** 6px border radii on pills, 1px `#E5E5E5` borders dividing major panels, zero drop shadows. Reliance on subtle background changes (`#F7F7F7` to `#FFFFFF`) to define spatial hierarchy.

## Design Tokens

```css
:root {
  --color-primary: #000000;
  --color-bg: #FFFFFF;
  --color-surface: #F7F7F7;
  --color-border: #E5E5E5;
  --color-text: #1A1A1A;
  --color-text-muted: #8C8C8C;
  --color-dev-1: #0066FF;
  --color-dev-2: #FF6B00;
  --font-ui: 'Instrument Sans', sans-serif;
  --font-mono: 'JetBrains Mono', monospace;
  --radius-pill: 6px;
  --radius-window: 10px;
  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
}
```

</details>

---

<details>
<summary>Screen Specifications</summary>

### Log Viewer

**Purpose:** Primary workspace for monitoring real-time Android device logs.

**Layout:** 180px fixed left sidebar (`#F7F7F7`). Fluid right content area (`#FFFFFF`). Top 48px toolbar. Top 36px sub-tab strip. Bottom 28px status bar.

**Key Elements:**
- **Sidebar Nav:** 28px height items, 8px padding, `Instrument Sans 13px`. Active state gets `#EBEBEB` background and `#000000` text.
- **Sub-tab Strip:** 36px height, bottom border `1px solid #E5E5E5`. Items: "All devices" (active, bold), "Pixel 8" (includes 6px `#0066FF` dot), "Emu Pix 7" (includes 6px `#FF6B00` dot).
- **Toolbar Input:** 32px height, 100% width of right panel (minus 32px padding). Background `#EBEBEB`, 6px radius, placeholder "Search or filter (e.g. tag:Auth)...".
- **Log Row:** 20px min-height. 3px left border (device color).
  - Time: `11px`, `#8C8C8C`
  - PID/TID: `11px`, `#8C8C8C`
  - Priority: `12px`, 16x16px pill (e.g., `[E]` in `#E52E2E` text, `#FBEAEE` bg)
  - Tag: `12px`, `#1A1A1A`, 500 weight, fixed 120px width, truncate with ellipsis
  - Message: `12px`, `#1A1A1A`, wrapping text

**States:**
- **Empty:** "No devices connected" centered in right panel. 14px `#8C8C8C`.
- **Loading:** Indeterminate 2px progress bar at top of log view (`#0066FF`).
- **Error:** Red banner below toolbar (`#FBEAEE` bg, `#E52E2E` text), "ADB connection lost".

**Components:**
- **Status Pill:** 20px height, 4px radius, `#EBEBEB` bg, 11px `#1A1A1A` text. E.g., `2 devices`, `bug-repro`.
- **Streaming Indicator:** 6px green dot (`#00A04C`) + "Streaming..." text in 11px `#8C8C8C`.

**Interactions:**
- **Click Log Row:** Expands row to show full stack trace in nested gray `#F7F7F7` block.
- **Hover Log Row:** Background shifts from `#FFFFFF` to `#FAFAFA`.

**Responsive:**
- **Desktop:** Fixed 180px sidebar, flexible log view.
- **Tablet:** N/A (Desktop utility)
- **Mobile:** N/A

### UUID Grouping

**Purpose:** Aggregate scattered logs by correlation IDs (transactions, API requests).

**Layout:** Same sidebar/toolbar structure. Log view is split horizontally: Top 40% UUID list, Bottom 60% associated logs.

**Key Elements:**
- **UUID Table:** Columns: UUID, Device, Start Time, Duration, Log Count. Standard macOS list styling (`#FAFAFA` header).
- **Detail View:** Standard log rows, but constrained to the selected UUID.

**States:**
- **Empty:** "No UUIDs detected in current stream."

### Sessions

**Purpose:** Manage and review saved `.logcat` files.

**Layout:** 3-column macOS Finder style. Sidebar (180px) -> Session List (240px) -> Session Detail/Log View (Fluid).

**Key Elements:**
- **Session List Item:** 48px height. Title (e.g., "Login Crash - Monday"), Date (11px, `#8C8C8C`), Size (11px, `#8C8C8C`).
- **Export Button:** 24px height, `#FFFFFF` bg, `#E5E5E5` border, "Export CSV".

### Filter Builder

**Purpose:** Construct complex regex or multi-parameter queries.

**Layout:** Popover originating from the toolbar search input. 400px width, fluid height.

**Key Elements:**
- **Rule Row:** Dropdown (Tag, Message, PID, Level), Operator (Contains, Equals, Excludes), Input field.
- **Add Rule Button:** 24px height, dashed `#E5E5E5` border, `#8C8C8C` text.
- **Apply Button:** 28px height, `#000000` bg, `#FFFFFF` text, 6px radius.

</details>

---

<details>
<summary>Build Guide</summary>

**Stack:** HTML + Tailwind CSS v3, Electron/Tauri (for native macOS window shell)

**Build Order:**
1. **Log Viewer Skeleton:** Setup Sidebar, Toolbar, and Status bar. Establishes core layout grid and surface colors (`#FFFFFF` vs `#F7F7F7`).
2. **Log Row Component:** Crucial for performance and aesthetics. Build the dense typography layout (Time, PID, Tag, Message) and device striping.
3. **Sub-tab Strip:** Implement the device filtering toggles.
4. **UUID Grouping:** Reuses Log Row component, adds horizontal split pane logic.
5. **Sessions & Filter Builder:** Secondary views and overlays.

</details>