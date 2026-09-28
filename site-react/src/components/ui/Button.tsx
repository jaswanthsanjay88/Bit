import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ease } from '../../lib/motion';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'ghost' | 'dark-ghost';
  size?: 'sm' | 'md' | 'lg';
  href?: string;
  target?: string;
  rel?: string;
  icon?: React.ReactNode;
  badge?: React.ReactNode;
  children: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  href,
  target,
  rel,
  icon,
  badge,
  children,
  className = '',
  ...props
}) => {
  const shouldReduceMotion = useReducedMotion();

  const baseClasses =
    'group relative inline-flex items-center justify-center font-medium rounded-full cursor-pointer focus-ring select-none text-center transition-colors duration-150';

  const sizeClasses = {
    sm: 'text-xs sm:text-sm px-4 py-2 gap-1.5 h-8',
    md: 'text-[14.5px] sm:text-[15px] px-6 py-3 gap-2.5 h-11',
    lg: 'text-[16px] sm:text-[17px] px-7 py-3.5 gap-2.5 h-12',
  }[size];

  const variantClasses = {
    primary:
      'bg-[#0a0a0a] text-white hover:bg-[#1a1a1a] border border-[#0a0a0a]',
    ghost:
      'bg-transparent text-[#0a0a0a] border border-[var(--line)] hover:border-[var(--line-strong)] hover:bg-[#fafafa]',
    'dark-ghost':
      'bg-transparent text-white border border-white/20 hover:border-white/40 hover:bg-white/5',
  }[variant];

  const combinedClasses = `${baseClasses} ${sizeClasses} ${variantClasses} ${className}`;

  const content = (
    <>
      {icon && (
        <span className="shrink-0 transition-transform duration-150 group-hover:translate-y-0.5 group-hover:scale-105">
          {icon}
        </span>
      )}
      <span>{children}</span>
      {badge && <span className="ml-1 shrink-0">{badge}</span>}
    </>
  );

  if (href) {
    return (
      <motion.a
        href={href}
        target={target}
        rel={rel}
        whileHover={shouldReduceMotion ? {} : { y: -1 }}
        whileTap={shouldReduceMotion ? {} : { scale: 0.97 }}
        transition={{ duration: 0.1, ease }}
        className={combinedClasses}
      >
        {content}
      </motion.a>
    );
  }

  return (
    <motion.button
      whileHover={shouldReduceMotion ? {} : { y: -1 }}
      whileTap={shouldReduceMotion ? {} : { scale: 0.97 }}
      transition={{ duration: 0.1, ease }}
      className={combinedClasses}
      {...(props as any)}
    >
      {content}
    </motion.button>
  );
};
