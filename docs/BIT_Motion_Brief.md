# BIT — Motion Brief & Animation Specification

> **Aesthetic Core:** Precision Monolith · Tactile Responsiveness · Zero Jitter

---

## 1. M3 Expressive Spring Physics

All spatial and scaling interactions are physics-driven via Jetpack Compose spring specs:

```kotlin
// Fluid expansion and spatial navigation transitions
val SpatialSpring = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 380f
)

// Card expansion / collapse spring
val ExpandSpring = spring<IntSize>(
    dampingRatio = 1.0f,
    stiffness = 400f
)

// Snappy micro-interactions (chevrons, icons, badges)
val MicroSpring = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = 500f
)
```

---

## 2. Logo Choreography & Presence States

The top-bar logo reflects the live agent harness state:

| Presence State | Visual Behavior | Trigger |
| :--- | :--- | :--- |
| `BitPresence.Idle` | Steady glyph, resting contrast | No active inference |
| `BitPresence.Thinking` | Subtle breathing luminance pulse (1600ms cycle) | Reasoning tokens streaming |
| `BitPresence.Streaming` | Focused active glow | Answer text streaming to UI |

Header shimmer and top-bar logo pulse start and stop synchronously.

---

## 3. Calm Mode & Thermal Adaptation

To preserve device battery and prevent GPU jank on saturated mobile hardware:
- Shimmer sweep disabled when `PowerManager.isPowerSaveMode` is true.
- Shimmer sweep disabled when `PowerManager.currentThermalStatus >= THERMAL_STATUS_MODERATE`.
- Shimmer sweep disabled when `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`.
- Shimmer sweep is draw-phase only (`drawWithContent` + `CompositingStrategy.Offscreen` + `BlendMode.SrcAtop`), incurring 0 recompositions.
