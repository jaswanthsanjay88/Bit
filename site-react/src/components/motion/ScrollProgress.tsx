import React from 'react';
import { motion, useScroll, useSpring, useReducedMotion } from 'motion/react';

export const ScrollProgress: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, {
    stiffness: 400,
    damping: 40,
    restDelta: 0.001,
  });

  if (shouldReduceMotion) {
    return null;
  }

  return (
    <motion.div
      style={{ scaleX, transformOrigin: '0% 50%' }}
      className="fixed top-0 left-0 right-0 h-[1.5px] bg-[#0A0A0A] z-[100] pointer-events-none"
      aria-hidden="true"
    />
  );
};
