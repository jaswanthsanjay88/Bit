import React, { useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { BitLogo } from './BitLogo';
import { ease } from '../lib/motion';

export const Footer: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [hoveredLink, setHoveredLink] = useState<string | null>(null);

  const links = [
    { label: 'GitHub', href: 'https://github.com/jaswanthsanjay88/Bit_Android' },
    { label: 'Docs', href: 'https://github.com/jaswanthsanjay88/Bit_Android/blob/main/README.md' },
    { label: 'Privacy', href: 'https://github.com/jaswanthsanjay88/Bit_Android/blob/main/docs/SECURITY.md' },
    { label: 'License', href: 'https://github.com/jaswanthsanjay88/Bit_Android/blob/main/LICENSE' },
  ];

  return (
    <footer className="w-full border-t border-[var(--line)] bg-white py-12 sm:py-14">
      <div className="section-container flex flex-col sm:flex-row items-center justify-between gap-6 text-[14.5px] text-[#a3a3a3]">
        
        {/* Brand signature */}
        <div className="flex items-center gap-2.5 text-[#6b6b6b]">
          <div className="w-5 h-5 rounded bg-[#0a0a0a] flex items-center justify-center text-white">
            <BitLogo className="w-3 h-3 text-white" />
          </div>
          <span className="font-semibold text-[#0a0a0a]">BIT</span>
          <span>&mdash;</span>
          <span>Sovereign On-Device AI</span>
        </div>

        {/* Links with underline growing from left */}
        <div className="flex items-center gap-7">
          {links.map((link) => (
            <a
              key={link.label}
              href={link.href}
              target="_blank"
              rel="noreferrer"
              onMouseEnter={() => setHoveredLink(link.label)}
              onMouseLeave={() => setHoveredLink(null)}
              className="relative text-[#6b6b6b] hover:text-[#0a0a0a] transition-colors duration-150 py-1 focus-ring rounded"
            >
              <span>{link.label}</span>
              <motion.span
                animate={{
                  scaleX: hoveredLink === link.label && !shouldReduceMotion ? 1 : 0,
                }}
                transition={{ duration: 0.2, ease }}
                style={{ transformOrigin: '0% 50%' }}
                className="absolute left-0 right-0 bottom-0 h-[1px] bg-[#0a0a0a]"
                aria-hidden="true"
              />
            </a>
          ))}
        </div>

      </div>
    </footer>
  );
};
