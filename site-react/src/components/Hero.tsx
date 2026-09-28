import React, { useRef } from 'react';
import { motion, useScroll, useTransform, useReducedMotion } from 'motion/react';
import { Download } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { Button } from './ui/Button';
import { Phone } from './ui/Phone';
import { LocalRagChatScreen } from './LocalRagChatScreen';
import { RevealText } from './motion/RevealText';
import { Magnetic } from './motion/Magnetic';
import { CountUp } from './motion/CountUp';
import { Tilt } from './motion/Tilt';
import { ease } from '../lib/motion';

export const Hero: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const heroRef = useRef<HTMLElement>(null);

  const { scrollYProgress } = useScroll({
    target: heroRef,
    offset: ['start start', 'end start'],
  });

  // H1 fades slightly and shifts up as user scrolls past hero
  const h1Opacity = useTransform(scrollYProgress, [0, 0.4], [1, 0.4]);
  const h1Y = useTransform(scrollYProgress, [0, 0.4], [0, -24]);

  // Phone parallax: moves up slower than page and scales 1 -> 0.96 as hero leaves
  const phoneY = useTransform(scrollYProgress, [0, 1], [0, 48]);
  const phoneScale = useTransform(scrollYProgress, [0, 0.8], [1, 0.96]);

  return (
    <section
      ref={heroRef}
      className="relative w-full bg-white pt-32 sm:pt-40 md:pt-44 pb-12 sm:pb-16 overflow-hidden text-center"
    >
      <div className="section-container">
        
        {/* Header Block */}
        <motion.div
          style={shouldReduceMotion ? {} : { opacity: h1Opacity, y: h1Y }}
          className="max-w-4xl mx-auto flex flex-col items-center"
        >
          {/* Main H1: Words rise via RevealText with 40ms stagger */}
          <h1 className="text-4xl sm:text-6xl md:text-7xl lg:text-[84px] font-semibold text-[#0a0a0a] tracking-[-0.04em] leading-[1.02] mb-6 text-balance">
            <RevealText
              text="AI that never leaves your phone."
              as="span"
              delay={0.1}
              staggerDelay={0.04}
            />
          </h1>

          {/* Subtitle: Fades up at 450ms */}
          <motion.p
            initial={shouldReduceMotion ? false : { opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.45, ease }}
            className="text-[17px] sm:text-[19px] md:text-[20px] text-[#6b6b6b] max-w-xl mx-auto mb-9 leading-relaxed font-normal text-pretty"
          >
            Private AI agents, document search and voice, running entirely on your phone. No cloud, no subscription.
          </motion.p>

          {/* Action Row: Buttons fade up at 550ms */}
          <motion.div
            initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, delay: 0.55, ease }}
            className="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4 mb-10"
          >
            <Magnetic maxDistance={6} radius={70}>
              <Button
                variant="primary"
                size="lg"
                href="#download"
                icon={<Download className="w-4 h-4" />}
                className="w-full sm:w-auto"
              >
                Download APK
              </Button>
            </Magnetic>

            <Magnetic maxDistance={6} radius={70}>
              <Button
                variant="ghost"
                size="lg"
                href="https://github.com/jaswanthsanjay88/Bit_Android"
                target="_blank"
                rel="noreferrer"
                icon={<GithubIcon className="w-4 h-4 text-[#6b6b6b]" />}
                badge={
                  <span className="text-xs bg-[#fafafa] text-[#6b6b6b] border border-[var(--line)] px-2 py-0.5 rounded-full font-mono">
                    <CountUp value="2.1k" duration={1} fromValue={2.0} />
                  </span>
                }
                className="w-full sm:w-auto"
              >
                GitHub
              </Button>
            </Magnetic>
          </motion.div>
        </motion.div>

        {/* Hero Phone Mockup: slides up from y: 80 at 700ms */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, y: 80 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.9, delay: 0.7, ease }}
          style={shouldReduceMotion ? {} : { y: phoneY, scale: phoneScale }}
          className="relative flex justify-center items-center -mt-2 z-20"
        >
          <Tilt maxTilt={4}>
            <Phone
              variant="static"
              size="hero"
              priority={true}
              className="w-[280px] sm:w-[320px] md:w-[336px]"
            >
              <LocalRagChatScreen />
            </Phone>
          </Tilt>
        </motion.div>

      </div>
    </section>
  );
};
