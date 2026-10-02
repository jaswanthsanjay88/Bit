import React from 'react';
import { motion } from 'motion/react';
import { springs } from '../lib/motion';

interface StatItem {
  value: string;
  label: string;
  detail: string;
}

const STATS: StatItem[] = [
  {
    value: '100%',
    label: 'Zero Network Egress',
    detail: 'Fully autonomous in airplane mode with zero external pinging.'
  },
  {
    value: '0 Bytes',
    label: 'Data Leaves Device',
    detail: 'Encrypted local SQLite storage with zero cloud telemetry.'
  },
  {
    value: '45+ t/s',
    label: 'Inference Throughput',
    detail: 'ARM64 Neon SIMD and Qualcomm Hexagon NPU accelerated.'
  },
  {
    value: 'Apache 2.0',
    label: 'Open Source',
    detail: 'Fully transparent code auditable and inspectable by everyone.'
  }
];

export const StatsStrip: React.FC = () => {
  return (
    <section className="border-y border-white/[0.08] bg-[#07080A] py-16 relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 lg:gap-10">
          {STATS.map((stat, idx) => (
            <motion.div
              key={idx}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ ...springs.snappy, delay: idx * 0.08 }}
              className="space-y-2 cursor-default"
            >
              {/* Big Accent Number */}
              <div className="text-4xl sm:text-5xl font-extrabold tracking-tight text-[#B6FF3B] tabular-nums">
                {stat.value}
              </div>

              {/* Caption Headline */}
              <div className="text-base font-bold text-white tracking-tight">
                {stat.label}
              </div>

              {/* Supporting Detail (min 15px) */}
              <p className="text-[15px] sm:text-base text-[#A1A1AA] leading-relaxed font-normal">
                {stat.detail}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};
