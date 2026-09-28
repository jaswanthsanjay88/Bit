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
    { icon: <Cpu className="w-3.5 h-3.5 text-[#a3a3a3]" />, text: 'Requires Android 12+' },
    { text: 'ARM64-v8a Architecture' },
    { text: '4GB+ RAM Recommended' },
    { text: 'Apache 2.0 Open Source' },
  ];

  return (
    <section id="download" className="w-full bg-white section-spacing border-t border-[var(--line)]">
      <div className="section-container">
        
        {/* Dark Block with clip-path enter animation */}
        <motion.div
          initial={
            shouldReduceMotion
              ? { opacity: 0 }
              : {
                  clipPath: 'inset(6% 4% round 24px)',
                  opacity: 0,
                  y: 30,
                }
          }
          whileInView={{
            clipPath: 'inset(0% 0% round 24px)',
            opacity: 1,
            y: 0,
          }}
          viewport={{ once: true, amount: 0.2 }}
          transition={{ duration: 0.9, ease }}
          className="relative rounded-[24px] bg-[#0a0a0a] text-white py-16 sm:py-24 px-6 sm:px-12 lg:px-16 text-center overflow-hidden border border-white/10"
        >
          {/* Faint animated dot-grid drifting slowly */}
          <div className="absolute inset-0 bg-dot-drift opacity-[0.04] pointer-events-none" />

          {/* Headline */}
          <div className="relative z-10 max-w-3xl mx-auto mb-6">
            <RevealText
              text="Reclaim your cognitive sovereignty."
              as="h2"
              className="text-3xl sm:text-5xl lg:text-[54px] font-semibold text-white tracking-[-0.035em] leading-[1.08] text-balance"
            />
          </div>

          {/* Subtitle */}
          <div className="relative z-10 max-w-xl mx-auto mb-10">
            <Reveal delay={0.1}>
              <p className="text-[17px] sm:text-[18px] text-[#a3a3a3] leading-relaxed mx-auto text-pretty">
                No subscriptions. No cloud surveillance. No internet requirement. Install the sovereign AI assistant that never leaves your phone.
              </p>
            </Reveal>
          </div>

          {/* Action Buttons wrapped in Magnetic */}
          <div className="relative z-10 flex flex-col sm:flex-row items-center justify-center gap-4 mb-10">
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
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-7 py-3.5 h-12 rounded-full bg-[#141414] border border-white/15 text-white font-medium text-[15px] hover:bg-[#1a1a1a] transition-colors focus-ring"
              >
                <GithubIcon className="w-4 h-4 text-white shrink-0" />
                <span>View on GitHub</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-white/10 text-white/90 font-mono ml-1">
                  2.1k
                </span>
              </motion.a>
            </Magnetic>
          </div>

          {/* Staggered Requirement Chips */}
          <div className="relative z-10 flex flex-wrap items-center justify-center gap-x-4 gap-y-2 text-xs text-[#a3a3a3] border-t border-white/10 pt-8 max-w-2xl mx-auto">
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

        </motion.div>

      </div>
    </section>
  );
};
