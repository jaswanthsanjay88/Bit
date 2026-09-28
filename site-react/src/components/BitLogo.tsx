import React from 'react';

interface BitLogoProps {
  className?: string;
  size?: number;
}

export const BitLogo: React.FC<BitLogoProps> = ({ className = 'w-5 h-5', size }) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 280 280"
      fill="currentColor"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
    >
      {/* Tall Left Bar: 77 x 280 */}
      <rect x="0" y="0" width="77" height="280" rx="4" />
      {/* Right Top Block: 153 x 128 */}
      <rect x="127" y="0" width="153" height="128" rx="4" />
      {/* Right Bottom Block: 153 x 128 */}
      <rect x="127" y="152" width="153" height="128" rx="4" />
    </svg>
  );
};
