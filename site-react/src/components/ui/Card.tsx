import React from 'react';

export interface CardProps {
  variant?: 'card' | 'alt' | 'white' | 'dark';
  radius?: 'xl' | '2xl' | '3xl';
  border?: boolean;
  className?: string;
  children: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({
  variant = 'card',
  radius = '3xl',
  border = true,
  className = '',
  children,
}) => {
  const radiusClasses = {
    xl: 'rounded-xl', // 12px
    '2xl': 'rounded-2xl', // 24px
    '3xl': 'rounded-3xl', // 32px
  }[radius];

  const variantClasses = {
    card: 'bg-[#FAFAF9] text-[#57534E]',
    alt: 'bg-[#F5F5F4] text-[#57534E]',
    white: 'bg-white text-[#57534E]',
    dark: 'bg-[#0A0A0A] text-white',
  }[variant];

  const borderClass = border
    ? variant === 'dark'
      ? 'border border-neutral-800'
      : 'border border-[#E7E5E4]'
    : '';

  return (
    <div className={`overflow-hidden transition-all duration-300 ${radiusClasses} ${variantClasses} ${borderClass} ${className}`}>
      {children}
    </div>
  );
};
