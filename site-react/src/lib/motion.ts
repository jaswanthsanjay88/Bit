import { useReducedMotion } from 'motion/react';

export const motionTokens = {
  duration: {
    instant: 0.08,
    fast: 0.18,
    normal: 0.32,
    slow: 0.55,
    crawl: 0.9,
  },
  easing: {
    smooth: [0.22, 1, 0.36, 1] as const,
    sharp: [0.4, 0, 0.2, 1] as const,
    linear: [0, 0, 1, 1] as const,
  },
  distance: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 40,
  },
  scale: {
    subtle: 0.98,
    press: 0.96,
    pop: 1.02,
  },
};

export const springs = {
  snappy: { type: 'spring', stiffness: 320, damping: 28 },
  gentle: { type: 'spring', stiffness: 140, damping: 18 },
  bouncy: { type: 'spring', stiffness: 420, damping: 14 },
  instant: { type: 'spring', stiffness: 600, damping: 35 },
  release: { type: 'spring', stiffness: 220, damping: 22, restDelta: 0.001 },
} as const;

export function useSafeMotion(fullY: number = 16) {
  const reduce = useReducedMotion();
  return {
    initial: { opacity: 0, y: reduce ? 0 : fullY },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: reduce ? 0 : -fullY },
    transition: springs.snappy,
  };
}
