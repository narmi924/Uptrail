# Uptrail Design System

Visual and interaction rules for the Uptrail user interface: server-rendered Thymeleaf pages, Bootstrap 5 as the component base, one local stylesheet and small vanilla JavaScript files.

Uptrail should feel like a modern product, not a form generator: calm surfaces, confident typography, one clear accent colour and data that reads at a glance. It is still a work tool, so density, clarity and keyboard use matter more than decoration.

Provenance: inspired by publicly available descriptions of modern product design languages (money-transfer and payments products, documentation sites and editorial productivity tools). No vendor names, proprietary typefaces, logos or assets are used; every rule is written for this application.

Implementation: `src/main/resources/static/css/uptrail.css` (tokens are CSS custom properties on `:root`; Bootstrap variables are mapped onto them), icons in `static/img/icons.svg`, the Inter font in `static/fonts/`.

---

## 1. Principles

1. **Calm surfaces, clear hierarchy.** A warm paper-white canvas, white cards with whisper-thin borders and soft, warm shadows. Hierarchy comes from type weight and spacing, not from boxes inside boxes.
2. **One accent.** Trail Pine marks what is interactive or selected. Status colours are reserved for status; category colours only for categories.
3. **Numbers are content.** Days and money use tabular figures, always with their unit (`SGD 600.00`, `2.5 days`). Balances show a progress meter so the remaining budget is visible without reading.
4. **Never colour alone.** Every status badge has a text label and a dot; every icon button has a label or `aria-label`.
5. **Fast and self-contained.** No CDN, no web fonts from third parties, no inline scripts or styles (the Content-Security-Policy blocks them). Pages work at 390 px width and in print.

---

## 2. Colour

The theme takes its colours from the name: a trail through pine forest, with a lime marker at the summit. Neutrals are warm (paper and stone with a faint green undertone), never cool blue-grey. Blue and violet are not used as theme colours.

### Neutrals

| Token | Value | Use |
| --- | --- | --- |
| `--ut-ink` | `#1a1f1c` | Headings, primary text, table values (a warm near-black with a green undertone) |
| `--ut-ink-2` | `#3b423e` | Body text, labels |
| `--ut-ink-3` | `#646b66` | Secondary text, captions, table headings |
| `--ut-ink-4` | `#9aa09b` | Placeholders, disabled text, quiet icons |
| `--ut-canvas` | `#f5f4ef` | Page background (warm paper) |
| `--ut-surface` | `#ffffff` | Cards, inputs, sidebar |
| `--ut-surface-2` | `#faf9f6` | Table headers, subtle fills, read-only fields, weekends |
| `--ut-surface-3` | `#efeee8` | Hover fills, chips, meter tracks |
| `--ut-border` | `#e5e3db` | Card and table borders (whisper weight) |
| `--ut-border-strong` | `#d1cec4` | Inputs, secondary buttons |

### Accent (Trail Pine)

| Token | Value | Use |
| --- | --- | --- |
| `--ut-primary` | `#1f6b4f` | Primary buttons, links, active navigation, focus |
| `--ut-primary-hover` | `#195a42` | Hover |
| `--ut-primary-active` | `#134834` | Pressed |
| `--ut-primary-soft` | `#e9f3ec` | Active navigation item, selected chips, soft buttons |
| `--ut-primary-soft-border` | `#b9d9c6` | Borders of soft elements |
| `--ut-primary-on-soft` | `#175b42` | Text and icons on soft backgrounds |
| `--ut-lime` | `#b8ee84` | Brand highlight: the trail in the brand mark, icons and glow on the sign-in panel, text selection |
| `--ut-night` | `#0d2119` | Forest Night: background of the sign-in panel |

The brand mark is a pine tile (gradient `#2a8a63 → #123f2e`) with a lime trail. The sign-in panel is Forest Night with a soft pine glow and a faint lime glow. Lime never carries text on a light background.

### Status

| Role | Text | Background | Border | Dot | Used for |
| --- | --- | --- | --- | --- | --- |
| Success (leaf) | `#3f6212` | `#f3f9e9` | `#cfe6ae` | `#65a30d` | Completed, reimbursed, confirmed, sent, active |
| Warning (amber) | `#b54708` | `#fffaeb` | `#fedf89` | `#f79009` | Awaiting a decision: applied, updated, submitted, draft, pending |
| Danger (red) | `#b42318` | `#fef3f2` | `#fecdca` | `#f04438` | Rejected, failed, validation errors, destructive actions |
| Info (lagoon) | `#0e6874` | `#eaf6f7` | `#aedde1` | `#1aa2b1` | Approved and scheduled, notices |
| Neutral (stone) | `#57534e` | `#f3f2ee` | `#e3e1da` | `#a8a29e` | Deleted, cancelled, inactive, not configured |

Success is a yellow-green leaf so that it reads differently from the pine accent.

### Categories

| Category | Colour | Soft background |
| --- | --- | --- |
| Internal training | Moss `#4d7c0f` | `#f4f9e8` |
| External course | Lagoon `#0e7c86` | `#ebf7f7` |
| Professional certification | Clay `#b4532a` | `#fdf1ea` |

Contrast: body text and badge text reach at least 4.5:1 on their backgrounds (secondary text on the canvas 5.0:1); white on `--ut-primary` is 6.4:1.

---

## 3. Typography

Font: **Inter** (variable, self-hosted, SIL Open Font License), fallback `system-ui`. Features: `cv11` (single-storey a) everywhere; `tnum` (tabular figures) for every number in tables, stats, balances and money. Reference numbers stay in Inter with tabular figures and a slashed zero; a monospace font (`ui-monospace`, `SFMono-Regular`, `Consolas`) is used only for raw JSON in the audit trail.

| Role | Size / line height | Weight | Tracking | Use |
| --- | --- | --- | --- | --- |
| Display | 34 / 40 | 650 | -0.025em | Sign-in panel headline |
| Page title | 26 / 32 | 650 | -0.02em | One `h1` per page |
| Section title | 16 / 24 | 600 | -0.01em | Card and section headings |
| Stat value | 28 / 34 | 650 | -0.02em, `tnum` | Dashboard numbers |
| Body | 14 / 22 | 400 | 0 | Default text |
| Body strong | 14 / 22 | 550 | 0 | Table primary column, names |
| Small | 13 / 20 | 400–500 | 0 | Buttons (small), helper text, pager |
| Caption | 12 / 18 | 500 | 0 | Labels, meta lines, table headings |
| Overline | 11 / 16 | 600 | 0.06em, uppercase | Sidebar group labels, stat labels |
| Reference | 13 / 20 | 500 | 0, `tnum`, slashed zero | Reference numbers, staff numbers, correlation ids |

Rules: headings in `--ut-ink`, body in `--ut-ink-2`, secondary text in `--ut-ink-3`. Never use weights above 700. Keep line length under 80 characters for prose.

---

## 4. Layout and spacing

- Spacing grid of 4 px: 4, 8, 12, 16, 20, 24, 32, 40, 48.
- **App shell:** a 256 px white sidebar on the left (brand, workspace label, grouped navigation with icons, the signed-in person at the bottom) and the content area on the canvas. Below 992 px the sidebar becomes a drawer opened from a slim top bar.
- **Content:** maximum width 1240 px, padding 32 px (24 px below 1200 px, 16 px below 768 px). Gaps between cards 16–24 px.
- **Grids:** stat cards in an auto-fit grid (minimum 220 px); detail pages use 8/4 columns (content / side panel) from 992 px up.
- Tables scroll inside their card on narrow screens; the page itself never scrolls sideways.

---

## 5. Shape, elevation and motion

| Token | Value | Use |
| --- | --- | --- |
| `--ut-radius-sm` | 6 px | Inputs, small buttons, calendar events |
| `--ut-radius-md` | 8 px | Buttons, dropdowns, navigation items |
| `--ut-radius-lg` | 12 px | Cards, tables, panels |
| `--ut-radius-xl` | 16 px | Sign-in card, empty-state illustrations |
| `--ut-radius-pill` | 999 px | Badges, chips, avatars, meters |

Shadows are layered and warm (with a hint of forest green in the larger ones) so elevation feels part of the palette:

| Token | Value | Use |
| --- | --- | --- |
| `--ut-shadow-xs` | `0 1px 2px rgba(28,25,23,.05)` | Buttons, inputs |
| `--ut-shadow-sm` | `0 1px 3px rgba(28,25,23,.07), 0 1px 2px rgba(28,25,23,.04)` | Cards at rest |
| `--ut-shadow-md` | `0 12px 24px -10px rgba(26,46,36,.20), 0 4px 10px -6px rgba(0,0,0,.08)` | Hovered clickable cards, sticky panels |
| `--ut-shadow-lg` | `0 30px 60px -24px rgba(26,46,36,.32), 0 18px 36px -18px rgba(0,0,0,.16)` | Popovers, dialogs, sign-in card |

Focus ring: `0 0 0 4px rgba(31,107,79,.20)` plus the pine border on inputs; a 2 px pine outline on other focusable elements.

Motion: 150 ms `cubic-bezier(.2,.8,.2,1)` for hover, press and panel changes; clickable cards lift by 1 px. Everything is disabled under `prefers-reduced-motion`.

---

## 6. Iconography

Line icons from one local sprite (`/img/icons.svg`, ids `i-<name>`), drawn with `currentColor`, stroke 2, sizes 16 (inline, buttons), 18 (navigation) and 20 (stat and empty-state icons). An icon always sits next to a text label; an icon-only button carries an `aria-label`. Use the same icon for the same concept everywhere (for example `receipt` for claims, `wallet` for entitlement, `calendar-days` for holidays).

---

## 7. Components

**Sidebar navigation.** Brand mark (pine tile with a lime trail) and name, then the workspace label ("Staff workspace" or "Administration") as an overline. Items: 18 px icon + label, 36 px high, 8 px radius. Active item: `--ut-primary-soft` background, `--ut-primary-on-soft` text and icon, weight 600. Counts (for example waiting approvals) sit right-aligned in a small pill. The footer shows an initials avatar, name, roles and a sign-out button.

**Page header.** Optional back link (small, with an arrow), the page title, a one-line subtitle in `--ut-ink-3`, and actions on the right. Detail pages add a key-facts row under the title.

**Cards.** White, 1 px `--ut-border`, 12 px radius, `--ut-shadow-sm`. Header: 16 px section title with an optional icon and actions on the right, separated from the body by a hairline. Clickable cards lift on hover.

**Stat cards.** Icon in a 36 px tinted circle, overline label, stat value, a note line and, for balances, a meter (8 px pill track in `--ut-surface-3`, pine fill; warning fill above 85 %, danger fill when over).

**Tables.** Inside a card; header row in `--ut-surface-2` with caption-style headings; rows at least 48 px high with hairline separators and a soft hover. The first column (reference or name) is the link to the detail. Numbers right-aligned with `tnum`. Empty table: one row with an icon and a sentence that says what to do next.

**Badges.** Pill, 12 px weight 550, a 6 px coloured dot before the label, soft background and border from the status table. Never only a coloured dot.

**Buttons.** Primary: pine fill, white text, 8 px radius, 38 px high (34 px small). Secondary: white with `--ut-border-strong` border and `--ut-shadow-xs`, ink text. Soft: `--ut-primary-soft` fill with pine text for secondary calls to action. Ghost: text only. Danger: outlined red, filled red only inside a confirmation. One primary button per panel.

**Links.** Pine text. Links inside running text and tables carry a soft pine underline (30 % opacity) that turns solid on hover, so they stand apart from dark body text; navigation, tabs and buttons have no underline.

**Forms.** Labels 13 px weight 550 in `--ut-ink-2` above fields; inputs 40 px high, 8 px radius, white, `--ut-border-strong`; focus ring as above. Help text 12 px under the field; errors in danger text with the field border in danger. Required fields say "(required)" in the label.

**Filters and tabs.** Filter bars sit in a card body as a row of fields with the submit button at the end. Tabs are underlined: 2 px pine underline for the active tab, counts in a small pill.

**Messages.** Flash messages and strips are rounded panels with an icon, a soft status background and a 1 px status border; the title is weight 600.

**Timeline.** A vertical hairline with 10 px dots; each entry shows the event, who and when, and the reason in a soft quote block.

**Calendar.** A rounded month grid; day numbers top-left, today in a pine circle, weekends and other months on `--ut-surface-2`. Events are compact chips with the category's soft background and a category-coloured left edge; holidays appear as small red captions.

**Sign-in.** Split screen: on the left a Forest Night panel with the brand mark, a short headline and three feature lines with icons; on the right the sign-in card (16 px radius, `--ut-shadow-lg`). The public demo lists its accounts as selectable rows under the form. On narrow screens only the card is shown.

**Empty, loading and error states.** Empty: a 40 px icon in a soft circle, one sentence and, where useful, a button. Loading (calendar, eligibility check): a quiet text line, never a spinner alone. Error pages use the sign-in layout with the status code, a title, the message and the correlation id.

---

## 8. Status mapping

| Record | States | Style |
| --- | --- | --- |
| Course application | APPLIED, UPDATED | Warning (awaiting the manager) |
| | APPROVED | Info (scheduled) |
| | COMPLETED | Success |
| | REJECTED | Danger |
| | DELETED, CANCELLED | Neutral |
| Fee claim | SUBMITTED | Warning |
| | APPROVED | Info |
| | REIMBURSED | Success |
| | REJECTED | Danger |
| Calendar year | DRAFT / CONFIRMED | Warning / Success |
| Email | PENDING, SENDING / SENT / FAILED | Warning / Success / Danger |

---

## 9. Accessibility and responsiveness

- Text contrast at least 4.5:1; focus always visible; the skip link goes to the main content.
- Touch targets at least 44 px below 768 px.
- Breakpoints: 1200 px (content padding), 992 px (sidebar becomes a drawer, side panels stack), 768 px (single column, full-width buttons in forms).
- Print: navigation, filters and buttons hidden; cards lose their shadows; tables use the full width.

---

## 10. Do and don't

Do:

- Use one primary button per panel and put secondary actions next to it as secondary or ghost buttons.
- Show units with every number and use tabular figures.
- Pair every icon with text, and every status colour with a label.
- Use the same course terms and status names on every page.

Don't:

- Load fonts, icons or scripts from a CDN, or add inline scripts or styles.
- Use gradients outside the brand mark and the sign-in panel.
- Use blue or violet as a theme colour, or cool blue-grey neutrals.
- Invent new status colours or show status by colour alone.
- Put state-changing actions behind links (`GET`).
- Show placeholder numbers, fake metrics or buttons without a function.
