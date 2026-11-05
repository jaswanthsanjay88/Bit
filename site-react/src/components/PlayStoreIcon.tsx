import React from 'react';

export const PlayStoreIcon: React.FC<{ className?: string }> = ({ className = 'w-4 h-4' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M3.609 1.814L13.792 12 3.61 22.186A2.296 2.296 0 0 1 3 20.563V3.437c0-.64.228-1.226.609-1.623z"
      fill="#2196F3"
    />
    <path
      d="M17.208 8.583L5.394.708C4.787.303 4.137.172 3.609.308l10.183 10.183 3.416-1.908z"
      fill="#FFC107"
    />
    <path
      d="M3.609 23.692c.528.136 1.178.005 1.785-.4l11.814-7.875-3.416-1.909L3.609 23.692z"
      fill="#F44336"
    />
    <path
      d="M21.528 11.458l-4.32-2.875-3.416 3.417 3.416 3.417 4.32-2.875c.983-.655.983-1.429 0-2.084z"
      fill="#4CAF50"
    />
  </svg>
);
