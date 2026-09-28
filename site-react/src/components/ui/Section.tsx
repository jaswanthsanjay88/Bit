import React from 'react';

export interface SectionProps {
  id?: string;
  background?: 'white' | 'alt' | 'card' | 'dark';
  padding?: 'normal' | 'compact' | 'hero';
  className?: string;
  containerClassName?: string;
  children: React.ReactNode;
}

export const Section: React.FC<SectionProps> = ({
  id,
  background = 'white',
  padding = 'normal',
  className = '',
  containerClassName = '',
  children,
}) => {
  const bgClasses = {
    white: 'bg-white text-[#57534E]',
    alt: 'bg-[#F5F5F4] text-[#57534E]',
    card: 'bg-[#FAFAF9] text-[#57534E]',
    dark: 'bg-[#0A0A0A] text-neutral-300',
  }[background];

  const padClasses = {
    normal: 'py-18 sm:py-24 lg:py-32', // 72px mobile, 96px tablet, 128px desktop
    compact: 'py-12 sm:py-16 lg:py-20',
    hero: 'pt-28 pb-18 sm:pt-36 sm:pb-24 lg:pt-44 lg:pb-32',
  }[padding];

  return (
    <section
      id={id}
      className={`relative w-full overflow-hidden ${bgClasses} ${padClasses} ${className}`}
    >
      <div
        className={`max-w-[1120px] mx-auto px-5 sm:px-8 w-full relative z-10 ${containerClassName}`}
      >
        {children}
      </div>
    </section>
  );
};
