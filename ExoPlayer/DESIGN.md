---
name: Sahab IPTV
colors:
  surface: '#151024'
  surface-dim: '#151024'
  surface-bright: '#3b364b'
  surface-container-lowest: '#0f0b1e'
  surface-container-low: '#1d192c'
  surface-container: '#211d31'
  surface-container-high: '#2b273c'
  surface-container-highest: '#363247'
  on-surface: '#e7defa'
  on-surface-variant: '#cfc2d7'
  inverse-surface: '#e7defa'
  inverse-on-surface: '#322d42'
  outline: '#988ca0'
  outline-variant: '#4c4354'
  surface-tint: '#dcb8ff'
  primary: '#dcb8ff'
  on-primary: '#480081'
  primary-container: '#8a2be2'
  on-primary-container: '#eed9ff'
  inverse-primary: '#8422dc'
  secondary: '#ffffff'
  on-secondary: '#003737'
  secondary-container: '#00fbfb'
  on-secondary-container: '#007070'
  tertiary: '#c4c1fb'
  on-tertiary: '#2d2a5b'
  tertiary-container: '#615e92'
  on-tertiary-container: '#e1ddff'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#efdbff'
  primary-fixed-dim: '#dcb8ff'
  on-primary-fixed: '#2c0051'
  on-primary-fixed-variant: '#6700b5'
  secondary-fixed: '#00fbfb'
  secondary-fixed-dim: '#00dddd'
  on-secondary-fixed: '#002020'
  on-secondary-fixed-variant: '#004f4f'
  tertiary-fixed: '#e3dfff'
  tertiary-fixed-dim: '#c4c1fb'
  on-tertiary-fixed: '#181445'
  on-tertiary-fixed-variant: '#444173'
  background: '#151024'
  on-background: '#e7defa'
  surface-variant: '#363247'
typography:
  headline-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-md:
    fontFamily: Be Vietnam Pro
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Be Vietnam Pro
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Be Vietnam Pro
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1.5rem
  margin: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style
This design system embraces a **Glassmorphism** aesthetic tailored specifically for a cinematic, high-end entertainment experience (IPTV). It evokes an immersive, premium, and futuristic atmosphere through deep space backgrounds, vibrant neon purple and cyan accents, and translucent frosted glass surfaces. The target audience expects a polished, responsive, and distraction-free interface optimized for media consumption, live sports, and video streaming. All components fully support RTL layouts and Arabic typography.

## Colors
The color system relies on deep midnight blues and dark purples to simulate a cosmic, theatrical environment. Vibrant purple serves as the primary action and focus indicator, while electric cyan provides high-visibility active states and glowing accents. Neutral surfaces use semi-transparent dark layers with subtle borders to achieve the desired glassmorphic depth.

## Typography
Typography is clean, highly legible, and optimized for both large TV screens and dense data displays. The chosen font family pairs exceptionally well with Arabic script requirements, ensuring distinct hierarchy across channel listings, Electronic Program Guides (EPG), and media titles.

## Layout & Spacing
A fluid grid layout is utilized to adapt seamlessly across large displays, tablets, and mobile viewports. Generous outer margins and consistent gutters maintain breathing room between complex panels such as the navigation sidebar, channel rows, and the main video preview viewport.

## Elevation & Depth
Depth is primarily conveyed through **Glassmorphism** and **Ambient shadows**. Surfaces stack using semi-transparent dark fills paired with subtle, low-opacity light borders (ghost borders) and soft, diffused glowing shadows tinted with the primary purple and cyan accents to create a layered, holographic feel.

## Shapes
A roundedness level of `2` (Rounded) is applied across all interface elements. Cards, input fields, navigation pills, and video containers feature smooth, friendly corner radii (0.5rem to 1.5rem) that soften the dark futuristic aesthetic and enhance the premium streaming experience.

## Components

- **Buttons:** Styled with translucent glass backgrounds, glowing borders on hover/focus, and vibrant purple or cyan gradients for primary actions. Icons are paired seamlessly with Arabic text labels respecting RTL direction.
- **Chips & Navigation Items:** Pill-shaped category selectors with clear active states highlighted by glowing neon purple fills and badge counters aligned to the opposite side.
- **Lists & Channel Rows:** Horizontal card strips featuring channel numbers, logos, live progress indicators ("مباشر الآن"), and smooth progress bars for current broadcasts.
- **Checkboxes & Radio Controls:** Minimalist custom toggles using glowing accent rings and subtle background fills to indicate active states.
- **Input Fields:** Semi-transparent dark inputs featuring subtle inner shadows, clear placeholder text, and leading search icons positioned for RTL reading flows.
- **Cards (Media & Video):** Frosted glass containers designed for live TV previews and EPG schedules, complete with embedded video controls, progress tracks, and high-contrast metadata typography.