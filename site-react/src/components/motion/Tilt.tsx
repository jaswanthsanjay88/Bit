import React, { useRef, useState, useEffect } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { springSoft } from '../../lib/motion';

interface TiltProps {
  children: React.ReactNode;
  maxTilt?: number; // max tilt in degrees, default 6
  perspective?: number; // default 1000
  className?: string;
}

export const Tilt: React.FC<TiltProps> = ({
  children,
  maxTilt = 6,
  perspective = 1000,
  className = '',
}) => {
  const shouldReduceMotion = useReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const [rotation, setRotation] = useState({ rotateX: 0, rotateY: 0 });
  const [canHover, setCanHover] = useState(true);

  useEffect(() => {
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
    const rect = ref.current.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    const mouseX = e.clientX - centerX;
    const mouseY = e.clientY - centerY;

    const percentX = mouseX / (rect.width / 2);
    const percentY = mouseY / (rect.height / 2);

    // RotateX is driven by mouseY (inverted), rotateY by mouseX
    setRotation({
      rotateX: -percentY * maxTilt,
      rotateY: percentX * maxTilt,
    });
  };

  const handleMouseLeave = () => {
    setRotation({ rotateX: 0, rotateY: 0 });
  };

  return (
    <div
      ref={ref}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={{ perspective: `${perspective}px` }}
      className={`inline-block ${className}`}
    >
      <motion.div
        animate={{
          rotateX: rotation.rotateX,
          rotateY: rotation.rotateY,
        }}
        transition={springSoft}
        style={{ transformStyle: 'preserve-3d' }}
      >
        {children}
      </motion.div>
    </div>
  );
};
