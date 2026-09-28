import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { ChevronDown, HelpCircle } from 'lucide-react';
import { springs } from '../lib/motion';

interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

const FAQS: FaqItem[] = [
  {
    id: 'offline',
    question: 'Is BIT truly 100% offline, or does it call an API proxy?',
    answer: 'BIT executes 100% locally on your smartphone processor using an embedded C++ llama.cpp runtime via JNI. You can turn on Airplane Mode and disconnect Wi-Fi and Cellular entirely; every core capability—chat, voice transcription, GBNF tool calling, and document RAG—continues to run with zero interruption.'
  },
  {
    id: 'battery',
    question: 'Will running local models drain my battery or overheat the phone?',
    answer: 'BIT uses quantized 4-bit weights (Q4_K_M) and hardware SIMD vectorization (ARM Neon) to minimize CPU active time. Tensor operations run only during active inference turns. Memory buffers are unloaded when idle, preventing background battery drain and avoiding thermal throttling.'
  },
  {
    id: 'models',
    question: 'Where do the models come from and are they free to use?',
    answer: 'All models are open-weights hosted publicly on HuggingFace by organizations like Alibaba (Qwen), Liquid AI, and Rhasspy. There are no subscriptions, paywalls, or DRM locks. You can also sideload your own custom GGUF weights directly into the application.'
  },
  {
    id: 'tools',
    question: 'How does tool calling work without an external server?',
    answer: 'Tools execute directly on-device. Local tasks (document search, file parsing, encrypted SQLite queries, unit calculations) run against local Android APIs. Web grounding tools, when activated with internet access, make direct HTTP requests without passing through any intermediate proxy or analytics service.'
  },
  {
    id: 'requirements',
    question: 'What are the minimum device specifications to run BIT?',
    answer: 'Any Android phone running Android 12.0 or higher (API level 31+) with an ARM64-v8a processor. Entry-level models like Qwen 3.5 0.8B and LFM2 350M require only 4GB of physical RAM and run smoothly on mid-range chipsets like Snapdragon 695 and Helio G99.'
  },
  {
    id: 'license',
    question: 'Why is BIT open source and free? What is the catch?',
    answer: 'There is no catch. BIT is an open-source sovereign computing project released under the Apache 2.0 license. We do not monetize data, sell subscriptions, or serve ads. The complete codebase is publicly auditable on GitHub.'
  }
];

export const FaqSection: React.FC = () => {
  const [openId, setOpenId] = useState<string | null>('offline');

  const toggleFaq = (id: string) => {
    setOpenId((prev) => (prev === id ? null : id));
  };

  return (
    <section id="faq" className="py-28 md:py-36 bg-[#07080A] relative">
      <div className="max-w-[900px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-20">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B] mb-4">
            <HelpCircle className="w-3.5 h-3.5" />
            <span>Common Questions</span>
          </div>
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-4">
            Frequently Asked Questions
          </h2>
          <p className="text-lg text-[#A1A1AA]">
            Everything you need to know about on-device sovereign intelligence.
          </p>
        </div>

        {/* Larger Accordion List */}
        <div className="space-y-4">
          {FAQS.map((faq) => {
            const isOpen = openId === faq.id;

            return (
              <div
                key={faq.id}
                className="rounded-2xl surface-2 border border-white/10 overflow-hidden transition-colors"
              >
                <button
                  type="button"
                  onClick={() => toggleFaq(faq.id)}
                  className="w-full p-6 sm:p-7 text-left flex items-center justify-between gap-6 hover:bg-white/[0.02] transition-colors"
                >
                  <span className="text-lg sm:text-xl font-bold text-white tracking-tight">
                    {faq.question}
                  </span>
                  <motion.div
                    animate={{ rotate: isOpen ? 180 : 0 }}
                    transition={springs.snappy}
                    className="w-8 h-8 rounded-lg bg-[#07080A] border border-white/10 flex items-center justify-center shrink-0 text-[#B6FF3B]"
                  >
                    <ChevronDown className="w-4 h-4" />
                  </motion.div>
                </button>

                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={springs.snappy}
                      className="overflow-hidden"
                    >
                      <div className="p-6 sm:p-7 pt-0 text-base sm:text-lg text-[#A1A1AA] leading-relaxed font-normal border-t border-white/5">
                        {faq.answer}
                      </div>
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
