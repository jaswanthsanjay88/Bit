import React, { useRef, useState } from 'react';
import { motion, useScroll, useSpring, useTransform, useReducedMotion } from 'motion/react';
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
  const sectionRef = useRef<HTMLElement>(null);
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start center', 'end center'],
  });

  const smoothProgress = useSpring(scrollYProgress, {
    stiffness: 300,
    damping: 35,
  });

  return (
    <section
      ref={sectionRef}
      id="how-it-works"
      className="w-full bg-white section-spacing border-t border-[var(--line)]"
    >
      <div className="section-container">
        
        {/* Section Heading */}
        <div className="text-center max-w-2xl mx-auto mb-20 sm:mb-24">
          <div className="text-xs uppercase tracking-widest text-[#a3a3a3] font-medium mb-3">
            Workflow
          </div>
          <RevealText
            text="How it works"
            as="h2"
            className="text-3xl sm:text-4xl lg:text-[46px] font-semibold text-[#0a0a0a] tracking-[-0.035em] leading-[1.08] mb-4 text-balance"
          />
          <Reveal delay={0.1}>
            <p className="text-[17px] text-[#6b6b6b] leading-relaxed mx-auto text-pretty">
              Three steps from download to 100% offline sovereign intelligence.
            </p>
          </Reveal>
        </div>

        {/* 3 Step Flow with Scroll Connector */}
        <div className="relative">
          
          {/* Base Horizontal hairline connector */}
          <div
            className="hidden md:block absolute top-[44px] left-[10%] right-[10%] h-[1px] bg-[var(--line)] z-0"
            aria-hidden="true"
          />

          {/* Active progress hairline connector driven by scroll */}
          {!shouldReduceMotion && (
            <motion.div
              style={{ scaleX: smoothProgress, transformOrigin: '0% 50%' }}
              className="hidden md:block absolute top-[44px] left-[10%] right-[10%] h-[1.5px] bg-[#0a0a0a] z-0"
              aria-hidden="true"
            />
          )}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-12 sm:gap-10 relative z-10">
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
                  {/* Outline Numeral that nudges up on hover */}
                  <motion.div
                    animate={{
                      y: isHovered && !shouldReduceMotion ? -4 : 0,
                    }}
                    transition={spring}
                    className="text-7xl sm:text-8xl font-bold tracking-tighter leading-none mb-6 select-none text-numeral-outline group-hover:text-numeral-solid transition-colors duration-300"
                  >
                    {step.numeral}
                  </motion.div>

                  {/* Step Title with growing underline on hover */}
                  <div className="relative mb-3">
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

      </div>
    </section>
  );
};
