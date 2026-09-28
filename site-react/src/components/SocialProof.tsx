import React from 'react';
import { motion } from 'motion/react';
import { ShieldCheck, Cpu, GitBranch, Star } from 'lucide-react';
import { springs } from '../lib/motion';

export const SocialProof: React.FC = () => {
  return (
    <section className="border-b border-white/[0.08] bg-zinc-950/60 py-8 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 items-center justify-center text-center md:text-left">
          {/* Item 1 */}
          <div className="flex items-center justify-center md:justify-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-zinc-900 border border-white/10 flex items-center justify-center shrink-0">
              <Star className="w-4 h-4 text-zinc-300" />
            </div>
            <div>
              <div className="text-sm font-bold text-white tabular-nums">2,100+ Stars</div>
              <div className="text-[11px] font-mono text-zinc-500 uppercase tracking-wider">GitHub Community</div>
            </div>
          </div>

          {/* Item 2 */}
          <div className="flex items-center justify-center md:justify-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-zinc-900 border border-white/10 flex items-center justify-center shrink-0">
              <ShieldCheck className="w-4 h-4 text-zinc-300" />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Zero Telemetry</div>
              <div className="text-[11px] font-mono text-zinc-500 uppercase tracking-wider">Audited Codebase</div>
            </div>
          </div>

          {/* Item 3 */}
          <div className="flex items-center justify-center md:justify-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-zinc-900 border border-white/10 flex items-center justify-center shrink-0">
              <Cpu className="w-4 h-4 text-zinc-300" />
            </div>
            <div>
              <div className="text-sm font-bold text-white">llama.cpp Core</div>
              <div className="text-[11px] font-mono text-zinc-500 uppercase tracking-wider">Native C++ JNI</div>
            </div>
          </div>

          {/* Item 4 */}
          <div className="flex items-center justify-center md:justify-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-zinc-900 border border-white/10 flex items-center justify-center shrink-0">
              <GitBranch className="w-4 h-4 text-zinc-300" />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Apache-2.0</div>
              <div className="text-[11px] font-mono text-zinc-500 uppercase tracking-wider">Open Source Forever</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
