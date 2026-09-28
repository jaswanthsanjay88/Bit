import React from 'react';
import { motion } from 'motion/react';
import { Download, ShieldCheck, ArrowRight } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { springs } from '../lib/motion';

export const FinalCta: React.FC = () => {
  return (
    <section className="py-28 bg-black border-t border-white/[0.08] relative overflow-hidden">
      {/* Background radial glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[300px] bg-gradient-to-b from-zinc-800/20 via-zinc-900/10 to-transparent blur-3xl pointer-events-none" />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-white/10 text-xs font-mono text-zinc-300 mb-6">
          <ShieldCheck className="w-3.5 h-3.5 text-zinc-400" />
          <span>Zero Telemetry &bull; 100% On-Device</span>
        </div>

        <h2 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-white mb-6 leading-[1.06]">
          Reclaim your cognitive sovereignty.
        </h2>

        <p className="text-base sm:text-lg text-zinc-400 max-w-2xl mx-auto mb-10 leading-relaxed font-normal">
          No monthly subscription. No cloud surveillance. No internet requirement. Install the sovereign AI agent that never leaves your phone.
        </p>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <motion.a
            href="#terminal"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-8 py-4 rounded-xl bg-white text-black font-bold text-sm hover:bg-zinc-200 transition-all shadow-[0_0_30px_rgba(255,255,255,0.2)]"
          >
            <Download className="w-4 h-4" />
            <span>Download APK (v2.1.1)</span>
          </motion.a>

          <motion.a
            href="https://github.com/jaswanthsanjay88/Bit_Android"
            target="_blank"
            rel="noreferrer"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-6 py-4 rounded-xl bg-zinc-950 hover:bg-zinc-900 text-zinc-200 hover:text-white font-medium text-sm border border-white/10 hover:border-white/20 transition-all"
          >
            <GithubIcon className="w-4 h-4" />
            <span>View Source on GitHub</span>
            <span className="text-xs font-mono text-zinc-500 ml-1">2.1k</span>
          </motion.a>
        </div>
      </div>
    </section>
  );
};
