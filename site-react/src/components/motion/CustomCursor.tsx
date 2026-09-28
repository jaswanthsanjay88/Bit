import React, { useEffect, useState } from 'react';
import { motion, useSpring, useReducedMotion } from 'motion/react';

export const CustomCursor: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [canHover, setCanHover] = useState(false);
  const [isInteractive, setIsInteractive] = useState(false);
  const [isHidden, setIsHidden] = useState(false);
  const [isVisible, setIsVisible] = useState(false);

  // Smooth spring coordinates
  const cursorX = useSpring(-100, { stiffness: 600, damping: 35 });
  const cursorY = useSpring(-100, { stiffness: 600, damping: 35 });

  useEffect(() => {
    // Only enable on desktop with fine pointer
    const media = window.matchMedia('(hover: hover) and (pointer: fine)');
    setCanHover(media.matches);

    if (!media.matches || shouldReduceMotion) return;

    const handleMouseMove = (e: MouseEvent) => {
      cursorX.set(e.clientX);
      cursorY.set(e.clientY);
      if (!isVisible) setIsVisible(true);
    };

    const handleMouseOver = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (!target) return;

      const interactive = target.closest('a, button, [role="button"], input, textarea, [data-interactive="true"]');
      if (interactive) {
        if (target.matches('input, textarea')) {
          setIsHidden(true);
          setIsInteractive(false);
        } else {
          setIsHidden(false);
          setIsInteractive(true);
        }
      } else {
        setIsHidden(false);
        setIsInteractive(false);
      }
    };

    const handleMouseLeave = () => {
      setIsVisible(false);
    };

    window.addEventListener('mousemove', handleMouseMove, { passive: true });
    window.addEventListener('mouseover', handleMouseOver, { passive: true });
    document.addEventListener('mouseleave', handleMouseLeave);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseover', handleMouseOver);
      document.removeEventListener('mouseleave', handleMouseLeave);
    };
  }, [cursorX, cursorY, isVisible, shouldReduceMotion]);

  if (shouldReduceMotion || !canHover || !isVisible) {
    return null;
  }

  const size = isInteractive ? 36 : 8;

  return (
    <motion.div
      style={{
        x: cursorX,
        y: cursorY,
        translateX: '-50%',
        translateY: '-50%',
      }}
      animate={{
        width: size,
        height: size,
        opacity: isHidden ? 0 : isInteractive ? 0.35 : 0.6,
      }}
      transition={{ duration: 0.15 }}
      className="fixed top-0 left-0 rounded-full bg-[#0a0a0a] pointer-events-none z-[9999] transition-colors"
      aria-hidden="true"
    />
  );
};
