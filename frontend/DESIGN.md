---
name: Kho Mộc Frontend
description: A familiar dark interface for discovering, comparing, and requesting self-storage.
colors:
  primary: "#C2D099"
  primary-strong: "#7DA78C"
  neutral-bg: "#0B1719"
  neutral-surface: "#102326"
  neutral-surface-subtle: "#173532"
  text: "#E6EEC9"
  text-muted: "#C2D099"
  accent: "#35858E"
  border: "rgba(230, 238, 201, 0.16)"
  info: "#8FC1C7"
  success: "#A8CF9A"
  warning: "#E6EEC9"
  danger: "#FF9B73"
typography:
  display:
    fontFamily: "Be Vietnam Pro, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(3.25rem, 7vw, 6rem)"
    fontWeight: 760
    lineHeight: 0.98
    letterSpacing: "-0.055em"
  title:
    fontFamily: "Be Vietnam Pro, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(2.15rem, 4.5vw, 3.7rem)"
    fontWeight: 750
    lineHeight: 1.03
    letterSpacing: "-0.045em"
  body:
    fontFamily: "Be Vietnam Pro, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Be Vietnam Pro, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 750
    lineHeight: 1.35
  mono:
    fontFamily: "IBM Plex Mono, ui-monospace, monospace"
    fontSize: "0.625rem"
    fontWeight: 500
    lineHeight: 1.4
rounded:
  sm: "4px"
  md: "8px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  2xl: "48px"
  3xl: "64px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.neutral-bg}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "12px 18px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.primary-strong}"
    textColor: "{colors.neutral-bg}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "12px 18px"
    height: "44px"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "12px 18px"
    height: "44px"
  surface-card:
    backgroundColor: "{colors.neutral-surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    padding: "24px"
---

# Design System: Kho Mộc Frontend

<!-- This is the current visual baseline. Future changes should append or amend durable decisions here. -->

## Overview

**Creative North Star: “Familiar flow, clear signal.”**

Kho Mộc uses familiar product and booking patterns on a dark, teal-sage surface. The interface should feel calm and direct: a visitor can identify the service, choose a location or storage size, compare the available information, and understand the next action without learning a new interaction model.

The current public landing implementation is intentionally minimal and functional. It uses a concise hero, a finder form, a small proof row, a facility list/detail view, process rows, and one final CTA. The header is part of the reading experience: it begins as a normal navigation bar and becomes a compact floating header after scrolling.

**Key Characteristics:**

- dark-first, cool-natural and high-contrast;
- soft lime and sage as the primary action signal, deep teal as sparse emphasis;
- familiar search, select, list, detail and form patterns;
- short Vietnamese copy and low visual noise;
- tonal depth instead of decorative effects;
- one clear primary action in each decision area.

**The Clear Signal Rule.** Every prominent element must help the user understand, compare or act. If it only fills space, remove it.

**The Prototype Familiarity Rule.** Prefer a pattern a first-time visitor already understands over a novel metaphor or interaction gimmick.

## Colors

The palette translates the [Color Hunt palette](https://colorhunt.co/palette/35858e7da78cc2d099e6eec9) into a dark interface: deep teal, sage, soft lime and pale cream. The three dark neutrals are derived from the same teal-sage family so the theme stays cohesive without turning the page into a flat color swatch.

### Primary

- **Soft lime** (`#C2D099`): primary buttons, active navigation, focus emphasis and selected states.
- **Sage** (`#7DA78C`): hover and stronger primary emphasis; use it with restraint so actions remain distinct.

### Secondary

- **Deep teal** (`#35858E`): sparse editorial emphasis, selected detail labels, focus-adjacent accents and brand punctuation. It is not the primary action color.

### Neutral

- **Teal-black** (`#0B1719`): page background and deepest reading surface.
- **Deep teal surface** (`#102326`): finder panels, cards and primary surfaces.
- **Sage-teal surface** (`#173532`): secondary surface, hover region and CTA band.
- **Pale cream** (`#E6EEC9`): primary text and important data.
- **Soft lime** (`#C2D099`): supporting copy, metadata and secondary labels.
- **Cream translucent border** (`rgba(230, 238, 201, 0.16)`): rules and surface separation.

Status colors always accompany text: info `#8FC1C7`, success `#A8CF9A`, warning `#E6EEC9`, and danger `#FF9B73`.

**The Contrast Before Mood Rule.** Never reduce text contrast to make a dark surface feel more atmospheric.

### Light Theme

Dark is the default theme. The optional light theme keeps the same Color Hunt family but inverts the hierarchy for contrast: pale cream and white surfaces, deep teal text, and deep teal action controls. The selected theme is persisted locally for the current browser.

## Typography

**Display Font:** Be Vietnam Pro (with ui-sans-serif, system-ui fallback)<br />
**Body Font:** Be Vietnam Pro (with ui-sans-serif, system-ui fallback)<br />
**Label/Mono Font:** IBM Plex Mono for compact metadata, indexes, timestamps and technical identifiers.

The pairing is practical and contemporary. Be Vietnam Pro carries Vietnamese reading text; IBM Plex Mono gives small labels a measured, operational rhythm without turning the whole interface into a terminal.

### Hierarchy

- **Display** (760, `clamp(3.25rem, 7vw, 6rem)`, `0.98`): one landing message in the first viewport.
- **Title** (750, `clamp(2.15rem, 4.5vw, 3.7rem)`, `1.03`): section headings and major page titles.
- **Body** (400, `1rem`, `1.55`): explanations and customer-facing copy; keep paragraphs short and readable.
- **Label** (750, `0.8125rem`, `1.35`): controls, buttons and compact headings.
- **Mono** (500, `0.625rem`, `1.4`): indexes, labels, status metadata and small supporting signals.

**The Short Surface Rule.** Text is an aid to the next decision, not a texture layer. Do not stack a kicker, slogan, subtitle and paragraph above one heading.

## Layout

Use a fluid container up to `1200px` with `24px` desktop gutters and `16px` mobile gutters. Keep a 4px/8px spacing rhythm and use generous section separation.

The landing first viewport uses a two-column composition: a concise product message on the left and a familiar finder form on the right. The finder accepts facility and storage-size filters and routes to `/units` without requiring login. Below it, a three-column proof row explains the sequence in plain language.

Facility discovery uses a list/detail composition rather than a carousel. A facility list sits beside the selected facility’s address, receiving hours, note and available unit-type rows. The mobile layout stacks the list above the detail view. The process section uses a text-led list of three steps; it does not need decorative cards or illustrations.

The public header is sticky. At the top it uses the full content width; after the visitor scrolls beyond `24px`, it contracts to a maximum width of `1080px`, reduces its height to `56px`, lowers background opacity and gains a restrained border and shadow. On mobile it remains compact within the viewport width.

The header also contains a compact theme switch. It is available on public and auth surfaces through the shared app shell, uses a sun/moon icon with a visible text label, and changes only presentation—not navigation or product state.

Auth keeps the same palette and controls but gives the form priority. Operations pages may become denser with tables, filters and queues without introducing a second brand language.

## Authenticated Customer Surface

Customer pages use a quiet operations-style shell while preserving the Kho Mộc visual language.

- Use a left sidebar for primary customer navigation.
- Keep navigation labels customer-facing: Kho của tôi, Hóa đơn & thanh toán, Lịch hẹn, Hỗ trợ and Thông báo.
- Mark the active item with a 3px soft-lime left rule, a subtle tinted background and readable text.
- Keep the sidebar visually calm; do not use pill-shaped active tabs.
- Use a compact top bar for account identity, theme control and secondary actions.
- Authenticated pages may use denser spacing and data grouping than public pages.
- Keep one clear primary action per page.
- Customer-facing copy remains Vietnamese-first and task-oriented.

## Elevation & Depth

Depth is primarily tonal: teal-black background, teal-sage surfaces, thin borders and whitespace. Shadows are structural only, used for the finder panel, floating header, dialog and other elements that must read above the page. No gradients, glassmorphism, decorative blur or parallax are part of the baseline. Restrained glow is allowed for notification emphasis when it supports hierarchy and does not reduce text contrast.

Motion is short and functional: approximately `150–220ms` for hover, focus, header compaction and panel transitions. The theme switch uses a `280ms` thumb/icon transition to make the state change legible. Respect `prefers-reduced-motion`; the switch remains usable with movement reduced. The compact header may animate width, background and border treatment; do not animate large page layout regions.

## Shapes

Controls use a restrained `4px` radius. Finder panels, directory surfaces and dialogs use `8px`. Pills are reserved for compact statuses such as “Đang hoạt động”; buttons, fields and navigation are not pill-shaped. The theme switch is the intentional exception because its track must read as a switch.

Use 1px borders for separation. Primary controls are at least `44px` high. Focus rings use the soft lime primary and remain visible against the teal-black field. Native select menus explicitly request dark color-scheme and dark teal surface colors, while remaining aware that the final popup rendering is browser-controlled.

Forms have explicit labels, useful defaults, field-level feedback and one clear submit action. Status and validation never rely on color alone.

## Components

### Navigation

The public header contains the Kho Mộc mark, browse navigation, process/location links and login action. It starts as a quiet dark bar, then becomes a narrower translucent floating bar after scroll. The compact state changes density, not the information architecture.

### Buttons and Links

Primary buttons use soft lime with dark text. Hover uses sage. Secondary buttons use transparent teal surfaces with a visible border. Text links use soft lime and an arrow when they indicate forward navigation. Keep one primary button per decision area.

### Finder Form

The finder is a tonal surface with explicit labels, icon-supported native selects, a full-width primary action and a short no-login note. It should remain useful with either filter empty. Do not add extra fields to the public first viewport without a product requirement.

### Facility Directory

The directory is a list/detail component. Facility options show district and address, with a selected state that changes surface tone and soft-lime index color. The detail panel shows name, active status, address, receiving hours, note and unit-type rows. Unit rows show capacity, size, reference monthly price and a secondary detail action.

### Process and CTA

Process content is a compact three-row sequence with numbered indexes and short explanations. The final CTA is a quiet umber band with one action to start a storage request; it should not become a promotional banner full of claims.

### Status, Selects and Notices

Status is text-first with color as a supporting signal. Native selects use dark color-scheme, dark options and sage checked state where the browser supports it. Loading, empty, error, pending, success and demo/TODO states keep the same layout rhythm and never imply backend persistence that does not exist.

Notifications use the same accent color for their border and surface, with a restrained glow to make important feedback visible. Profile menus use the active theme's surface token rather than browser-default backgrounds. Notification color never replaces its text or context.

## Do's and Don'ts

### Do

- Use soft lime and sage for actions; use deep teal sparingly for emphasis.
- Keep the first viewport focused on choosing a facility or storage size.
- Prefer search, filter, list, detail, table and form patterns that visitors already understand.
- Reuse the same status vocabulary and action hierarchy across public, auth and operations surfaces.
- Keep copy Vietnamese-first, concise and honest about prototype data.
- Preserve keyboard navigation, visible focus and responsive mobile stacking.
- Append durable visual decisions here when the product grows.

### Don't

- Do not let deep teal replace soft lime as the primary action signal.
- Do not add slogans, lore, testimonials, decorative statistics or invented facility claims.
- Do not force a visual metaphor, unusual navigation model or interaction gimmick onto a standard workflow.
- Do not use gradients, glow, glass, abstract hero art or card grids as filler.
- Do not use color alone for status, validation or permission feedback.
- Do not hard-code branch-only API contracts or provisional business rules as settled UI truth.
