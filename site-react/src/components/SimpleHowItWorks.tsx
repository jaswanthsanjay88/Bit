import React from 'react';
import { motion } from 'motion/react';
import { Download, HardDrive, WifiOff, ArrowRight } from 'lucide-react';
import { springs } from '../lib/motion';

interface Step {
  number: string;
  title: string;
  description: string;
  icon: React.ReactNode;
}

const STEPS: Step[] = [
  {
    number: '01',
    title: 'Install the APK',
    description: 'Sideload the release APK directly to your phone via ADB or your browser in under 30 seconds.',
    icon: <Download className="w-5 h-5 text-zinc-300" />
  },
  {
    number: '02',
    title: 'Pick Your Weights',
    description: 'Download verified open-source GGUF weights directly from HuggingFace with automatic RAM safety checks.',
    icon: <HardDrive className="w-5 h-5 text-zinc-300" />
  },
  {
    number: '03',
    title: 'Disconnect & Run',
    description: 'Toggle airplane mode. Your autonomous agent runs forever with zero latency, zero cloud fees, and zero leaks.',
    icon: <WifiOff className="w-5 h-5 text-zinc-300" />
  }
];

export const SimpleHowItWorks: React.FC = () => {
  return (
    <section className="py-24 bg-zinc-950 border-t border-white/[0.08] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
            <span>Simple Setup</span>
          </div>
          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white mb-4">
            Get started in three steps.
          </h2>
          <p className="text-sm sm:text-base text-zinc-400">
            Zero accounts. Zero API keys. Zero subscription tollgates.
          </p>
        </div>

        {/* 3 Step Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {STEPS.map((st, idx) => (
            <motion.div
              key={st.number}
              whileHover={{ y: -3 }}
              transition={springs.snappy}
              className="p-8 rounded-2xl surface-card flex flex-col justify-between group cursor-default"
            >
              <div>
                <div className="flex items-center justify-between mb-6">
                  <span className="font-mono text-xs font-bold text-zinc-500 tabular-nums">
                    STEP {st.number}
                  </span>
                  <div className="w-10 h-10 rounded-xl bg-zinc-900 border border-white/10 flex items-center justify-center group-hover:border-white/20 transition-colors">
                    {st.icon}
                  </div>
                </div>

                <h3 className="text-xl font-bold text-white mb-3 tracking-tight">
                  {st.title}
                </h3>

                <p className="text-sm text-zinc-400 leading-relaxed font-normal">
                  {st.description}
                </p>
              </div>

              <div className="pt-6 mt-6 border-t border-white/5 flex items-center justify-between text-xs font-mono text-zinc-500">
                <span>Status:</span>
                <span className="text-zinc-300">Ready</span>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};
