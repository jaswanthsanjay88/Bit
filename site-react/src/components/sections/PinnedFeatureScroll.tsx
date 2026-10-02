import React, { useRef, useState } from 'react';
import { motion, AnimatePresence, useScroll, useMotionValueEvent, useReducedMotion } from 'motion/react';
import { Phone } from '../ui/Phone';
import { Tilt } from '../motion/Tilt';
import { ease } from '../../lib/motion';

interface FeatureStep {
  id: string;
  stepNum: string;
  eyebrow: string;
  title: string;
  description: string;
  bullets: string[];
  screenName: string;
}

const FEATURE_STEPS: FeatureStep[] = [
  {
    id: 'agents',
    stepNum: '01',
    eyebrow: 'Autonomous Agents',
    title: 'Agents that act on your phone',
    description: 'Subagents decompose complex goals, execute local device tools, and run deterministic tasks with zero cloud reliance.',
    bullets: [
      'GBNF context-free grammars guarantee valid tool call outputs',
      'Multi-step agent planning executed in private on-device sandbox',
    ],
    screenName: 'Agent Tool Chat',
  },
  {
    id: 'voice',
    stepNum: '02',
    eyebrow: 'Offline Audio',
    title: 'Talk naturally, even in airplane mode',
    description: "Real-time neural speech recognition and voice synthesis run directly on your phone's processor without sending audio to any server.",
    bullets: [
      'Sherpa-ONNX streaming Whisper for instantaneous voice transcription',
      'On-device Piper neural TTS for responsive conversational dialogue',
    ],
    screenName: 'Live Voice Mode',
  },
  {
    id: 'vault',
    stepNum: '03',
    eyebrow: 'Private Retrieval',
    title: 'Your documents stay private',
    description: 'Index contracts, notes, and personal PDFs into an encrypted on-device vector vault for zero-leak retrieval and semantic search.',
    bullets: [
      'Local 384-dimensional BGE vector embeddings generated on device',
      'Encrypted on-device SQLite database with zero external telemetry',
    ],
    screenName: 'Memory Vault',
  },
];

export const PinnedFeatureScroll: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const shouldReduceMotion = useReducedMotion();
  const [activeStep, setActiveStep] = useState(0);

  // Track scroll position through the multi-screen container
  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ['start start', 'end end'],
  });

  useMotionValueEvent(scrollYProgress, 'change', (latest) => {
    if (typeof window !== 'undefined' && window.innerWidth >= 1024) {
      if (latest < 0.33) {
        setActiveStep(0);
      } else if (latest >= 0.33 && latest < 0.66) {
        setActiveStep(1);
      } else {
        setActiveStep(2);
      }
    }
  });

  const handleStepClick = (idx: number) => {
    setActiveStep(idx);
    if (containerRef.current && typeof window !== 'undefined' && window.innerWidth >= 1024) {
      const rect = containerRef.current.getBoundingClientRect();
      const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
      const containerTop = rect.top + scrollTop;
      const totalScrollable = containerRef.current.offsetHeight - window.innerHeight;
      const targetProgress = idx === 0 ? 0.05 : idx === 1 ? 0.5 : 0.95;
      const targetScroll = containerTop + targetProgress * totalScrollable;
      window.scrollTo({ top: targetScroll, behavior: 'smooth' });
    }
  };

  const current = FEATURE_STEPS[activeStep];

  return (
    <section
      ref={containerRef}
      id="features"
      className="relative w-full bg-white lg:h-[240vh] py-16 sm:py-20 lg:py-0"
    >
      {/* Sticky Full-Viewport Container on Desktop: 100vh height */}
      <div className="lg:sticky lg:top-0 lg:h-screen flex items-center justify-center">
        <div className="container w-full">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 lg:gap-16 items-center">
            
            {/* Left Column: Text aligned to container's left edge, no overflow hidden, no fixed height */}
            <div className="flex flex-col justify-center text-left">
              
              {/* Step Label: left-aligned with title, 24px (mb-6) above it */}
              <div className="flex items-center justify-between mb-6">
                <span className="text-xs font-mono font-medium tracking-wider uppercase text-[#0a0a0a]">
                  {current.stepNum} {current.eyebrow}
                </span>
                
                {/* Step Switcher Indicators */}
                <div className="flex items-center gap-1.5">
                  {FEATURE_STEPS.map((step, idx) => (
                    <button
                      key={step.id}
                      onClick={() => handleStepClick(idx)}
                      aria-label={`Switch to ${step.title}`}
                      className={`h-1.5 rounded-full transition-all duration-300 cursor-pointer ${
                        activeStep === idx
                          ? 'w-6 bg-[#0a0a0a]'
                          : 'w-2 bg-[var(--line-strong)] hover:bg-[#6b6b6b]'
                      }`}
                    />
                  ))}
                </div>
              </div>

              {/* Dynamic Feature Content with Smooth Cross-Fade */}
              <AnimatePresence mode="wait">
                <motion.div
                  key={current.id}
                  initial={shouldReduceMotion ? { opacity: 1 } : { opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={shouldReduceMotion ? { opacity: 0 } : { opacity: 0, y: -12 }}
                  transition={{ duration: 0.3, ease }}
                >
                  {/* Title: max 3 lines, clamp(3rem, 6vw, 5.5rem), mb-6 (24px) */}
                  <h2
                    className="section-title text-[#0a0a0a] text-balance mb-6"
                    style={{ fontSize: 'clamp(3rem, 6vw, 5.5rem)', lineHeight: 0.92 }}
                  >
                    {current.title}
                  </h2>

                  {/* Body: 24px below title, 32px (mb-8) above bullets */}
                  <p className="text-[17px] text-[#6b6b6b] leading-relaxed mb-8">
                    {current.description}
                  </p>

                  {/* Bullets: no clipping */}
                  <div className="space-y-3.5">
                    {current.bullets.map((bullet, idx) => (
                      <div key={idx} className="flex items-start gap-3">
                        <span
                          className="w-1.5 h-1.5 rounded-full bg-[#0a0a0a] shrink-0 mt-2"
                          aria-hidden="true"
                        />
                        <span className="text-[16px] sm:text-[17px] text-[#44403C] leading-snug">
                          {bullet}
                        </span>
                      </div>
                    ))}
                  </div>
                </motion.div>
              </AnimatePresence>

              {/* Scroll progress dots / indicator */}
              <div className="hidden lg:flex items-center gap-2 pt-10">
                {FEATURE_STEPS.map((_, idx) => (
                  <div
                    key={idx}
                    className={`h-1 rounded-full transition-all duration-300 ${
                      activeStep === idx
                        ? 'w-8 bg-[#0a0a0a]'
                        : 'w-2 bg-[var(--line-strong)]'
                    }`}
                  />
                ))}
                <span className="text-[11px] font-mono text-[#a3a3a3] ml-2">
                  Scroll to inspect features
                </span>
              </div>

            </div>

            {/* Right Column: Single Pinned Device with Morphed Screens */}
            <div className="flex justify-center items-center">
              <Tilt maxTilt={4} className="relative">
                {/* Tilted Screen Name Sticker */}
                <div className="absolute top-1 right-2 sm:right-3 z-40 bg-[#0a0a0a] text-white text-[11px] font-mono px-3 py-1 rounded-full border border-white/20 shadow-lg select-none">
                  {current.screenName}
                </div>

                <Phone
                  variant="static"
                  size="lg"
                  className="w-[280px] sm:w-[320px] md:w-[340px]"
                >
                  <AnimatePresence mode="wait">
                    {activeStep === 0 && (
                      <motion.div
                        key="agents"
                        initial={shouldReduceMotion ? false : { opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.25 }}
                        className="w-full h-full"
                      >
                        <img
                          src="/img/real/screen_agent_full.png"
                          alt="Autonomous Agent DAG Execution"
                          className="w-full h-full object-cover object-top"
                        />
                      </motion.div>
                    )}

                    {activeStep === 1 && (
                      <motion.div
                        key="voice"
                        initial={shouldReduceMotion ? false : { opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.25 }}
                        className="w-full h-full"
                      >
                        <video
                          src="/videos/voice_live.mp4"
                          poster="/img/real/screen_voice.png"
                          autoPlay
                          loop
                          muted
                          playsInline
                          className="w-full h-full object-cover object-top"
                        />
                      </motion.div>
                    )}

                    {activeStep === 2 && (
                      <motion.div
                        key="vault"
                        initial={shouldReduceMotion ? false : { opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.25 }}
                        className="w-full h-full"
                      >
                        <img
                          src="/img/real/screen_rag_expanded.png"
                          alt="Encrypted Vector Vault & RAG Search"
                          className="w-full h-full object-cover object-top"
                        />
                      </motion.div>
                    )}
                  </AnimatePresence>
                </Phone>
              </Tilt>
            </div>

          </div>
        </div>
      </div>
    </section>
  );
};
