import React, { useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ArrowUpRight } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { RevealText } from './motion/RevealText';
import { Magnetic } from './motion/Magnetic';
import { ease, spring } from '../lib/motion';

export const FounderNote: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [imageError, setImageError] = useState(false);

  const paragraphs = [
    "Every week, another technology giant announces that their AI needs direct access to all your messages, browsing history, and personal notes to be useful. In return, you receive monthly subscription invoices, server outages, and zero oversight over how your cognitive data is harvested.",
    "Modern mobile chipsets now pack specialized vector accelerators and fast unified memory capable of executing complex 4-bit foundation models right in your pocket. Yet the software ecosystem continues to treat flagship smartphones as dumb client terminals for distant cloud datacenters.",
    "I built BIT to prove a sovereign alternative: an autonomous, open-source AI assistant that executes locally in native C++, runs seamlessly in airplane mode, and never transmits a single byte off your device.",
  ];

  return (
    <section id="founder" className="w-full bg-white section-spacing border-t border-[var(--line)]">
      <div className="section-container">
        <div className="max-w-[640px] mx-auto text-left">
          
          {/* Eyebrow */}
          <div className="text-xs uppercase tracking-widest text-[#a3a3a3] font-medium mb-3">
            Founder Note
          </div>

          {/* Heading */}
          <RevealText
            text="Why I built BIT"
            as="h2"
            className="text-3xl sm:text-4xl font-semibold text-[#0a0a0a] tracking-[-0.035em] mb-8 leading-tight"
          />

          {/* Body Paragraphs revealing sequentially */}
          <div className="space-y-6 text-[17px] sm:text-[18px] text-[#44403C] leading-relaxed font-normal">
            {paragraphs.map((p, idx) => (
              <motion.p
                key={idx}
                initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.2 }}
                transition={{ duration: 0.55, delay: idx * 0.12, ease }}
              >
                {p}
              </motion.p>
            ))}
          </div>

          {/* Hairline Divider above Author Row */}
          <div className="h-[1px] bg-[var(--line)] my-10" aria-hidden="true" />

          {/* Author Row with Ring-Animated Avatar and Action Chips */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6">
            
            <div className="flex items-center gap-4">
              {/* Avatar with SVG animated border ring */}
              <div className="relative w-12 h-12 flex items-center justify-center shrink-0">
                <svg className="absolute inset-0 w-12 h-12 -rotate-90 pointer-events-none" viewBox="0 0 48 48">
                  <motion.circle
                    cx="24"
                    cy="24"
                    r="22"
                    stroke="#0a0a0a"
                    strokeWidth="1.5"
                    fill="none"
                    initial={shouldReduceMotion ? false : { pathLength: 0 }}
                    whileInView={{ pathLength: 1 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.8, ease }}
                  />
                </svg>

                <div className="w-10 h-10 rounded-full overflow-hidden bg-[#fafafa] flex items-center justify-center text-xs font-semibold text-[#6b6b6b]">
                  {!imageError ? (
                    <img
                      src="https://github.com/jaswanthsanjay88.png"
                      alt="Jaswanth Sanjay"
                      className="w-full h-full object-cover"
                      onError={() => setImageError(true)}
                    />
                  ) : (
                    <span>JS</span>
                  )}
                </div>
              </div>

              <div>
                <div className="font-semibold text-[#0a0a0a] text-base leading-tight">
                  Jaswanth Sanjay
                </div>
                <div className="text-sm text-[#a3a3a3]">
                  Creator of BIT &bull; Systems & ML Engineer
                </div>
              </div>
            </div>

            {/* Link Chips with Magnetic Wrap and Hover State */}
            <div className="flex items-center gap-3">
              <Magnetic maxDistance={4} radius={60}>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full border border-[var(--line-strong)] text-xs font-medium text-[#0a0a0a] hover:bg-[#fafafa] transition-colors focus-ring"
                >
                  <GithubIcon className="w-3.5 h-3.5 text-[#0a0a0a]" />
                  <span>Project GitHub</span>
                  <ArrowUpRight className="w-3 h-3 text-[#a3a3a3]" />
                </a>
              </Magnetic>

              <Magnetic maxDistance={4} radius={60}>
                <a
                  href="https://github.com/jaswanthsanjay88"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full border border-[var(--line)] text-xs font-medium text-[#6b6b6b] hover:text-[#0a0a0a] hover:bg-[#fafafa] transition-colors focus-ring"
                >
                  <span>Profile</span>
                  <ArrowUpRight className="w-3 h-3 text-[#a3a3a3]" />
                </a>
              </Magnetic>
            </div>

          </div>

        </div>
      </div>
    </section>
  );
};
