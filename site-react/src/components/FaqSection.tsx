import React, { useState } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'motion/react';
import { RevealText } from './motion/RevealText';
import { Reveal } from './motion/Reveal';
import { ease, spring } from '../lib/motion';

interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

const FAQS: FaqItem[] = [
  {
    id: 'offline',
    question: 'Does any data ever leave my device?',
    answer: 'Zero bytes leave your phone. All model inference, vector embedding generation, SQLite memory indexing, and audio processing execute directly on your hardware via compiled C++ (llama.cpp and whisper.cpp). You can activate Airplane Mode and turn off Wi-Fi and Cellular entirely; BIT works with zero degradation.'
  },
  {
    id: 'devices',
    question: 'Which Android devices and processors are supported?',
    answer: 'Any Android phone running Android 12.0 (API level 31) or newer with an ARM64-v8a processor. Lightweight 4-bit models like Qwen 3.5 0.8B and Liquid LFM2 350M require only 4GB of physical RAM and run comfortably on mid-tier chips (Snapdragon 695, Helio G99). Larger 7B and 8B models recommend 8GB to 12GB of RAM with a flagship processor (Snapdragon 8 Gen 1+, Dimensity 9000+, or Tensor G2+).'
  },
  {
    id: 'models',
    question: 'Which open-source models can I download and run?',
    answer: 'BIT supports any standard GGUF-quantized model. The built-in Model Store provides tested, one-tap downloads for Qwen 3.5 (0.8B, 2B), Liquid LFM2, and SmolLM. Advanced users can also sideload custom GGUF weights directly from local storage or transfer them from a computer.'
  },
  {
    id: 'voice',
    question: 'How does real-time voice mode operate without an internet connection?',
    answer: 'Voice conversation combines an embedded quantized Whisper C++ model for real-time speech-to-text with an offline neural text-to-speech engine (Piper TTS) running on CPU. Audio buffers remain strictly in temporary memory and are never uploaded to any remote transcription service.'
  },
  {
    id: 'license',
    question: 'Why is BIT free and open source? What is the catch?',
    answer: 'There is no catch. BIT is an open-source research and engineering initiative licensed under Apache 2.0. We do not sell subscriptions, collect telemetry, track usage analytics, or serve advertisements. The full codebase, native bindings, and build scripts are publicly verifiable on GitHub.'
  }
];

export const FaqSection: React.FC = () => {
  const [openId, setOpenId] = useState<string | null>('offline');
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const shouldReduceMotion = useReducedMotion();

  const toggleFaq = (id: string) => {
    setOpenId((prev) => (prev === id ? null : id));
  };

  return (
    <section id="faq" className="w-full bg-white section-rhythm border-t border-[var(--line)]">
      <div className="container--narrow text-left">
        
        {/* Header: at most 2 lines, clamp(3rem, 7vw, 5.5rem), mb-6 (24px) */}
        <div className="mb-6 w-full">
          <RevealText
            text="Frequently asked questions"
            as="h2"
            className="section-title text-[#0a0a0a] block w-full"
            style={{ fontSize: 'clamp(3rem, 7vw, 5.5rem)', lineHeight: 0.92 }}
          />
        </div>
        <Reveal delay={0.1}>
          <p className="text-[17px] text-[#6b6b6b] leading-relaxed mb-8">
            Architecture details on local inference, hardware bounds, and privacy guarantees.
          </p>
        </Reveal>

          {/* Clean Hairline Accordion List */}
          <div className="border-t border-[var(--line)]">
            {FAQS.map((faq) => {
              const isOpen = openId === faq.id;
              const isHovered = hoveredId === faq.id;

              return (
                <div
                  key={faq.id}
                  onMouseEnter={() => setHoveredId(faq.id)}
                  onMouseLeave={() => setHoveredId(null)}
                  className={`border-b transition-colors duration-200 ${
                    isHovered ? 'border-[var(--line-strong)]' : 'border-[var(--line)]'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => toggleFaq(faq.id)}
                    aria-expanded={isOpen}
                    aria-controls={`faq-answer-${faq.id}`}
                    id={`faq-btn-${faq.id}`}
                    className="w-full py-6 sm:py-7 text-left flex items-center justify-between gap-6 group cursor-pointer focus-ring rounded"
                  >
                    <motion.span
                      animate={{
                        x: isHovered && !shouldReduceMotion ? 4 : 0,
                      }}
                      transition={{ duration: 0.18, ease }}
                      className="text-lg sm:text-[20px] font-medium text-[#0a0a0a] group-hover:text-black transition-colors leading-snug"
                    >
                      {faq.question}
                    </motion.span>

                    {/* Minimal + to - Morph Icon */}
                    <div className="relative w-6 h-6 shrink-0 flex items-center justify-center text-[#0a0a0a]">
                      {/* Horizontal bar */}
                      <span className="absolute w-3.5 h-[1.5px] bg-[#0a0a0a] rounded-full" />
                      {/* Vertical bar rotating to minus */}
                      <motion.span
                        animate={{
                          rotate: isOpen ? 90 : 0,
                          opacity: isOpen ? 0 : 1,
                        }}
                        transition={spring}
                        className="absolute w-[1.5px] h-3.5 bg-[#0a0a0a] rounded-full"
                      />
                    </div>
                  </button>

                  <AnimatePresence initial={false}>
                    {isOpen && (
                      <motion.div
                        id={`faq-answer-${faq.id}`}
                        role="region"
                        aria-labelledby={`faq-btn-${faq.id}`}
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.35, ease }}
                        className="overflow-hidden"
                      >
                        <motion.div
                          initial={shouldReduceMotion ? false : { y: 6, opacity: 0 }}
                          animate={{ y: 0, opacity: 1 }}
                          transition={{ duration: 0.3, delay: 0.06, ease }}
                          className="pb-7 pr-8 sm:pr-12 text-[16px] sm:text-[17px] text-[#6b6b6b] leading-relaxed"
                        >
                          {faq.answer}
                        </motion.div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              );
            })}
          </div>

        </div>
    </section>
  );
};
