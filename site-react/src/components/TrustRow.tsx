import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { CountUp } from './motion/CountUp';
import { ease } from '../lib/motion';

interface StatItem {
  value: string;
  label: string;
  sublabel?: string;
  roll?: boolean;
}

const STATS: StatItem[] = [
  { value: '100%', label: 'Offline on device' },
  { value: '0B', label: 'Leaves your phone', roll: true },
  { value: '$0', label: 'Free and open source', roll: true },
  { value: '2.1k', label: 'GitHub stars' },
];

export const TrustRow: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <section className="relative w-full bg-white border-y border-[var(--line)] py-16 sm:py-20 lg:py-24">
      <div className="section-container">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-y-10 md:gap-y-0 relative">
          
          {STATS.map((stat, idx) => (
            <div
              key={idx}
              className="relative flex flex-col items-center md:items-start text-center md:text-left px-4 sm:px-6 lg:px-8"
            >
              {/* Vertical Hairline Divider between columns (draws from top to bottom) */}
              {idx > 0 && (
                <motion.div
                  initial={shouldReduceMotion ? { opacity: 1 } : { scaleY: 0 }}
                  whileInView={{ scaleY: 1 }}
                  viewport={{ once: true, amount: 0.6 }}
                  transition={{ duration: 0.7, delay: idx * 0.1, ease }}
                  style={{ transformOrigin: 'top center' }}
                  className="hidden md:block absolute left-0 top-1 bottom-1 w-[1px] bg-[var(--line)]"
                  aria-hidden="true"
                />
              )}

              {/* Stat Number */}
              <div className="text-5xl sm:text-6xl lg:text-[72px] font-medium tracking-tight text-[#0a0a0a] leading-none mb-3 tabular-nums">
                <CountUp value={stat.value} duration={1.2} />
              </div>

              {/* Label */}
              <div className="text-xs uppercase tracking-wider text-[#a3a3a3] font-medium leading-relaxed">
                {stat.label}
              </div>
            </div>
          ))}

        </div>
      </div>
    </section>
  );
};
