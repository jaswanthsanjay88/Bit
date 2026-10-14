# BIT — Design System & Visual Specification

> **Version:** 2.4.0  
> **Aesthetic Core:** Precision Monolith · Tactile Glassmorphism · Distraction-Free On-Device Intelligence  
> **Platform:** Android (Jetpack Compose, Material Design 3 Expressive, Edge-to-Edge)

---

## Table of Contents
1. [App Logo & Vector Identity](#1-app-logo--vector-identity)
   - [SVG Code — Primary Dark (OLED Carbon)](#svg-code--primary-dark-oled-carbon)
   - [SVG Code — Light Theme (Zinc Clean)](#svg-code--light-theme-zinc-clean)
   - [SVG Code — Transparent Vector Glyph](#svg-code--transparent-vector-glyph)
   - [SVG Code — Mint Accent Variant](#svg-code--mint-accent-variant)
   - [Mathematical Construction & Grid](#mathematical-construction--grid)
   - [Symbolism & Semantics](#symbolism--semantics)
2. [Color Architecture & Design Tokens](#2-color-architecture--design-tokens)
   - [Obsidian Stealth (Monochrome OLED)](#obsidian-stealth-monochrome-oled)
   - [Obsidian Light (Zinc Day Theme)](#obsidian-light-zinc-day-theme)
   - [Dynamic Monet Engine](#dynamic-monet-engine)
   - [Glassmorphic Surface Tokens (`GlassTokens`)](#glassmorphic-surface-tokens-glasstokens)
3. [Typography Hierarchy](#3-typography-hierarchy)
   - [Type Families](#type-families)
   - [Typographic Scale Table](#typographic-scale-table)
4. [Motion & Animation System](#4-motion--animation-system)
   - [M3 Expressive Spring Physics](#m3-expressive-spring-physics)
   - [Spatial Page Transitions](#spatial-page-transitions)
   - [Theme-Adaptive Splash Screen Logo Choreography](#theme-adaptive-splash-screen-logo-choreography)
   - [Tactile Haptic Feedback Tokens](#tactile-haptic-feedback-tokens)
5. [Component Specifications](#5-component-specifications)
   - [Navigation & Window Insets](#navigation--window-insets)
   - [Chat Canvas & Message Nodes](#chat-canvas--message-nodes)
   - [Glass Action Dock & Input Field](#glass-action-dock--input-field)
   - [Model Selector & Modal Sheets](#model-selector--modal-sheets)
6. [Developer Handoff & Compose Code Snippets](#6-developer-handoff--compose-code-snippets)

---

## 1. App Logo & Vector Identity

The BIT identity mark is an architectural monolithic **"B"** ligature composed of three pure geometric blocks: a vertical anchor spine and two stacked computational data registers.

### SVG Code — Primary Dark (OLED Carbon)
*Ideal for dark mode, OLED displays, GitHub README headers, app stores, and app icons.*

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
  <rect width="120" height="120" rx="26" fill="#121214" />
  <!-- Monolithic Spine -->
  <rect x="30" y="35" width="15" height="55" rx="3.5" fill="#FFFFFF" />
  <!-- Upper Data Block -->
  <rect x="55" y="35" width="30" height="25" rx="3.5" fill="#FFFFFF" />
  <!-- Lower Data Block -->
  <rect x="55" y="65" width="30" height="25" rx="3.5" fill="#FFFFFF" />
</svg>
```

### SVG Code — Light Theme (Zinc Clean)
*Optimized for light surfaces, documentation exports, white backgrounds, and print.*

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
  <rect width="120" height="120" rx="26" fill="#F4F4F6" stroke="#E4E4E7" stroke-width="1.5" />
  <!-- Monolithic Spine -->
  <rect x="30" y="35" width="15" height="55" rx="3.5" fill="#18181B" />
  <!-- Upper Data Block -->
  <rect x="55" y="35" width="30" height="25" rx="3.5" fill="#18181B" />
  <!-- Lower Data Block -->
  <rect x="55" y="65" width="30" height="25" rx="3.5" fill="#18181B" />
</svg>
```

### SVG Code — Transparent Vector Glyph
*Zero-background vector path for watermarks, app navigation headers, favicon use, and in-app tinting.*

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120" fill="currentColor">
  <!-- Monolithic Spine -->
  <rect x="30" y="35" width="15" height="55" rx="3.5" />
  <!-- Upper Data Block -->
  <rect x="55" y="35" width="30" height="25" rx="3.5" />
  <!-- Lower Data Block -->
  <rect x="55" y="65" width="30" height="25" rx="3.5" />
</svg>
```

### SVG Code — Mint Accent Variant
*Signature branded theme variant used in Seafoam Mint & high-contrast marketing assets.*

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
  <defs>
    <radialGradient id="glow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#5EEAD4" stop-opacity="0.25"/>
      <stop offset="100%" stop-color="#0D1514" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="120" height="120" rx="26" fill="#0D1514" />
  <circle cx="60" cy="60" r="54" fill="url(#glow)" />
  <!-- Monolithic Spine -->
  <rect x="30" y="35" width="15" height="55" rx="3.5" fill="#5EEAD4" />
  <!-- Upper Data Block -->
  <rect x="55" y="35" width="30" height="25" rx="3.5" fill="#5EEAD4" />
  <!-- Lower Data Block -->
  <rect x="55" y="65" width="30" height="25" rx="3.5" fill="#5EEAD4" />
</svg>
```

---

### Mathematical Construction & Grid

The logo geometry lives on a **$120 \times 120$ square unit viewport** with a balanced $30\text{ unit}$ horizontal margin:

| Element | $X$ Origin | $Y$ Origin | Width | Height | Aspect Ratio | Corner Radius |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Squircle Container** | $0$ | $0$ | $120$ | $120$ | $1:1$ | $rx = 26$ ($21.6\%$) |
| **Monolithic Spine** | $30$ | $35$ | $15$ | $55$ | $1 : 3.67$ | $rx = 3.5$ |
| **Upper Data Block** | $55$ | $35$ | $30$ | $25$ | $1.2 : 1$ | $rx = 3.5$ |
| **Lower Data Block** | $55$ | $65$ | $30$ | $25$ | $1.2 : 1$ | $rx = 3.5$ |
| **Horizontal Gap** | $45 \rightarrow 55$ | — | $10$ | — | — | Negative space canal |
| **Vertical Inter-block Gap** | — | $60 \rightarrow 65$ | — | $10$ | — | Negative space canal |

```
0                        60                       120
+---------------------------------------------------+  0
|     .---------------------------------------.     |
|    /                                         \    |
|   |         [30]    [45] [55]         [85]    |   |
|   |          |       |    |            |      |   |
|   |   (35)---+-------+    +------------+      |   |
|   |      |   |       |    |   UPPER    |      |   |
|   |      |   | SPINE |    |   BLOCK    |      |   |
|   |      |   |       |    +------------+-(60) |   |
|   |      |   |       |      <gap: 5>          |   |
|   |      |   |       |    +------------+-(65) |   |
|   |      |   |       |    |   LOWER    |      |   |
|   |      |   |       |    |   BLOCK    |      |   |
|   |   (90)---+-------+    +------------+      |   |
|   |                                           |   |
|    \                                         /    |
|     '---------------------------------------'     |
+---------------------------------------------------+  120
```

### Symbolism & Semantics
- **The "B" Monolith:** Represents the name **BIT**, standing as a solitary, solid pillar of local agency.
- **The Pillar & Registers:** The left vertical column symbolizes the local memory engine / neural spine. The dual blocks represent the binary fundamental units of computing ($0$ and $1$, storage and execution).
- **Tactile Negative Space:** The $10\text{ unit}$ cross canals allow clean rendering down to $16\text{dp}$ without pixel collision or stroke degradation.

---

## 2. Color Architecture & Design Tokens

BIT operates on a **dynamic-first, strict-tonal contrast** foundation. It prioritizes OLED true blacks in dark environments and crisp zinc neutrals in daylight.

### Obsidian Stealth (Monochrome OLED)
*Primary Dark System Theme (`id = "obsidian"`)*

| Token Role | Hex Color | Purpose / Layer |
| :--- | :--- | :--- |
| `background` | `#000000` | True pitch black (zero OLED power draw) |
| `surface` | `#0F0F0F` | Bottom sheets, cards, dialog canvases |
| `surfaceVariant` | `#1E1E1E` | Inactive chip backgrounds, message bubbles |
| `surfaceContainerLow` | `#141414` | Subtle container separation |
| `surfaceContainerHigh`| `#222222` | Raised toolcards, floating menu surfaces |
| `primary` | `#FFFFFF` | Core actionable elements, glyph fills, high emphasis text |
| `primaryContainer` | `#262626` | Active toggle container, secondary pills |
| `outline` | `#555555` | Hairline card strokes |
| `outlineVariant` | `#2E2E2E` | Subtle dividers, inactive tab indicators |
| `error` | `#FF6E6E` | Soft coral alert & error badges |

### Obsidian Light (Zinc Day Theme)
*Clean Minimalist Daylight Palette*

| Token Role | Hex Color | Purpose / Layer |
| :--- | :--- | :--- |
| `background` | `#FAFAFA` | Warm paper white canvas |
| `surface` | `#FFFFFF` | Crisp elevated surface cards |
| `surfaceVariant` | `#F4F4F5` | Neutral pills, secondary controls |
| `surfaceContainer` | `#F1F1F3` | Chat input dock, drawer canvas |
| `primary` | `#18181B` | High-contrast zinc carbon text & icons |
| `primaryContainer` | `#E4E4E7` | Active selection pills |
| `outline` | `#A1A1AA` | Clear border definitions |
| `outlineVariant` | `#E4E4E7` | Micro-dividers and soft rules |
| `error` | `#DC2626` | High-contrast crimson validation alerts |

### Dynamic Monet Engine
On Android 12+ (`SDK >= 31`), BIT queries `dynamicDarkColorScheme(context)` and `dynamicLightColorScheme(context)` when dynamic color is enabled in settings.
- All core UI components bind to semantic Material 3 roles (`MaterialTheme.colorScheme.primary`, `surface`, `background`, `onSurface`).
- When dynamic color is toggled off, the user can select curated palettes: **Obsidian**, **Seafoam Mint**, **Crimson**, **Cyberpunk Violet**, or **Amber**.

### Glassmorphic Surface Tokens (`GlassTokens`)
All frosted translucent layers reference the unified `LocalGlass` composition token:

```kotlin
// Dark Mode Glass Profile
DarkGlassTokens(
    Surface         = Color(0xFA0F0F12), // 98% opacity deep glass
    SurfaceElevated = Color(0xFC121216), // 99% opacity elevated sheet
    Border          = Color(0x26FFFFFF), // 15% white frosted edge
    BorderSubtle    = Color(0x1AFFFFFF), // 10% white micro-edge
    AccentPrimary   = Color(0xFFFFFFFF),
    TextPrimary     = Color(0xFFF5F5F5),
    TextSecondary   = Color(0xFFBDBDBD),
    Scrim           = Color(0xF2000000)  // 95% black backdrop blur
)

// Light Mode Glass Profile
LightGlassTokens(
    Surface         = Color(0xF2F2F2F6), // 95% opacity frosted zinc
    SurfaceElevated = Color(0xFFFFFFFF), // Solid elevated card
    Border          = Color(0x1F000000), // 12% black frosted edge
    BorderSubtle    = Color(0x14000000), // 8% black micro-edge
    AccentPrimary   = Color(0xFF1C1B1F),
    TextPrimary     = Color(0xFF1C1B1F),
    TextSecondary   = Color(0xFF49454F),
    Scrim           = Color(0x66000000)  // 40% soft translucent veil
)
```

---

## 3. Typography Hierarchy

### Type Families
1. **Primary Body & Display:** **Manrope** (Modern Geometric Sans, open counters, neutral tech voice).
2. **System Fallback:** **Google Sans** / Roboto.
3. **Monospace & Code:** **Maple Mono** / **Google Sans Code** (with clean ligature support for symbols, JSON payloads, and diff blocks).

### Typographic Scale Table

| Style Role | Font Weight | Size (`sp`) | Line Height (`sp`) | Tracking (`sp`) | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `displayLarge` | ExtraBold (800) | `32.sp` | `38.sp` | `-0.25.sp` | Hero numbers, intro headlines |
| `headlineMedium`| Bold (700) | `22.sp` | `28.sp` | `0.0.sp` | Screen titles, sheet headers |
| `titleLarge` | SemiBold (600) | `18.sp` | `24.sp` | `0.15.sp` | Top bar title, card titles |
| `titleMedium` | Medium (500) | `15.sp` | `20.sp` | `0.1.sp` | Section grouping headers |
| `bodyLarge` | Normal (400) | `15.sp` | `22.sp` | `0.15.sp` | Main assistant chat responses |
| `bodyMedium` | Normal (400) | `13.5.sp`| `19.sp` | `0.2.sp` | Secondary explanations, tool inputs |
| `labelLarge` | SemiBold (600) | `13.sp` | `18.sp` | `0.25.sp` | Action buttons, pill chips |
| `labelSmall` | Medium (500) | `11.sp` | `15.sp` | `0.4.sp` | Token counters, cadence indicators |

---

## 4. Motion & Animation System

BIT implements **Material 3 Expressive Motion** grounded in real-world spring mechanics rather than artificial linear tweens.

### M3 Expressive Spring Physics
Transitions are configured via `spring(...)` tokens designed for zero jitter and 60/120 FPS high refresh rates:

```kotlin
// Spatial Navigation Physics
StiffnessMediumLow = 400f   // Natural responsive response without sluggishness
DampingRatioNoBouncy = 1.0f // Critical damping: zero overshoot, zero jarring bounce

// Micro-interaction Feedback
StiffnessHigh = 800f        // Button press scale depressions
```

### Spatial Page Transitions
Page navigation implements directional parallax slide paired with subtle depth scaling:
- **Forward Enter:** Slides in from right ($+22\%$ width offset) + scale from $0.96 \rightarrow 1.0$ (fade-in $0 \rightarrow 1$).
- **Forward Exit:** Shifts slightly backward ($-12\%$ width offset) + scale down to $0.96$.
- **Pop Enter:** Returns from depth ($0.96 \rightarrow 1.0$) + slides back from $-12\%$.
- **Pop Exit:** Slides away to the right ($+22\%$) + scale down to $0.96$.

### Theme-Adaptive Splash Screen Logo Choreography
The splash screen (`IntroScreen`) animates the BIT mark dynamically adapting to whatever theme the user has selected:

1. **Ambient Theme Bloom ($0 \rightarrow 300\text{ms}$):**
   - A soft circular radial gradient (`Brush.radialGradient`) blossoms centered behind the mark.
   - Tinted directly with `MaterialTheme.colorScheme.primary` (White in Obsidian Dark, Zinc in Obsidian Light, Mint in Seafoam).
2. **Staggered Multi-Axis Clip Sweep:**
   - **Monolithic Spine ($0 \rightarrow 280\text{ms}$):** Wipes upward from the base ($Y_{\text{bottom}} \rightarrow Y_{\text{top}}$) like an emerging pillar.
   - **Upper Data Block ($50 \rightarrow 330\text{ms}$):** Cascades downward ($Y_{\text{top}} \rightarrow Y_{\text{bottom}}$).
   - **Lower Data Block ($100 \rightarrow 380\text{ms}$):** Cascades downward in synchronized cadence.
3. **Leading-Edge Shimmer ($0 \rightarrow 380\text{ms}$):**
   - A soft luminous crest tracks the moving mask edge at $35\%$ alpha.
4. **Spring Settle ($380 \rightarrow 510\text{ms}$):**
   - Scale gently expands to $1.025$ before critically settling to $1.0$.
5. **Shared Element Handoff:**
   - Seamlessly morphs through `sharedElement(key = "bit_mark")` into the top bar / onboarding anchor.

### Tactile Haptic Feedback Tokens
Every user touch communicates state through `LocalBitHaptics`:
- `tap()`: Subtle $5\text{ms}$ tick for keyboard and chip selection.
- `select()`: Crisp confirmation pulse on radio / model switch.
- `toggle()`: Sharp feedback when enabling switches.
- `delete()` / `heavy()`: Deliberate double-pulse on destructive removals.

---

## 5. Component Specifications

### Navigation & Window Insets
- **True Edge-to-Edge:** Enabled across all screens via `enableEdgeToEdge()`.
- **Dynamic System Bars:** Status bar luminance dynamically switches `isAppearanceLightStatusBars` depending on whether `colorScheme.surface.luminance() > 0.5f`.
- **Navigation Bar:** Immersive translucent surface tinting with zero window flicker.

### Chat Canvas & Message Nodes
- **User Bubble:** Right-aligned, encapsulated in `MaterialTheme.colorScheme.primaryContainer`, rounded with `BitShapes.large` ($18\text{dp}$ radius with $4\text{dp}$ tail).
- **Assistant Node:** Left-aligned, unboxed canvas layout with high-contrast `TextPrimary`, markdown rendering, copy actions, and audio readout dock.
- **Tool Execution Card:** Monospaced collapsed drawer with status badge (Success: White/Mint, Running: Subtle rotating dot indicator).

### Glass Action Dock & Input Field
- Floating pill structure pinned above navigation bar with `imePadding()`.
- Background: `Glass.Surface` with hairline `Glass.Border` stroke ($1\text{dp}$).
- Trailing send / mic button: circular pill morphing between send chevron and stop square during model generation.

---

## 6. Developer Handoff & Compose Code Snippets

### Embedding the Logo in Jetpack Compose
To render the BIT mark anywhere in the app with theme adaptation:

```kotlin
import com.bit.ui.screen.intro.AnimatedLogo

// Uses the active theme's primary color automatically
AnimatedLogo(
    modifier = Modifier.size(36.dp),
    color = MaterialTheme.colorScheme.primary
)
```

### Accessing Glass Tokens in Any Composable
```kotlin
import com.bit.ui.theme.Glass

Surface(
    color = Glass.Surface,
    border = BorderStroke(1.dp, Glass.Border),
    shape = RoundedCornerShape(16.dp)
) {
    Text(
        text = "Secured In-Memory Vault",
        color = Glass.TextPrimary
    )
}
```

---

*Authored for the BIT Project. All vector coordinates, token roles, and animation timings are synchronized with the active codebase.*
