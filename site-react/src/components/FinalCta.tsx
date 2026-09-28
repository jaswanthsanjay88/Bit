import React from 'react';
import { motion } from 'motion/react';
import { Download, ShieldCheck, ArrowRight, Smartphone } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { springs } from '../lib/motion';

export const FinalCta: React.FC = () => {
  return (
    <section className="py-32 md:py-44 bg-gradient-to-b from-[#0D1508] via-[#080E05] to-[#07080A] border-t border-[#B6FF3B]/20 relative overflow-hidden">
      {/* Soft Full-Bleed Lime Radial Glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[450px] bg-gradient-to-b from-[#B6FF3B]/15 to-transparent blur-3xl pointer-events-none rounded-full" />

      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
        <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#B6FF3B]/10 border border-[#B6FF3B]/30 text-xs font-mono text-[#B6FF3B] mb-8">
          <ShieldCheck className="w-4 h-4" />
          <span>Zero Telemetry &bull; 100% On-Device Sovereignty</span>
        </div>

        <h2 className="text-5xl sm:text-7xl md:text-8xl font-extrabold tracking-[-0.045em] text-white mb-6 leading-[0.98]">
          Reclaim your cognitive sovereignty.
        </h2>

        <p className="text-lg sm:text-xl text-zinc-300 max-w-2xl mx-auto mb-12 leading-relaxed font-normal">
          No monthly subscription. No cloud surveillance. No internet requirement. Install the sovereign AI agent that never leaves your phone.
        </p>

        {/* Big Buttons */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-6">
          <motion.a
            href="#terminal"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-10 py-4 h-14 rounded-2xl btn-accent text-base"
          >
            <Download className="w-5 h-5" />
            <span>Download APK (v2.1.1)</span>
          </motion.a>

          <motion.a
            href="https://github.com/jaswanthsanjay88/Bit_Android"
            target="_blank"
            rel="noreferrer"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-8 py-4 h-14 rounded-2xl btn-ghost text-base font-medium"
          >
            <GithubIcon className="w-5 h-5 text-zinc-400" />
            <span>View Source on GitHub</span>
            <span className="text-xs font-mono text-[#B6FF3B] ml-1">2.1k</span>
          </motion.a>
        </div>

        {/* Device Requirement Line right under button */}
        <div className="flex items-center justify-center gap-2 text-xs sm:text-sm font-mono text-zinc-400">
          <Smartphone className="w-4 h-4 text-[#B6FF3B]" />
          <span>Requires Android 12+ (API 31+) &bull; 64-bit ARM64 processor &bull; Free & Open Source</span>
        </div>
      </div>
    </section>
  );
};
