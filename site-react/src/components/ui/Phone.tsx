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
    sm: 'w-[160px] sm:w-[185px] md:w-[205px]',
    md: 'w-[190px] sm:w-[220px] md:w-[240px]',
    lg: 'w-[220px] sm:w-[250px] md:w-[265px]',
    hero: 'w-[230px] sm:w-[260px] md:w-[280px]',
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
            <div className="absolute inset-0 w-full h-full">{children}</div>
          )}
        </div>
      </div>
    </div>
  );
};
