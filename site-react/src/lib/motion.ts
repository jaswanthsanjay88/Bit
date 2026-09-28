export const ease = [0.22, 1, 0.36, 1] as const;
export const easeInOut = [0.65, 0, 0.35, 1] as const;
export const easeSpringSoft = [0.34, 1.56, 0.64, 1] as const;

export const spring = {
  type: 'spring',
  stiffness: 380,
  damping: 32,
  mass: 0.8,
} as const;

export const springSoft = {
  type: 'spring',
  stiffness: 220,
  damping: 26,
} as const;

// Backward-compatibility alias
export const springs = {
  snappy: spring,
  gentle: springSoft,
  bouncy: { type: 'spring', stiffness: 400, damping: 20 },
} as const;

export const fadeUp = {
  hidden: { opacity: 0, y: 16, filter: 'blur(4px)' },
  show: {
    opacity: 1,
    y: 0,
    filter: 'blur(0px)',
    transition: { duration: 0.7, ease },
  },
};

export const stagger = (gap = 0.08, delay = 0) => ({
  hidden: {},
  show: {
    transition: {
      staggerChildren: gap,
      delayChildren: delay,
    },
  },
});
