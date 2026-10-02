import React, { useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { RevealText } from './motion/RevealText';
import { Reveal } from './motion/Reveal';
import { ease, spring } from '../lib/motion';

interface Step {
  numeral: string;
  headline: string;
  description: string;
}

const STEPS: Step[] = [
  {
    numeral: '01',
    headline: 'Install the APK',
    description: 'Download the signed release directly from GitHub with zero Play Store or account requirements.',
  },
  {
    numeral: '02',
    headline: 'Pick a model',
    description: 'Select an optimized GGUF quantized model tailored for your phone’s available RAM.',
  },
  {
    numeral: '03',
    headline: 'Turn on airplane mode and go',
    description: 'Cut all network connections. Your assistant, documents, and voice run 100% locally.',
  },
];

export const SimpleHowItWorks: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  return (
    <section
      id="how-it-works"
      className="w-full bg-white section-rhythm border-t border-[var(--line)]"
    >
      <div className="container">
        
        {/* Section Heading: Centered title and content */}
        <div className="text-center max-w-2xl mx-auto mb-16">
          <RevealText
            text="How it works"
            as="h2"
            className="section-title text-[#0a0a0a] mb-6 text-balance text-center"
          />
          <Reveal delay={0.1}>
            <p className="text-[17px] text-[#6b6b6b] leading-relaxed mx-auto text-pretty">
              Three steps from download to 100% offline sovereign intelligence.
            </p>
          </Reveal>
        </div>

        {/* 3 Equal Columns Spanning Container without awkward connector through '02' */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 sm:gap-10">
          {STEPS.map((step, idx) => {
            const isHovered = hoveredIdx === idx;

            return (
              <motion.div
                key={idx}
                initial={shouldReduceMotion ? false : { opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.2 }}
                transition={{ duration: 0.5, delay: idx * 0.12, ease }}
                onMouseEnter={() => setHoveredIdx(idx)}
                onMouseLeave={() => setHoveredIdx(null)}
                className="flex flex-col items-start text-left cursor-default group"
              >
                {/* Numeral: left-aligned with step text */}
                <motion.div
                  animate={{
                    y: isHovered && !shouldReduceMotion ? -4 : 0,
                  }}
                  transition={spring}
                  style={{
                    fontFamily: 'var(--font-archivo), system-ui, sans-serif',
                    fontStretch: '64%',
                    fontWeight: 800,
                    letterSpacing: '-0.04em',
                  }}
                  className="text-6xl sm:text-7xl leading-none mb-6 select-none text-[#d4d4d4] group-hover:text-[#0a0a0a] transition-colors duration-300"
                >
                  {step.numeral}
                </motion.div>

                {/* Step Title with growing underline on hover */}
                <div className="relative mb-4">
                  <h3 className="text-xl sm:text-2xl font-semibold text-[#0a0a0a] tracking-tight">
                    {step.headline}
                  </h3>
                  <motion.div
                    animate={{ scaleX: isHovered && !shouldReduceMotion ? 1 : 0 }}
                    transition={{ duration: 0.25, ease }}
                    style={{ transformOrigin: '0% 50%' }}
                    className="h-[1.5px] bg-[#0a0a0a] w-full mt-1"
                    aria-hidden="true"
                  />
                </div>

                {/* Step Description */}
                <p className="text-[16px] sm:text-[17px] text-[#6b6b6b] leading-relaxed">
                  {step.description}
                </p>
              </motion.div>
            );
          })}
        </div>

      </div>
    </section>
  );
};
