import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Reveal } from '../motion/Reveal';
import { RevealText } from '../motion/RevealText';
import { Tilt } from '../motion/Tilt';
import { Parallax } from '../motion/Parallax';
import { Spotlight } from '../motion/Spotlight';
import { ease, spring } from '../../lib/motion';

export interface FeatureProps {
  id?: string;
  eyebrow?: string;
  title: string;
  description: string;
  bullets: string[];
  phone: React.ReactNode;
  side?: 'left' | 'right'; // side for the text on desktop
  showDivider?: boolean;
}

export const Feature: React.FC<FeatureProps> = ({
  id,
  eyebrow,
  title,
  description,
  bullets,
  phone,
  side = 'left',
  showDivider = true,
}) => {
  const shouldReduceMotion = useReducedMotion();
  const isTextLeft = side === 'left';

  return (
    <div id={id} className="relative w-full">
      <div className="section-container py-20 sm:py-28 lg:py-32">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Text Column (5 cols on lg) */}
          <div
            className={`lg:col-span-5 flex flex-col justify-center ${
              isTextLeft ? 'lg:order-1' : 'lg:order-2'
            }`}
          >
            <Spotlight className="p-4 -m-4 rounded-2xl">
              {eyebrow && (
                <div className="text-xs uppercase tracking-widest text-[#a3a3a3] font-medium mb-3">
                  {eyebrow}
                </div>
              )}

              <RevealText
                text={title}
                as="h2"
                className="text-3xl sm:text-4xl lg:text-[44px] font-semibold text-[#0a0a0a] tracking-[-0.035em] leading-[1.08] mb-5 text-balance"
              />

              <Reveal delay={0.08}>
                <p className="text-[17px] text-[#6b6b6b] leading-relaxed mb-8">
                  {description}
                </p>
              </Reveal>

              <div className="space-y-3.5">
                {bullets.map((bullet, idx) => (
                  <Reveal key={idx} delay={0.15 + idx * 0.08} className="flex items-start gap-3">
                    <motion.span
                      initial={shouldReduceMotion ? { opacity: 1 } : { scale: 0, opacity: 0 }}
                      whileInView={{ scale: 1, opacity: 1 }}
                      viewport={{ once: true }}
                      transition={{ ...spring, delay: 0.2 + idx * 0.08 }}
                      className="w-1.5 h-1.5 rounded-full bg-[#0a0a0a] shrink-0 mt-2"
                      aria-hidden="true"
                    />
                    <span className="text-[16px] sm:text-[17px] text-[#44403C] leading-snug">
                      {bullet}
                    </span>
                  </Reveal>
                ))}
              </div>
            </Spotlight>
          </div>

          {/* Device Column (7 cols on lg) */}
          <div
            className={`lg:col-span-7 flex justify-center items-center ${
              isTextLeft ? 'lg:order-2' : 'lg:order-1'
            }`}
          >
            <Tilt maxTilt={4} className="w-full flex justify-center">
              <Parallax offset={20} className="w-full flex justify-center">
                <motion.div
                  initial={
                    shouldReduceMotion
                      ? { opacity: 0 }
                      : { opacity: 0, y: 50, rotate: isTextLeft ? 1.5 : -1.5 }
                  }
                  whileInView={{ opacity: 1, y: 0, rotate: 0 }}
                  viewport={{ once: true, margin: '-10% 0px' }}
                  transition={{ duration: 0.85, ease }}
                  className="w-full flex justify-center"
                >
                  {phone}
                </motion.div>
              </Parallax>
            </Tilt>
          </div>

        </div>
      </div>

      {/* Hairline Divider drawing from left to right */}
      {showDivider && (
        <div className="section-container">
          <motion.div
            initial={shouldReduceMotion ? { opacity: 1 } : { scaleX: 0 }}
            whileInView={{ scaleX: 1 }}
            viewport={{ once: true, margin: '-5% 0px' }}
            transition={{ duration: 0.8, ease }}
            style={{ transformOrigin: '0% 50%' }}
            className="h-[1px] bg-[var(--line)] w-full"
            aria-hidden="true"
          />
        </div>
      )}
    </div>
  );
};
