import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Eye, X, Check } from 'lucide-react';
import { springs } from '../lib/motion';

interface StoryChapter {
  id: string;
  tag: string;
  headline: string;
  subhead: string;
  bullets: string[];
  imageSrc: string;
  tilt: string;
  align: 'left' | 'right';
}

const CHAPTERS: StoryChapter[] = [
  {
    id: 'agents',
    tag: 'Autonomous Agents',
    headline: 'Your phone now takes action.',
    subhead: 'Subagents plan, decompose tasks, and execute local tools with deterministic GBNF grammar constraints.',
    bullets: [
      'GBNF context-free grammars prevent hallucinated JSON arguments',
      'Multi-turn subagent execution with immutable event logging',
      'Sandboxed local execution within Android app boundary'
    ],
    imageSrc: '/img/screenshots/01_chat_interface_1080x1920.png',
    tilt: '-rotate-3',
    align: 'left'
  },
  {
    id: 'voice',
    tag: 'Offline Audio',
    headline: 'Talk naturally. Even in airplane mode.',
    subhead: 'Streaming speech recognition and neural voice synthesis run directly on your local CPU without sending audio to the cloud.',
    bullets: [
      'Sherpa-ONNX streaming Whisper for instantaneous transcription',
      'Silero VAD turn-around under 150ms for low-latency dialogue',
      'Piper neural TTS engine synthesizes 22.05kHz natural audio'
    ],
    imageSrc: '/img/screenshots/02_live_voice_mode_1080x1920.png',
    tilt: 'rotate-3',
    align: 'right'
  },
  {
    id: 'vault',
    tag: 'Local Retrieval',
    headline: 'Your private documents stay private.',
    subhead: 'Index contracts, PDFs, and personal notes into an encrypted on-device SQLite vector database for zero-cloud retrieval.',
    bullets: [
      'Local BGE-small embedding generation directly on ARM SIMD',
      'Encrypted on-device SQLite database with cosine vector search',
      'Zero cloud synchronization or telemetry egress guaranteed'
    ],
    imageSrc: '/img/screenshots/04_rag_documents_1080x1920.png',
    tilt: '-rotate-3',
    align: 'left'
  }
];

export const FeatureStories: React.FC = () => {
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  return (
    <section id="features" className="py-28 md:py-36 bg-[#07080A] relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8 space-y-36">
        {CHAPTERS.map((chap) => {
          const isReversed = chap.align === 'right';

          return (
            <div
              key={chap.id}
              className={`flex flex-col lg:flex-row items-center gap-14 lg:gap-20 ${
                isReversed ? 'lg:flex-row-reverse' : ''
              }`}
            >
              {/* Text Side (50% Width) */}
              <motion.div
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: '-60px' }}
                transition={springs.snappy}
                className="flex-1 space-y-6 max-w-xl"
              >
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B]">
                  <span>{chap.tag}</span>
                </div>

                <h2 className="text-4xl sm:text-5xl md:text-6xl font-extrabold tracking-[-0.04em] text-white leading-[1.04]">
                  {chap.headline}
                </h2>

                <p className="text-lg sm:text-xl text-[#A1A1AA] leading-relaxed font-normal">
                  {chap.subhead}
                </p>

                {/* 3 Checkmark Bullets with Lime Accents */}
                <div className="space-y-3.5 pt-3">
                  {chap.bullets.map((bullet, bIdx) => (
                    <div key={bIdx} className="flex items-start gap-3 text-sm sm:text-base text-zinc-300">
                      <div className="w-5 h-5 rounded-full bg-[#B6FF3B]/10 border border-[#B6FF3B]/30 flex items-center justify-center shrink-0 mt-0.5">
                        <Check className="w-3 h-3 text-[#B6FF3B]" />
                      </div>
                      <span className="leading-snug">{bullet}</span>
                    </div>
                  ))}
                </div>
              </motion.div>

              {/* Phone Visual Side (Large ~50% Width with Glow & Floating Tilt) */}
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true, margin: '-60px' }}
                transition={springs.snappy}
                className="flex-1 w-full flex justify-center items-center relative"
              >
                {/* Soft Radial Lime Glow behind phone */}
                <div className="absolute w-[450px] h-[450px] bg-gradient-to-b from-[#B6FF3B]/12 to-transparent blur-3xl rounded-full pointer-events-none" />

                {/* Large Tilted Titanium Phone Mockup */}
                <div
                  onClick={() => setSelectedImage(chap.imageSrc)}
                  className={`cursor-pointer relative z-10 w-full max-w-[340px] sm:max-w-[380px] aspect-[9/18] rounded-[44px] p-3 bg-[#0E1015] border-2 border-white/15 shadow-[0_30px_90px_rgba(0,0,0,0.95),0_0_50px_rgba(0,0,0,0.8)] transition-all duration-300 hover:scale-[1.02] hover:border-[#B6FF3B]/40 group ${chap.tilt} animate-float-center`}
                >
                  <div className="w-full h-full rounded-[34px] overflow-hidden bg-black relative">
                    <img
                      src={chap.imageSrc}
                      alt={chap.headline}
                      className="w-full h-full object-cover object-top filter contrast-105"
                      loading="lazy"
                      width={380}
                      height={760}
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent pointer-events-none" />
                    <div className="absolute bottom-4 right-4 z-10 px-3.5 py-1.5 rounded-lg bg-black/85 backdrop-blur-md border border-white/15 text-xs font-mono text-zinc-300 flex items-center gap-2 group-hover:text-white group-hover:border-[#B6FF3B]/40 transition-all">
                      <Eye className="w-3.5 h-3.5 text-[#B6FF3B]" />
                      <span>Inspect Screen</span>
                    </div>
                  </div>
                </div>
              </motion.div>
            </div>
          );
        })}
      </div>

      {/* Lightbox Modal */}
      <AnimatePresence>
        {selectedImage && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            role="dialog"
            aria-modal="true"
            className="fixed inset-0 z-50 bg-black/95 backdrop-blur-xl flex items-center justify-center p-4 sm:p-8"
            onClick={() => setSelectedImage(null)}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              transition={springs.snappy}
              className="relative max-w-xl max-h-[90vh] rounded-2xl overflow-hidden border border-white/20 shadow-2xl bg-zinc-950"
              onClick={(e) => e.stopPropagation()}
            >
              <button
                type="button"
                onClick={() => setSelectedImage(null)}
                className="absolute top-4 right-4 z-20 p-2 rounded-full bg-black/80 border border-white/20 text-white hover:bg-zinc-800 transition-colors"
                aria-label="Close Preview"
              >
                <X className="w-5 h-5" />
              </button>
              <img
                src={selectedImage}
                alt="Full Resolution Screen Capture"
                className="w-full h-auto max-h-[85vh] object-contain"
              />
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
};
