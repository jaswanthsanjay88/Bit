import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { fadeUp, ease } from '../../lib/motion';

interface RevealProps {
  children: React.ReactNode;
  delay?: number;
  className?: string;
  as?: React.ElementType;
}

export const Reveal: React.FC<RevealProps> = ({
  children,
  delay = 0,
  className = '',
  as: Component = 'div',
}) => {
  const shouldReduceMotion = useReducedMotion();
  const MotionComponent = motion(Component as any);

  if (shouldReduceMotion) {
    return (
      <MotionComponent
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true, margin: '-12% 0px' }}
        transition={{ duration: 0.15 }}
        className={className}
      >
        {children}
      </MotionComponent>
    );
  }

  return (
    <MotionComponent
      variants={fadeUp}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: '-12% 0px' }}
      transition={{ duration: 0.7, delay, ease }}
      className={className}
    >
      {children}
    </MotionComponent>
  );
};
