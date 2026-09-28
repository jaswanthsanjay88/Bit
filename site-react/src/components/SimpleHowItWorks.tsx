import React from 'react';
import { motion } from 'motion/react';
import { Download, HardDrive, WifiOff } from 'lucide-react';
import { springs } from '../lib/motion';

interface Step {
  numeral: string;
  title: string;
  description: string;
  icon: React.ReactNode;
}

const STEPS: Step[] = [
  {
    numeral: '01',
    title: 'Install the Release APK',
    description: 'Sideload the lightweight Android package directly via browser download or a single ADB terminal command.',
    icon: <Download className="w-5 h-5 text-[#B6FF3B]" />
  },
  {
    numeral: '02',
    title: 'Pick Verified Open Weights',
    description: 'Download verified GGUF weights directly from HuggingFace with automatic RAM safety verification for your silicon.',
    icon: <HardDrive className="w-5 h-5 text-[#B6FF3B]" />
  },
  {
    numeral: '03',
    title: 'Disconnect & Run Forever',
    description: 'Toggle airplane mode. Your autonomous agent runs locally forever with zero latency, zero cloud fees, and zero data leakage.',
    icon: <WifiOff className="w-5 h-5 text-[#B6FF3B]" />
  }
];

export const SimpleHowItWorks: React.FC = () => {
  return (
    <section id="how-it-works" className="py-28 md:py-36 bg-[#0D0F14] border-y border-white/[0.08] relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-20">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B] mb-4">
            <span>Simple 3-Step Setup</span>
          </div>
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-4">
            Up and running in 60 seconds.
          </h2>
          <p className="text-lg text-[#A1A1AA]">
            Zero accounts. Zero cloud API keys. Zero recurring subscription tollgates.
          </p>
        </div>

        {/* 3 Large Steps Connected by a Line */}
        <div className="relative">
          {/* Desktop Connecting Line behind cards */}
          <div className="hidden md:block absolute top-1/2 left-[15%] right-[15%] h-[2px] bg-gradient-to-r from-transparent via-[#B6FF3B]/30 to-transparent -translate-y-12 z-0 pointer-events-none" />

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 lg:gap-10 relative z-10">
            {STEPS.map((st, idx) => (
              <motion.div
                key={st.numeral}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ ...springs.snappy, delay: idx * 0.12 }}
                whileHover={{ y: -4 }}
                className="surface-2 rounded-2xl p-8 flex flex-col justify-between border border-white/10 hover:border-[#B6FF3B]/40 transition-all duration-200 cursor-default shadow-lg"
              >
                <div>
                  {/* Top Row: Big Numeral & Icon */}
                  <div className="flex items-center justify-between mb-8">
                    <span className="text-5xl sm:text-6xl font-extrabold tracking-tight text-[#B6FF3B] tabular-nums">
                      {st.numeral}
                    </span>
                    <div className="w-12 h-12 rounded-xl bg-[#07080A] border border-white/10 flex items-center justify-center shadow-inner">
                      {st.icon}
                    </div>
                  </div>

                  <h3 className="text-2xl font-bold text-white mb-3 tracking-tight">
                    {st.title}
                  </h3>

                  <p className="text-base text-[#A1A1AA] leading-relaxed font-normal">
                    {st.description}
                  </p>
                </div>

                <div className="pt-6 mt-8 border-t border-white/5 flex items-center justify-between text-xs font-mono text-zinc-500">
                  <span>Requirement</span>
                  <span className="text-zinc-300">Android 12+</span>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};
