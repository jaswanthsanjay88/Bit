import React, { useEffect, useState, useRef } from 'react';
import { useInView, useReducedMotion } from 'motion/react';

interface CountUpProps {
  value: string; // e.g. "100%", "0B", "$0", "2.1k"
  duration?: number;
  className?: string;
  fromValue?: number; // Optional starting numeric offset
}

export const CountUp: React.FC<CountUpProps> = ({
  value,
  duration = 1.2,
  className = '',
  fromValue,
}) => {
  const shouldReduceMotion = useReducedMotion();
  const ref = useRef<HTMLSpanElement>(null);
  const isInView = useInView(ref, { once: true, amount: 0.6 });
  const [displayValue, setDisplayValue] = useState(value);

  // Parse prefix, number, suffix
  const match = value.match(/^([^0-9.]*)([0-9.]+)(.*)$/);
  const prefix = match ? match[1] : '';
  const numStr = match ? match[2] : '0';
  const suffix = match ? match[3] : '';
  const targetNum = parseFloat(numStr);
  const isDecimal = numStr.includes('.');

  useEffect(() => {
    if (!isInView || shouldReduceMotion) {
      setDisplayValue(value);
      return;
    }

    let startTime: number | null = null;
    let animationFrameId: number;

    const startNum = fromValue !== undefined ? fromValue : (targetNum === 0 ? 5 : 0);

    const step = (timestamp: number) => {
      if (!startTime) startTime = timestamp;
      const progress = Math.min((timestamp - startTime) / (duration * 1000), 1);
      // Ease-out cubic: 1 - Math.pow(1 - progress, 3)
      const easedProgress = 1 - Math.pow(1 - progress, 3);
      
      const current = startNum + (targetNum - startNum) * easedProgress;

      let formattedNum = '';
      if (isDecimal) {
        formattedNum = current.toFixed(1);
      } else {
        formattedNum = Math.round(current).toString();
      }

      setDisplayValue(`${prefix}${formattedNum}${suffix}`);

      if (progress < 1) {
        animationFrameId = requestAnimationFrame(step);
      } else {
        setDisplayValue(value);
      }
    };

    animationFrameId = requestAnimationFrame(step);

    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [isInView, value, targetNum, prefix, suffix, isDecimal, duration, fromValue, shouldReduceMotion]);

  return (
    <span ref={ref} className={`tabular-nums inline-block ${className}`}>
      {shouldReduceMotion ? value : displayValue}
    </span>
  );
};
