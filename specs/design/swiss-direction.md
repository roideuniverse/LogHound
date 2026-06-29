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
> 2. ✅ Bundle Instrument Sans + JetBrains Mono and switch font families ([#32 bundle](https://github.com/roideuniverse/LogHound/pull/32))
> 3. ✅ Badge-pill priority renderer in log rows (visual replacement for colored text)
> 4. ✅ Hover / click-to-expand row states
> 5. ✅ Sub-tab strip layout updates to match Swiss heights / borders
> 6. ✅ Multi-device sub-tabs + status-bar pill ([issue #26](https://github.com/roideuniverse/LogHound/issues/26) — core shipped, follow-ups tracked below)
> 7. ✅ Sessions plugin + status-bar pill ([issue #21](https://github.com/roideuniverse/LogHound/issues/21) — core shipped, archive sub-tabs deferred)
> 8. ✅ Filter Builder popover (v1 — chip-display + AND/OR are follow-ups)
> 9. [ ] Theme system — `LogHoundTheme` data class + four token sets (Light,
>    Darcula, Nord, High Contrast) in `design`, `LocalLogHoundTheme`
>    composition local in `app`, theme switcher wired through the settings
>    panel.
>
> The remaining polish from #21 and #26 (per-device sub-tabs, editable
> device labels in state.json, connection-state pulse, archive sub-tabs,
> rename/export/delete archive actions) lives as follow-up issues; the
> shipped slice covers the headline flow for each.
>
> If a Swiss decision conflicts with shipped behavior, the spec wins —
> file an issue, don't paper over.

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

---

## Claude Design Revision (2026-06-29)

A Claude Design prototype was generated for LogHound on 2026-06-28
(`~/Downloads/LogHound.html`). It extends the Swiss direction with a
**multi-theme system** and a **settings panel**. The Light theme tokens are
identical to what is already in `LogHoundDesign.kt`; the three new dark themes
and the settings panel are net-new.

---

### Theme token tables

#### Light (current — already in `LogHoundDesign.kt`)

| Token | Value |
|---|---|
| `background` | `#FFFFFF` |
| `surface` / `panel` | `#F7F7F7` |
| `elevated` | `#FFFFFF` |
| `border` | `#E5E5E5` |
| `borderSoft` | `#F0F0F0` |
| `rowDivider` | `#F4F4F4` |
| `hover` | `#FAFAFA` |
| `hover2` | `#F2F2F2` |
| `active` / `pressed` | `#EBEBEB` |
| `input` | `#EBEBEB` |
| `onSurface` / `text` | `#1A1A1A` |
| `text2` | `#444444` |
| `secondary` / `dim` | `#8C8C8C` |
| `dim2` | `#A0A0A0` |
| `primary` / `accent` | `#0066FF` |
| `accentTint` | `#E5EFFF` |
| `accentSoft` | `#F2F6FF` |
| `onAccent` | `#FFFFFF` |
| `button` | `#1A1A1A` |
| `buttonText` | `#FFFFFF` |
| `buttonHover` | `#000000` |
| `priorityVerbose` | `#00A04C` |
| `priorityDebug` | `#8C8C8C` |
| `priorityInfo` | `#0066FF` |
| `priorityWarn` | `#FF6B00` |
| `priorityError` | `#E52E2E` |
| `priorityBgVerbose` | `#E6F5EC` |
| `priorityBgDebug` | `#EEEEEE` |
| `priorityBgInfo` | `#E5EFFF` |
| `priorityBgWarn` | `#FFF1E5` |
| `priorityBgError` | `#FBEAEE` |
| `hlBg` | `#FFE08A` |
| `hlFg` | `#1A1A1A` |
| `pulse` | `#00A04C` |
| `scrollbar` | `#D4D4D4` |
| `scrollbarHover` | `#BCBCBC` |

#### Darcula (new)

| Token | Value |
|---|---|
| `background` | `#2B2B2B` |
| `surface` / `panel` | `#3C3F41` |
| `elevated` | `#3C3F41` |
| `border` | `#4A4D4F` |
| `borderSoft` | `#323436` |
| `rowDivider` | `#323436` |
| `hover` | `#353739` |
| `hover2` | `#3C3F41` |
| `active` / `pressed` | `#4E5254` |
| `input` | `#45494A` |
| `onSurface` / `text` | `#C8CDD2` |
| `text2` | `#A9B0B6` |
| `secondary` / `dim` | `#808080` |
| `dim2` | `#6A6A6A` |
| `primary` / `accent` | `#4DA6FF` |
| `accentTint` | `rgba(77,166,255,0.16)` |
| `accentSoft` | `rgba(77,166,255,0.10)` |
| `onAccent` | `#FFFFFF` |
| `button` | `#3574F0` |
| `buttonText` | `#FFFFFF` |
| `buttonHover` | `#3E7BF5` |
| `priorityVerbose` | `#9CA0A6` |
| `priorityDebug` | `#6897BB` |
| `priorityInfo` | `#6A8759` |
| `priorityWarn` | `#BBB529` |
| `priorityError` | `#FF6B68` |
| `priorityBgVerbose` | `rgba(156,160,166,0.16)` |
| `priorityBgDebug` | `rgba(104,151,187,0.18)` |
| `priorityBgInfo` | `rgba(106,135,89,0.20)` |
| `priorityBgWarn` | `rgba(187,181,41,0.18)` |
| `priorityBgError` | `rgba(255,107,104,0.18)` |
| `hlBg` | `rgba(187,181,41,0.34)` |
| `hlFg` | `#FFE9A8` |
| `pulse` | `#62C56E` |
| `scrollbar` | `#5A5A5A` |
| `scrollbarHover` | `#6E6E6E` |

#### Nord (new)

| Token | Value |
|---|---|
| `background` | `#2E3440` |
| `surface` / `panel` | `#3B4252` |
| `elevated` | `#3B4252` |
| `border` | `#434C5E` |
| `borderSoft` | `#3B4252` |
| `rowDivider` | `#3B4252` |
| `hover` | `#39404E` |
| `hover2` | `#3B4252` |
| `active` / `pressed` | `#434C5E` |
| `input` | `#434C5E` |
| `onSurface` / `text` | `#ECEFF4` |
| `text2` | `#D8DEE9` |
| `secondary` / `dim` | `#828B9C` |
| `dim2` | `#667084` |
| `primary` / `accent` | `#88C0D0` |
| `accentTint` | `rgba(136,192,208,0.16)` |
| `accentSoft` | `rgba(136,192,208,0.10)` |
| `onAccent` | `#2E3440` |
| `button` | `#5E81AC` |
| `buttonText` | `#ECEFF4` |
| `buttonHover` | `#6B8FBC` |
| `priorityVerbose` | `#9AA5B5` |
| `priorityDebug` | `#81A1C1` |
| `priorityInfo` | `#A3BE8C` |
| `priorityWarn` | `#EBCB8B` |
| `priorityError` | `#BF616A` |
| `priorityBgVerbose` | `rgba(154,165,181,0.16)` |
| `priorityBgDebug` | `rgba(129,161,193,0.20)` |
| `priorityBgInfo` | `rgba(163,190,140,0.20)` |
| `priorityBgWarn` | `rgba(235,203,139,0.18)` |
| `priorityBgError` | `rgba(191,97,106,0.20)` |
| `hlBg` | `rgba(235,203,139,0.30)` |
| `hlFg` | `#ECEFF4` |
| `pulse` | `#A3BE8C` |
| `scrollbar` | `#4C566A` |
| `scrollbarHover` | `#5C6680` |

#### High Contrast (new)

| Token | Value |
|---|---|
| `background` | `#000000` |
| `surface` / `panel` | `#101012` |
| `elevated` | `#101012` |
| `border` | `#6F6F6F` |
| `borderSoft` | `#3A3A3A` |
| `rowDivider` | `#2A2A2A` |
| `hover` | `#1A1A1A` |
| `hover2` | `#1F1F1F` |
| `active` / `pressed` | `#173052` |
| `input` | `#1A1A1A` |
| `onSurface` / `text` | `#FFFFFF` |
| `text2` | `#E4E4E4` |
| `secondary` / `dim` | `#B0B0B0` |
| `dim2` | `#909090` |
| `primary` / `accent` | `#3FF5FF` |
| `accentTint` | `rgba(63,245,255,0.18)` |
| `accentSoft` | `rgba(63,245,255,0.10)` |
| `onAccent` | `#000000` |
| `button` | `#3FF5FF` |
| `buttonText` | `#000000` |
| `buttonHover` | `#6AF8FF` |
| `priorityVerbose` | `#FFFFFF` |
| `priorityDebug` | `#5BC0FF` |
| `priorityInfo` | `#5BFF8F` |
| `priorityWarn` | `#FFE34D` |
| `priorityError` | `#FF5C5C` |
| `priorityBgVerbose` | `rgba(255,255,255,0.14)` |
| `priorityBgDebug` | `rgba(91,192,255,0.20)` |
| `priorityBgInfo` | `rgba(91,255,143,0.18)` |
| `priorityBgWarn` | `rgba(255,227,77,0.18)` |
| `priorityBgError` | `rgba(255,92,92,0.20)` |
| `hlBg` | `rgba(255,227,77,0.32)` |
| `hlFg` | `#FFFFFF` |
| `pulse` | `#5BFF8F` |
| `scrollbar` | `#5A5A5A` |
| `scrollbarHover` | `#7A7A7A` |

---

### New semantic tokens (not yet in `LogHoundDesign.kt`)

These tokens appear in the prototype but are absent from the current implementation. They must be added when step 9 lands.

| Token | Purpose |
|---|---|
| `elevated` | Popover / modal surface — separate from `surface` so popovers can float above the panel background |
| `borderSoft` | Lighter divider used between toolbar and content areas |
| `rowDivider` | Row-to-row separator, lighter than `border` |
| `text2` | Secondary body text darker than `secondary` — sub-labels, metadata counts |
| `dim2` | A second muted level for de-emphasised chrome below `secondary` |
| `accentTint` | Accent at ~16% opacity — selected row / autocomplete hover backgrounds |
| `accentSoft` | Accent at ~10% opacity — softer selected-item highlight |
| `onAccent` | Foreground color on an accent-filled surface (white in Light/Darcula, dark in Nord/HC) |
| `button` | Primary button fill |
| `buttonText` | Label on a primary button |
| `buttonHover` | Primary button fill on hover |
| `hlBg` | Search-match highlight background |
| `hlFg` | Search-match highlight foreground |
| `pulse` | Live-streaming indicator dot (animated ring) |
| `scrollbar` | Scrollbar thumb |
| `scrollbarHover` | Scrollbar thumb on hover |

---

### Settings panel spec

The settings panel is part of the window shell (`app`), not a plugin. It opens
via a gear icon at the right end of the title bar and closes by clicking outside
or pressing Escape.

**Layout:** a narrow left column of category labels; a right content area that
swaps based on the selected category. Initial categories: **Appearance**,
**Display**, **Import / Export**.

#### Appearance

| Setting | Control | Options | Default |
|---|---|---|---|
| Theme | Segmented control | Light · Darcula · Nord · High Contrast | Light |
| Accent color | Swatch picker | per-theme default or user hex override | (theme default) |

#### Display

| Setting | Control | Options | Default |
|---|---|---|---|
| Density | Segmented control | Compact · Comfortable | Compact |
| Font size | Stepper (px) | 10 – 18 | 12 |
| Show PID/TID | Toggle | on / off | on |
| Zebra rows | Toggle | on / off | off |
| Word wrap | Toggle | on / off | on |
| Timestamp format | Segmented control | Full · Short · Seconds | Full |

**Density detail:** Compact = 3 px vertical padding per row; Comfortable = 7 px.

#### Import / Export

A raw JSON editor showing all current settings. Actions: **Copy JSON**,
**Download** (`loghound-settings.json`), **Upload / paste** (applies recognized
keys, ignores unknowns), **Reset to defaults**.

**Persistence:** settings are written to `~/.loghound/config.properties`
alongside the existing font overrides. Unknown keys are preserved on write so
settings files round-trip safely across versions.