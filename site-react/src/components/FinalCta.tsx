import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Download, Cpu } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { RevealText } from './motion/RevealText';
import { Reveal } from './motion/Reveal';
import { Magnetic } from './motion/Magnetic';
import { ease } from '../lib/motion';

export const FinalCta: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();

  const chips = [
    { icon: <Cpu className="w-4 h-4 text-zinc-400" />, text: 'Requires Android 12+' },
    { text: 'ARM64-v8a Architecture' },
    { text: '4GB+ RAM Recommended' },
    { text: 'Apache 2.0 Open Source' },
  ];

  return (
    <section id="download" className="w-full bg-[#0a0a0a] text-white section-rhythm relative overflow-hidden border-t border-white/10">
      {/* Faint animated dot-grid drifting slowly */}
      <div className="absolute inset-0 bg-dot-drift opacity-[0.04] pointer-events-none" />

      <div className="container relative z-10 text-center">
        {/* Headline: fits exactly 2 lines */}
        <div className="w-full max-w-5xl mx-auto mb-6">
          <RevealText
            text="Reclaim your"
            as="h2"
            className="section-title text-white block text-center"
            style={{ fontSize: 'clamp(2.75rem, 5.6vw, 5.25rem)', lineHeight: 0.92 }}
          />
          <RevealText
            text="cognitive sovereignty."
            as="h2"
            className="section-title text-white block text-center"
            style={{ fontSize: 'clamp(2.75rem, 5.6vw, 5.25rem)', lineHeight: 0.92 }}
            delay={0.06}
          />
        </div>

        {/* Subtitle: 24px below title, 32px (mb-8) above buttons */}
        <div className="max-w-xl mx-auto mb-8">
          <Reveal delay={0.1}>
            <p className="text-[17px] sm:text-[18px] text-zinc-400 leading-relaxed mx-auto text-pretty">
              No subscriptions. No cloud surveillance. No internet requirement. Install the sovereign AI assistant that never leaves your phone.
            </p>
          </Reveal>
        </div>

        {/* Action Buttons wrapped in Magnetic */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-10">
          <Magnetic maxDistance={6} radius={80}>
            <motion.a
              href="https://github.com/jaswanthsanjay88/Bit_Android/releases/latest"
              target="_blank"
              rel="noreferrer"
              whileHover={{ y: -1 }}
              whileTap={{ scale: 0.97 }}
              className="relative overflow-hidden w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-8 py-3.5 h-12 rounded-full bg-white text-[#0a0a0a] font-medium text-[15px] hover:bg-[#fafafa] transition-colors focus-ring"
            >
              {/* 5s subtle sheen sweep overlay */}
              {!shouldReduceMotion && (
                <span className="absolute inset-0 animate-sheen pointer-events-none" />
              )}
              <Download className="w-4 h-4 text-[#0a0a0a] shrink-0" />
              <span>Download APK (v2.1.1)</span>
            </motion.a>
          </Magnetic>

          <Magnetic maxDistance={6} radius={80}>
            <motion.a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              whileHover={{ y: -1 }}
              whileTap={{ scale: 0.97 }}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-7 py-3.5 h-12 rounded-full bg-[#18181b] border border-white/15 text-white font-medium text-[15px] hover:bg-[#27272a] transition-colors focus-ring"
            >
              <GithubIcon className="w-4 h-4 text-white shrink-0" />
              <span>View on GitHub</span>
            </motion.a>
          </Magnetic>
        </div>

        {/* Staggered Requirement Chips (>= 12px, >= #6b6b6b) */}
        <div className="flex flex-wrap items-center justify-center gap-x-4 gap-y-2 text-[13px] text-zinc-400 border-t border-white/10 pt-8 max-w-2xl mx-auto">
          {chips.map((chip, idx) => (
            <motion.div
              key={idx}
              initial={shouldReduceMotion ? false : { opacity: 0, y: 8 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.4, delay: 0.2 + idx * 0.08, ease }}
              className="inline-flex items-center gap-1.5"
            >
              {chip.icon}
              <span>{chip.text}</span>
              {idx < chips.length - 1 && (
                <span className="hidden sm:inline text-white/20 ml-2">&bull;</span>
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};
