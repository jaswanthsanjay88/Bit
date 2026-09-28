import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ease } from '../../lib/motion';

interface RevealTextProps {
  text: string;
  as?: 'h1' | 'h2' | 'h3' | 'p' | 'span';
  delay?: number;
  className?: string;
  staggerDelay?: number;
}

export const RevealText: React.FC<RevealTextProps> = ({
  text,
  as: Component = 'h2',
  delay = 0,
  className = '',
  staggerDelay = 0.04,
}) => {
  const shouldReduceMotion = useReducedMotion();
  const words = text.split(' ');

  if (shouldReduceMotion) {
    const Tag = Component;
    return <Tag className={className}>{text}</Tag>;
  }

  const containerVariants = {
    hidden: {},
    show: {
      transition: {
        staggerChildren: staggerDelay,
        delayChildren: delay,
      },
    },
  };

  const wordVariants = {
    hidden: { y: '100%', opacity: 0 },
    show: {
      y: '0%',
      opacity: 1,
      transition: {
        duration: 0.65,
        ease,
      },
    },
  };

  const MotionTag = motion[Component];

  return (
    <MotionTag
      variants={containerVariants}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: '-10% 0px' }}
      className={`inline-block overflow-hidden ${className}`}
    >
      {words.map((word, idx) => (
        <span key={idx} className="inline-block overflow-hidden mr-[0.26em] last:mr-0 align-top">
          <motion.span variants={wordVariants} className="inline-block">
            {word}
          </motion.span>
        </span>
      ))}
    </MotionTag>
  );
};
