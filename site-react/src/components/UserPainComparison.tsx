import React, { useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { RevealText } from './motion/RevealText';
import { Reveal } from './motion/Reveal';
import { ease, spring } from '../lib/motion';

interface ComparisonRow {
  dimension: string;
  cloudText: string;
  bitText: string;
  strikethrough?: boolean;
}

const COMPARISON_ROWS: ComparisonRow[] = [
  {
    dimension: 'Privacy',
    cloudText: 'Transfers prompts, telemetry, and private files to cloud servers',
    bitText: '0 bytes leave your phone. All data encrypted in local vault.',
  },
  {
    dimension: 'Offline',
    cloudText: 'Zero functionality in airplane mode, transit, or remote areas',
    bitText: '100% functional offline with zero internet connection.',
  },
  {
    dimension: 'Reliability',
    cloudText: 'Subject to cloud outages, server throttling, and latency spikes',
    bitText: 'Deterministic local execution with consistent millisecond response.',
  },
  {
    dimension: 'Cost',
    cloudText: '$20+/month recurring subscription or perpetual API token fees',
    bitText: '$0 forever. Free and open source under Apache-2.0.',
    strikethrough: true,
  },
];

export const UserPainComparison: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  return (
    <section id="comparison" className="w-full bg-white section-rhythm border-t border-[var(--line)]">
      <div className="container">
        
        {/* Section Header: Centered title and content */}
        <div className="text-center max-w-2xl mx-auto mb-16">
          <RevealText
            text="Cloud vs. On-Device"
            as="h2"
            className="section-title text-[#0a0a0a] mb-6 text-balance text-center"
          />
          <Reveal delay={0.1}>
            <p className="text-[17px] text-[#6b6b6b] leading-relaxed mx-auto text-pretty">
              Why sovereign hardware-local intelligence outperforms centralized server APIs.
            </p>
          </Reveal>
        </div>

        {/* Two-Column Comparison Table with Hairline Rows spanning full container */}
        <div className="w-full overflow-hidden border-t border-[var(--line)]">
          
          {/* Table Header */}
          <div className="grid grid-cols-1 md:grid-cols-2 pb-4 pt-2 border-b border-[var(--line)]">
            <div className="px-5 sm:px-8 py-2 text-xs uppercase tracking-wider text-[#6b6b6b] font-medium font-mono">
              Centralized Cloud
            </div>
            <div className="px-5 sm:px-8 py-2 text-xs uppercase tracking-wider text-[#0a0a0a] font-semibold flex items-center justify-between font-mono">
              <span>Sovereign Local (BIT)</span>
              <span className="text-[12px] text-[#6b6b6b]">100% Offline</span>
            </div>
          </div>

          {/* Hairline Rows with equal row heights */}
          <div className="divide-y divide-[var(--line)] border-b border-[var(--line)]">
            {COMPARISON_ROWS.map((row, idx) => {
              const isHovered = hoveredIdx === idx;

              return (
                <motion.div
                  key={idx}
                  initial={shouldReduceMotion ? false : { opacity: 0, y: 16 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, amount: 0.3 }}
                  transition={{ duration: 0.5, delay: idx * 0.09, ease }}
                  onMouseEnter={() => setHoveredIdx(idx)}
                  onMouseLeave={() => setHoveredIdx(null)}
                  className="grid grid-cols-1 md:grid-cols-2 relative group items-stretch"
                >
                  {/* Left Column: Cloud (text color >= #6b6b6b, no mono meta line) */}
                  <div className="p-5 sm:p-8 flex flex-col justify-start text-left">
                    <div className="text-xs uppercase tracking-wider text-[#6b6b6b] font-medium mb-2">
                      {row.dimension}
                    </div>
                    <div className="flex items-start gap-3">
                      <span className="text-[#6b6b6b] text-sm font-mono mt-0.5 shrink-0 opacity-80">
                        ✕
                      </span>
                      <div className="text-[16px] text-[#6b6b6b] leading-relaxed">
                        {row.strikethrough ? (
                          <span className="relative inline-block">
                            <span>{row.cloudText}</span>
                            <motion.span
                              initial={shouldReduceMotion ? { scaleX: 1 } : { scaleX: 0 }}
                              whileInView={{ scaleX: 1 }}
                              viewport={{ once: true }}
                              transition={{ duration: 0.6, delay: 0.4, ease }}
                              style={{ transformOrigin: '0% 50%' }}
                              className="absolute left-0 right-0 top-1/2 h-[1px] bg-[#6b6b6b]"
                            />
                          </span>
                        ) : (
                          row.cloudText
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right Column: BIT on Android (no mono meta line, shared row height) */}
                  <div
                    className={`p-5 sm:p-8 flex flex-col justify-start text-left md:border-l border-[var(--line)] relative transition-colors duration-200 ${
                      isHovered ? 'bg-[#fafafa]' : 'bg-transparent'
                    }`}
                  >
                    {/* Active Left Ink Bar on Hover */}
                    <motion.div
                      animate={{ scaleY: isHovered ? 1 : 0 }}
                      transition={spring}
                      style={{ transformOrigin: 'top center' }}
                      className="hidden md:block absolute left-[-1px] top-0 bottom-0 w-[2px] bg-[#0a0a0a]"
                      aria-hidden="true"
                    />

                    <div className="text-xs uppercase tracking-wider text-[#0a0a0a] font-semibold mb-2">
                      {row.dimension}
                    </div>

                    <div className="flex items-start gap-3">
                      {/* Checkmark SVG */}
                      <svg
                        className="w-4 h-4 text-[#0a0a0a] mt-1 shrink-0"
                        viewBox="0 0 16 16"
                        fill="none"
                      >
                        <motion.path
                          d="M3 8.5L6.5 12L13 4"
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          initial={shouldReduceMotion ? false : { pathLength: 0 }}
                          whileInView={{ pathLength: 1 }}
                          viewport={{ once: true }}
                          transition={{ duration: 0.45, delay: 0.2 + idx * 0.1, ease }}
                        />
                      </svg>

                      <div className="text-[16px] sm:text-[17px] font-medium text-[#0a0a0a] leading-relaxed">
                        {row.bitText}
                      </div>
                    </div>
                  </div>

                </motion.div>
              );
            })}
          </div>

        </div>

      </div>
    </section>
  );
};
