import React, { useRef, useState, useEffect } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { spring } from '../../lib/motion';

interface MagneticProps {
  children: React.ReactNode;
  maxDistance?: number;
  radius?: number;
  className?: string;
}

export const Magnetic: React.FC<MagneticProps> = ({
  children,
  maxDistance = 8,
  radius = 80,
  className = '',
}) => {
  const shouldReduceMotion = useReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [canHover, setCanHover] = useState(true);

  useEffect(() => {
    // Check if hover is supported (disable on touch devices)
    if (typeof window !== 'undefined') {
      const mediaQuery = window.matchMedia('(hover: hover) and (pointer: fine)');
      setCanHover(mediaQuery.matches);
      const listener = (e: MediaQueryListEvent) => setCanHover(e.matches);
      mediaQuery.addEventListener('change', listener);
      return () => mediaQuery.removeEventListener('change', listener);
    }
  }, []);

  if (shouldReduceMotion || !canHover) {
    return <div className={className}>{children}</div>;
  }

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!ref.current) return;
    const { clientX, clientY } = e;
    const { left, top, width, height } = ref.current.getBoundingClientRect();
    const centerX = left + width / 2;
    const centerY = top + height / 2;

    const deltaX = clientX - centerX;
    const deltaY = clientY - centerY;
    const distance = Math.hypot(deltaX, deltaY);

    if (distance < radius) {
      const pullFactor = (1 - distance / radius) * (maxDistance / distance || 0);
      setPosition({
        x: deltaX * pullFactor,
        y: deltaY * pullFactor,
      });
    } else {
      setPosition({ x: 0, y: 0 });
    }
  };

  const handleMouseLeave = () => {
    setPosition({ x: 0, y: 0 });
  };

  return (
    <motion.div
      ref={ref}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      animate={{ x: position.x, y: position.y }}
      transition={spring}
      className={`inline-block ${className}`}
    >
      {children}
    </motion.div>
  );
};
