# Uptrail Workbench Design System

Visual and interaction rules for the Uptrail user interface (server-rendered Thymeleaf pages, Bootstrap 5, one local stylesheet, small vanilla JavaScript modules).

Provenance: adapted from a publicly available description of an enterprise back-office design language. Vendor names, proprietary typefaces, product URLs, marketing copy and framework-specific component APIs were removed; tokens were renamed, some values were changed for accessibility, and every rule was rewritten for this application's stack and screens.

Implementation file: `src/main/resources/static/css/uptrail.css`. Tokens are CSS custom properties on `:root`; Bootstrap variables are mapped onto them so Bootstrap components follow the same palette.

---

## 1. Principles

1. **Work surface, not a showcase.** Every screen exists to review, decide, record or reconcile. No hero sections, no decorative gradients, no illustrations, no animation beyond short state transitions.
2. **One interactive colour.** Primary blue marks primary actions, links, focus and the active navigation item. Everything else is neutral.
3. **Status is a contract.** Four semantic states (positive, critical, negative, information) plus neutral. Each business status maps to exactly one state (section 6). Status is always written as text; colour only reinforces it.
4. **Density for desks, space for touch.** Compact controls and table rows on desktop; larger targets on narrow screens.
5. **Numbers are data.** Amounts and day counts use tabular numerals, right alignment, an explicit currency (`SGD`) and one decimal format.
6. **Explain, do not hide.** Unavailable actions are either absent because they never apply, or shown disabled with the reason next to them.

---

## 2. Colour tokens

| Token | Value | Use |
| --- | --- | --- |
| `--ut-primary` | `#0b62c4` | Primary buttons, links, focus ring, active tab/nav marker |
| `--ut-primary-hover` | `#094f9e` | Hover on primary fills and links |
| `--ut-primary-active` | `#073c78` | Pressed state |
| `--ut-ink` | `#1d2d3e` | Body text, titles (never pure black) |
| `--ut-ink-muted` | `#556b82` | Labels, table headers, metadata, helper text |
| `--ut-ink-quiet` | `#7a8b9e` | Disabled text only |
| `--ut-canvas` | `#f5f6f7` | Page background |
| `--ut-surface` | `#ffffff` | Cards, tables, forms, dialogs |
| `--ut-surface-soft` | `#eef1f4` | Table header row, secondary toolbars, read-only fields |
| `--ut-surface-hover` | `#f5f8fa` | Row hover |
| `--ut-hairline` | `#d9e0e6` | Card and table borders |
| `--ut-hairline-soft` | `#eef1f4` | Row separators |
| `--ut-hairline-strong` | `#a9b4c0` | Input borders |
| `--ut-scrim` | `rgba(29, 45, 62, 0.45)` | Modal backdrop |

### Semantic states

| State | Text / icon | Background | Border | Meaning in Uptrail |
| --- | --- | --- | --- | --- |
| Positive | `#107e3e` | `#eef8f1` | `#9ad4af` | Finished successfully: completed course, reimbursed claim, confirmed calendar, sent email |
| Critical | `#9a4a00` (text), `#df6e0c` (accent) | `#fff4e6` | `#f8cb9c` | Waiting for someone: pending application or claim, draft calendar year, retrying email |
| Negative | `#b00000` | `#ffeef0` | `#f399a2` | Refused or failed: rejected, validation error, failed email, insufficient balance |
| Information | `#0b62c4` | `#edf4fd` | `#a1cbfa` | Scheduled or informative: approved course, approved claim, notices |
| Neutral | `#1d2d3e` | `#eef1f4` | `#d9e0e6` | Closed without outcome: deleted, cancelled, inactive |

The critical text colour is darker than the accent so badge text meets WCAG AA contrast on its background.

---

## 3. Typography

System fonts only; nothing is downloaded at runtime.

```css
--ut-font: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif;
--ut-font-mono: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
```

| Role | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| Page title | 24px / 30px | 700 | One per page, in the page header |
| Section title | 18px / 24px | 700 | Card and section headers |
| Subsection | 16px / 22px | 600 | Group titles inside a card |
| Body | 14px / 20px | 400 | Default for tables, forms, text |
| Body strong | 14px / 20px | 600 | Key values, current balance |
| Small | 12px / 16px | 400 | Helper text, timestamps, badge text |
| Label | 12px / 16px | 600 | Form labels, table column headers (no uppercase transform) |
| Mono | 13px / 18px | 400 | Reference numbers (`CATS-2026-000042`), `SIM-` references, correlation ids |

Numeric cells and amounts use `font-variant-numeric: tabular-nums`.

---

## 4. Spacing, shape, elevation

Spacing scale (8px base): `2, 4, 8, 12, 16, 24, 32, 48` px as `--ut-space-*`.

| Radius token | Value | Use |
| --- | --- | --- |
| `--ut-radius-xs` | 4px | Inputs, message strips |
| `--ut-radius-sm` | 6px | Buttons, dropdown triggers |
| `--ut-radius-md` | 8px | Cards, table wrappers, panels |
| `--ut-radius-lg` | 12px | Dialogs |
| `--ut-radius-pill` | 16px | Status badges, filter chips |

Elevation:

- Level 0: border only (`1px solid var(--ut-hairline)`), used for tables inside cards.
- Level 1: `0 1px 4px rgba(29, 45, 62, 0.08)`, cards and panels.
- Level 2: `0 4px 16px rgba(29, 45, 62, 0.12)`, dropdowns and popovers.
- Level 3: `0 12px 32px rgba(29, 45, 62, 0.20)`, dialogs with the scrim.

---

## 5. Layout

### 5.1 Shell

- **Top bar**, 48px, white with a bottom hairline: product name "Uptrail", workspace label (Staff workspace / Administration), current user name and roles, sign-out button (POST form).
- **Side navigation**, 232px, white, left hairline marker in primary blue on the active item. Groups: *My training* (Dashboard, My Applications, Apply for a Course, My Claims, My Entitlement), *Team* (Approvals, Team History, Reports; managers only), *Shared* (Training Calendar). The administration workspace has its own group (Dashboard, Staff, Approval Routing, Entitlements, Catalogue, Public Holidays, Reimbursements, Operations, Training Calendar).
- **Content area** on the canvas colour, max width 1280px, 24px padding (16px below 768px).
- Below 992px the side navigation collapses behind a menu button in the top bar.

### 5.2 Page patterns

| Pattern | Structure | Used by |
| --- | --- | --- |
| List report | Page header (title, primary action) → filter bar card → table card with pagination footer | My Applications, Team History, Staff, Reimbursements, Operations |
| Object page | Header (reference in mono, title, status badge, key facts row, actions) → two-column body: details and schedule (left, 8/12), timeline and balances (right, 4/12) | Application detail, claim detail |
| Review page | Object page plus a sticky decision panel at the bottom of the left column | Manager application and claim review |
| Form with live check | Form (8/12) with three titled sections; sticky side panel (4/12) showing the eligibility check result | Apply / edit application |
| Grouped worklist | Tabs (Applications / Fee claims) → one card per subordinate with a compact table; pagination by subordinate | Manager approvals |
| Configuration page | Tabs for sibling entities in one shell; table plus inline "add" form | Catalogue, Public Holidays |
| Calendar | Toolbar (previous, month select, next, category filter, view toggle) → month grid or list | Training Calendar |

---

## 6. Status mapping

Status badges are pills with text, never colour alone.

| Domain | Status | State |
| --- | --- | --- |
| Course application | APPLIED, UPDATED | Critical (awaiting manager) |
| | APPROVED | Information (scheduled) |
| | COMPLETED | Positive |
| | REJECTED | Negative |
| | DELETED, CANCELLED | Neutral |
| Fee claim | SUBMITTED | Critical |
| | APPROVED | Information |
| | REIMBURSED | Positive, with the note "Recorded; no payment initiated" |
| | REJECTED | Negative |
| Calendar year | DRAFT | Critical |
| | CONFIRMED | Positive |
| | Not configured | Neutral |
| Email outbox | PENDING, SENDING | Critical |
| | SENT | Positive |
| | FAILED | Negative |
| Employee | Active | Positive |
| | Inactive | Neutral |

Badge labels use sentence case of the status name (for example "Applied", "Reimbursed").

---

## 7. Components

### Buttons

| Variant | Look | Use |
| --- | --- | --- |
| Primary | Blue fill, white text, 6px radius | The single main action of a page or panel |
| Secondary | White fill, strong hairline border, blue text | Other actions |
| Ghost | Transparent, blue text | Low-emphasis actions inside tables |
| Danger | Negative red fill, white text | Destructive confirmations only (Delete, Cancel course, Reject) inside a confirmation dialog |

Height 34px on desktop, 44px below 768px. Focus ring: 2px solid primary with 2px offset. State-changing buttons are always inside POST forms.

### Tables

- Header row: soft surface, label typography, 36px height.
- Body rows: 40px, row separators in soft hairline, hover in surface-hover.
- Amount and day columns right-aligned with tabular numerals; currency shown in the header ("Fee (SGD)").
- The first column (reference or course title) is the detail link.
- Empty table: one row spanning all columns with a one-line explanation and, where useful, a next action ("No applications this year. Apply for a course.").

### Forms

- Label above field; required fields marked with "Required" in the label text, not only with an asterisk.
- Input: 34px, 4px radius, strong hairline border; invalid input has negative border and background plus the message below the field.
- A form-level message strip summarises errors at the top after a failed submission; field input is preserved.
- Help text in small muted type under the field (for example the half-day rule).

### Message strips

Full-width strip with a 4px left border in the state colour, state background, ink text. Used for flash messages after actions, business-rule errors, and notices such as "Registration only. Uptrail does not transfer money."

### Cards and panels

White surface, hairline border, 8px radius, level 1 shadow, 16px padding, header with section title and optional actions on the right.

### Key facts row

Horizontal list of label/value pairs under an object page title (period, training days, fee, approver, submitted). Label in small muted, value in body strong.

### Balance panel

Rows: Entitlement, Pending reservations, Approved commitments, Available, and separately Completed (statistic) and Reimbursed (claims). Available is emphasised; a negative or insufficient result is shown with the negative state and the exact numbers.

### Timeline

Vertical list of audit events: time (Asia/Singapore), actor, event, status change, reason. Newest first.

### Dialogs

Used only for confirmations of destructive or irreversible actions and for mandatory reason entry. Title states the action, body states the consequence, primary button repeats the verb ("Cancel course").

### Pagination

Footer of the table card: "Showing 11–20 of 57", page size select (10, 20, 25), previous/next and page numbers. Filters are preserved in every link.

---

## 8. Page states

Every page defines these states explicitly:

| State | Presentation |
| --- | --- |
| Normal | Data from the database |
| Empty | Explanatory row or card with the next useful action |
| Validation failed | Form-level strip plus field messages, input preserved |
| Load failed (API) | Inline strip "Unable to check now. You can still submit; the server validates again." Never a fake success |
| Forbidden | 403 page naming the required workspace or role and how to switch |
| Not found | 404 page without revealing whether a record exists |
| Stale version | 409 strip "This record changed since you opened it. Review the current version and try again." Typed text preserved |

---

## 9. Accessibility and responsiveness

- WCAG 2.1 AA contrast for text and badges; focus visible on every interactive element.
- All actions reachable by keyboard; dialogs trap focus and close with Escape.
- Tables scroll horizontally inside their card on narrow screens; forms become single-column below 992px; the side panel moves under the form.
- Primary demo resolution 1366×768; forms usable at 375px width.

---

## 10. Do and don't

Do:

- Use one primary button per page or panel.
- Show currency and units with every number (`SGD 600.00`, `2.5 days`).
- Keep course terminology and status names from the course brief.
- Print reports with the print stylesheet (navigation hidden, tables full width).

Don't:

- Use gradients, large display type, pill-shaped buttons or illustrations.
- Invent new status colours or show status by colour alone.
- Load fonts, icons or scripts from a CDN.
- Put state-changing actions behind links (GET).
- Display placeholder numbers, fake metrics or unimplemented buttons.
