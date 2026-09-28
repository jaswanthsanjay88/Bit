import React, { useRef, useState, useEffect } from 'react';
import { useReducedMotion } from 'motion/react';

interface SpotlightProps {
  children: React.ReactNode;
  className?: string;
  size?: number; // radius in px, default 240px
  color?: string; // default rgba(10,10,10,0.035)
}

export const Spotlight: React.FC<SpotlightProps> = ({
  children,
  className = '',
  size = 240,
  color = 'rgba(10, 10, 10, 0.035)',
}) => {
  const shouldReduceMotion = useReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const [coords, setCoords] = useState<{ x: number; y: number } | null>(null);
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
    setCoords({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
  };

  const handleMouseLeave = () => {
    setCoords(null);
  };

  return (
    <div
      ref={ref}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      className={`relative overflow-hidden ${className}`}
      style={{
        '--mx': coords ? `${coords.x}px` : '-999px',
        '--my': coords ? `${coords.y}px` : '-999px',
      } as React.CSSProperties}
    >
      {coords && (
        <div
          className="pointer-events-none absolute inset-0 z-0 transition-opacity duration-300"
          style={{
            background: `radial-gradient(${size}px circle at ${coords.x}px ${coords.y}px, ${color}, transparent 80%)`,
          }}
        />
      )}
      <div className="relative z-10">{children}</div>
    </div>
  );
};
