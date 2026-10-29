---
name: Anticipation Studio
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f3'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#e9edff'
  surface-container-high: '#e1e8ff'
  surface-container-highest: '#dae2fc'
  on-surface: '#131b2e'
  on-surface-variant: '#444653'
  inverse-surface: '#283044'
  inverse-on-surface: '#edf0ff'
  outline: '#747685'
  outline-variant: '#c4c5d5'
  surface-tint: '#3256c4'
  primary: '#284dbc'
  on-primary: '#ffffff'
  primary-container: '#4567d6'
  on-primary-container: '#f2f2ff'
  inverse-primary: '#b6c4ff'
  secondary: '#3255c6'
  on-secondary: '#ffffff'
  secondary-container: '#6c8cff'
  on-secondary-container: '#002376'
  tertiary: '#465485'
  on-tertiary: '#ffffff'
  tertiary-container: '#5e6d9f'
  on-tertiary-container: '#f2f2ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dce1ff'
  primary-fixed-dim: '#b6c4ff'
  on-primary-fixed: '#001550'
  on-primary-fixed-variant: '#0d3cab'
  secondary-fixed: '#dce1ff'
  secondary-fixed-dim: '#b6c4ff'
  on-secondary-fixed: '#001550'
  on-secondary-fixed-variant: '#0d3bad'
  tertiary-fixed: '#dbe1ff'
  tertiary-fixed-dim: '#b6c5fc'
  on-tertiary-fixed: '#051846'
  on-tertiary-fixed-variant: '#354574'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fc'
typography:
  display-hero:
    fontFamily: Outfit
    fontSize: 56px
    fontWeight: '600'
    lineHeight: 64px
    letterSpacing: -0.03em
  display-hero-mobile:
    fontFamily: Outfit
    fontSize: 44px
    fontWeight: '600'
    lineHeight: 50px
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Outfit
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Outfit
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 30px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Outfit
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 26px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Manrope
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Manrope
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
  label-lg:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-numeral:
    fontFamily: Outfit
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers a calm, poised countdown experience built around emotional resonance rather than high-urgency friction. It honors milestones—trips, launches, reunions, personal goals—by making the passage of time feel deliberate, celebrated, and serene.

The style merges minimalist clarity with tactile, rounded material surfaces. It rejects aggressive alarms, gamified progress bars, and loud gradients in favor of sculpted typography, airy negative space, and luminous pastel-blue foundations. The interface acts as a quiet, polished gallery frame around shared life events.

## Colors

The palette establishes an open, ethereal atmosphere using cool blue values balanced against deep navy inks.

- **Primary Interactive (`#4567D6`):** Focused deep periwinkle used for key tappable glyphs, text links, selected navigation states, and the central elevation trigger.
- **Brand Soft Blue (`#6C8CFF`):** Hero filled state for primary action buttons, carrying dark high-contrast label typography (`#182033`) rather than white text.
- **Brand Pale Blue (`#B8C7FF`):** Gentle highlight tint reserved for progress tracks, secondary chip fills, and subtle border definitions.
- **Background (`#F7F9FF`):** Light, tinted canvas ensuring cards and surfaces lift naturally without harsh edge contrast.
- **Surface (`#FFFFFF`):** Base white for modular event cards, sheets, and dialogs.
- **Surface Subtle (`#EEF3FF`):** Structural inset fill used for compact metric badges, input enclosures, and inactive tab tracks.
- **Text & Content:** `#182033` provides deep obsidian ink readability for primary titles; `#5F687A` sets secondary descriptors, meta timestamps, and units.
- **Functional Semantics:** Critical states use `#B3261E`; confirmed and achieved milestones use `#287A55`.
- **Media Scrim:** Full bleed event imagery uses a calibrated 40%–68% linear scrim transitioning to `#182033` at the bottom edge to safeguard countdown legibility.

## Typography

The typographic hierarchy balances Outfit’s geometric, sculptural character for large-scale time units with Manrope’s balanced, human legibility for descriptions and labels.

Countdown digits require optical presence without feeling aggressive. Outfit handles numerals with proportional figures and controlled widths. Body copy in Manrope maintains generous x-height and open counters, keeping secondary event notes comfortable to parse against tinted surfaces.

## Layout & Spacing

The mobile layout operates on a 4-column fluid layout with `1.25rem` (20px) outer canvas margins and `1rem` (16px) gutters. Elements strictly adhere to an 8pt conceptual grid, utilizing `4px` increments only for micro-spacing inside pills and small metadata locks.

Vertical stack rhythm favors breathable intervals: `space-lg` between distinct event cards, `space-md` within card sub-structures, and `space-xl` prior to bottom navigation clearance. Screen headers feature deliberate top breathing room, aligning the top-right Join trigger cleanly with page titles.

## Elevation & Depth

Surfaces maintain depth through soft, chromatic tinting rather than standard black dropshadows:

- **Level 0 (Flat Canvas):** `#F7F9FF` base.
- **Level 1 (Event Cards & Insets):** `#FFFFFF` surface accompanied by a soft, diffused ambient drop: `0px 8px 24px -4px rgba(69, 103, 214, 0.06)`. No harsh boundaries or dark outlines.
- **Level 2 (Featured Hero Cards):** Elevated with dual shadows: `0px 4px 12px rgba(69, 103, 214, 0.04)` stacked with `0px 16px 36px -6px rgba(69, 103, 214, 0.10)`.
- **Level 3 (Floating Actions & Modals):** Crisp, buoyant lift: `0px 12px 32px -4px rgba(24, 32, 51, 0.12)`, used specifically for bottom sheets and the elevated center Create control.

## Shapes

The design system maintains a tactile, softened visual language:

- **Featured Cards:** Exactly `20dp` (`1.25rem`) corner radius to create an inviting, tablet-grade presence on hero events.
- **Compact Cards & Insets:** Exactly `16dp` (`1rem`) corner radius for secondary events, list entries, and modal sheets.
- **Interactive Controls & Badges:** `12dp` for text inputs and chips; fully pill-shaped (`9999px`) for buttons, countdown indicators, and the prominent floating Create button.

## Components

### Buttons
- **Primary:** Filled with Soft Blue (`#6C8CFF`), carrying primary dark text (`#182033`) set in `label-lg`. Height is 52dp, with fully rounded pill borders (`9999px`).
- **Secondary:** Filled with Surface Subtle (`#EEF3FF`), featuring Interactive Blue (`#4567D6`) text.
- **Ghost/Tertiary:** Transparent background with `#4567D6` icon and text labels, used for secondary event controls.

### Cards
- **Featured Countdown Card:** 20dp border radius, Level 2 elevation, `#FFFFFF` background. Houses full-bleed photographic media topped with a linear scrim (`#182033` at bottom), hosting an integrated circular progress ring, large Outfit time readout, event title, and collaborator badges.
- **Compact Countdown Card:** 16dp border radius, Level 1 elevation, `#FFFFFF` background, 16dp internal padding. Features horizontal organization: mini circular ring on the left, title and date in the center, and remaining time units grouped on the right.

### Countdown Ring & Sparkle Motif
- **Progress Track:** 4dp stroke thickness. Inactive circumference rendered in `#EEF3FF` or semi-transparent white on imagery; active progress path drawn in `#4567D6` or `#6C8CFF`.
- **Sparkle Motif:** A refined 4-point geometric star (`#6C8CFF` or `#FFFFFF`) anchored to the terminal tip of the active stroke, rotating smoothly as time advances.

### Navigation
- **Top App Bar:** Transparent header with left-aligned branding/title and a pill-shaped "Join" button situated at the top right, styled with a `#EEF3FF` background and `#4567D6` label.
- **Bottom Navigation Bar:** Grounded dock with 3 primary zones:
  1. *Left:* Home item (glyph with subtle label).
  2. *Center:* Prominent circular Create button (56dp diameter, `#4567D6` fill, white plus icon, elevated with Level 3 shadow, physically overlapping the top dock boundary).
  3. *Right:* Profile item (glyph with subtle label).

### Form Inputs & Chips
- **Inputs:** 48dp height, 12dp radius, background `#EEF3FF`, no borders in rest state. Focused state transitions smoothly to an active 1.5dp `#4567D6` outline.
- **Category Chips:** Inset pill shape, 32dp height, `#EEF3FF` background, `#5F687A` text; active state toggles to `#4567D6` fill with `#FFFFFF` text.