import React from 'react';

export interface PhoneProps {
  src?: string;
  alt?: string;
  variant?: 'center' | 'left' | 'right' | 'static';
  size?: 'sm' | 'md' | 'lg' | 'hero';
  className?: string;
  bezelColor?: string;
  priority?: boolean;
  children?: React.ReactNode;
}

export const Phone: React.FC<PhoneProps> = ({
  src,
  alt = 'BIT on Android',
  variant = 'center',
  size = 'hero',
  className = '',
  bezelColor = 'bg-[#0E1013] border-black/20 ring-white/10',
  priority = false,
  children,
}) => {
  const animClasses = {
    center: 'animate-phone-center',
    left: 'animate-phone-left',
    right: 'animate-phone-right',
    static: '',
  }[variant];

  const sizeClasses = {
    sm: 'w-[180px] sm:w-[230px] md:w-[260px]',
    md: 'w-[240px] sm:w-[290px] md:w-[320px]',
    lg: 'w-[280px] sm:w-[340px] md:w-[380px]',
    hero: 'w-[260px] sm:w-[310px] md:w-[330px]', // around 640px height with 9/19.5 aspect ratio
  }[size];

  return (
    <div className={`relative select-none ${animClasses} ${sizeClasses} ${className}`}>
      {/* 10px Bezel with rounded-[44px] and Wide Soft Shadow */}
      <div className={`relative rounded-[44px] p-[10px] border ring-1 shadow-[0_40px_80px_rgba(0,0,0,0.18)] transition-transform duration-300 ${bezelColor}`}>
        
        {/* Camera Pill / Punch-hole */}
        <div className="absolute top-3 left-1/2 -translate-x-1/2 z-30 w-3 h-3 rounded-full bg-black/95 ring-1 ring-white/10 pointer-events-none" />

        {/* Screen Container with Concentric rounded-[34px] (44px - 10px padding = 34px) */}
        <div className="relative w-full aspect-[9/19.5] rounded-[34px] overflow-hidden bg-black">
          {src ? (
            <img
              src={src}
              alt={alt}
              className="w-full h-full object-cover object-top img-outline"
              loading={priority ? 'eager' : 'lazy'}
            />
          ) : (
            children
          )}
        </div>
      </div>
    </div>
  );
};
