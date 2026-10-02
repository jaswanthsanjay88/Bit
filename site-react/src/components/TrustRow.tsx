import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { CountUp } from './motion/CountUp';
import { ease } from '../lib/motion';

interface StatItem {
  value: string;
  label: string;
  fromValue?: number;
}

const STATS: StatItem[] = [
  { value: '100%', label: 'Offline on device' },
  { value: '0B', label: 'Leaves your phone', fromValue: 9 },
  { value: '$0', label: 'Free and open source', fromValue: 99 },
  { value: '0', label: 'Telemetry sent', fromValue: 50 },
];

export const TrustRow: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <section className="relative w-full bg-white border-y border-[var(--line)] section-rhythm">
      <div className="container">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-y-12 lg:gap-y-0 relative">
          
          {STATS.map((stat, idx) => (
            <div
              key={idx}
              className="relative flex flex-col items-start text-left px-3 sm:px-6 lg:px-8 min-w-0"
            >
              {/* Vertical Hairline Divider between columns */}
              {idx > 0 && (
                <motion.div
                  initial={shouldReduceMotion ? { opacity: 1 } : { scaleY: 0 }}
                  whileInView={{ scaleY: 1 }}
                  viewport={{ once: true, amount: 0.6 }}
                  transition={{ duration: 0.7, delay: idx * 0.1, ease }}
                  style={{ transformOrigin: 'top center' }}
                  className={`absolute left-0 top-1 bottom-1 w-[1px] bg-[var(--line)] ${
                    idx % 2 === 1 ? 'block' : 'hidden lg:block'
                  }`}
                  aria-hidden="true"
                />
              )}

              {/* Display Size Ticker Number in Condensed Archivo */}
              <div className="stat-number text-[#0a0a0a] mb-3 select-none">
                <CountUp value={stat.value} duration={1.2} fromValue={stat.fromValue} />
              </div>

              {/* Quiet Label (>= 12px, >= #6b6b6b) */}
              <div className="text-[13px] sm:text-[14px] uppercase tracking-wider text-[#6b6b6b] font-medium leading-relaxed">
                {stat.label}
              </div>
            </div>
          ))}

        </div>
      </div>
    </section>
  );
};
