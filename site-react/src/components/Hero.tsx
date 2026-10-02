import React, { useRef } from 'react';
import { motion, useScroll, useTransform, useReducedMotion } from 'motion/react';
import { Download } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { Button } from './ui/Button';
import { Phone } from './ui/Phone';
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

  // Phone parallax: moves slower than text as user scrolls
  const phoneY = useTransform(scrollYProgress, [0, 1], [0, 48]);
  const phoneScale = useTransform(scrollYProgress, [0, 0.8], [1, 0.97]);

  const renderInteractiveWords = (text: string) => {
    return text.split(' ').map((word, wordIdx, arr) => (
      <span key={wordIdx} className="inline-block whitespace-nowrap">
        {word.split('').map((char, charIdx) => (
          <span key={charIdx} className="letter-interactive select-none">
            {char}
          </span>
        ))}
        {wordIdx < arr.length - 1 && <span className="inline-block">&nbsp;</span>}
      </span>
    ));
  };

  return (
    <section
      ref={heroRef}
      className="relative w-full bg-white pt-28 sm:pt-36 md:pt-40 pb-0 overflow-hidden text-center"
    >
      <div className="container">
        
        {/* 1. Full-Width Poster Headline (Two Lines) */}
        <div className="w-full flex flex-col items-center justify-center relative z-10">
          
          {/* Line 1 */}
          <div className="overflow-hidden w-full flex justify-center">
            <motion.div
              initial={shouldReduceMotion ? false : { y: '110%' }}
              animate={{ y: '0%' }}
              transition={{ duration: 0.85, delay: 0.1, ease }}
              className="display tracking-[-0.02em] text-[#0a0a0a] text-center"
            >
              {renderInteractiveWords('AI THAT NEVER')}
            </motion.div>
          </div>

          {/* Line 2 */}
          <div className="overflow-hidden w-full flex justify-center mt-1 sm:mt-2">
            <motion.div
              initial={shouldReduceMotion ? false : { y: '110%' }}
              animate={{ y: '0%' }}
              transition={{ duration: 0.85, delay: 0.18, ease }}
              className="display tracking-[-0.02em] text-[#0a0a0a] text-center"
            >
              {renderInteractiveWords('LEAVES YOUR PHONE.')}
            </motion.div>
          </div>

        </div>

        {/* 2. One-Line Subtext bumped to ~1.25rem */}
        <motion.p
          initial={shouldReduceMotion ? false : { opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.35, ease }}
          className="text-[1.2rem] sm:text-[1.3rem] text-[#6b6b6b] leading-relaxed max-w-2xl mx-auto mt-6 sm:mt-8 mb-8 sm:mb-10 text-balance font-normal"
        >
          Private AI agents, document search and voice, running entirely on your phone. No cloud, no subscription.
        </motion.p>

        {/* 3. Action Buttons (Directly below Subtext, before Phone) */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.45, ease }}
          className="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4 mb-12 sm:mb-16 relative z-30"
        >
          <Magnetic maxDistance={6} radius={70}>
            <Button
              variant="primary"
              size="md"
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
              size="md"
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              icon={<GithubIcon className="w-4 h-4 text-[#6b6b6b]" />}
              className="w-full sm:w-auto"
            >
              GitHub
            </Button>
          </Magnetic>
        </motion.div>

        {/* 4. Rising Phone Mockup: cropped by the fold like ugly.cash */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, y: 80 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.9, delay: 0.55, ease }}
          style={shouldReduceMotion ? {} : { y: phoneY, scale: phoneScale }}
          className="relative flex justify-center items-center -mb-28 sm:-mb-36 md:-mb-44 z-20"
        >
          <Tilt maxTilt={4} className="relative">
            {/* Tilted Sticker that wobbles on hover - clear of text */}
            <div
              className="absolute -top-3 right-2 sm:-right-6 z-40 bg-[#0a0a0a] text-white text-[11px] sm:text-xs font-mono font-medium px-3 py-1.5 rounded-full border border-white/20 shadow-xl sticker-wobble -rotate-6 select-none cursor-pointer"
              title="100% Offline"
            >
              0 bytes leave your phone
            </div>

            <Phone
              variant="static"
              size="hero"
              priority={true}
              className="w-[280px] sm:w-[320px] md:w-[340px]"
            >
              <video
                src="/videos/hero_chat.mp4"
                poster="/img/real/chat_stream_result.png"
                autoPlay
                loop
                muted
                playsInline
                className="w-full h-full object-cover object-top"
              />
            </Phone>
          </Tilt>
        </motion.div>

      </div>
    </section>
  );
};
