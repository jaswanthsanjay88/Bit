import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Eye, X, Check, ArrowRight } from 'lucide-react';
import { springs } from '../lib/motion';

interface StoryChapter {
  id: string;
  tag: string;
  headline: string;
  subhead: string;
  chips: string[];
  imageSrc: string;
  align: 'left' | 'right';
}

const CHAPTERS: StoryChapter[] = [
  {
    id: 'agents',
    tag: 'AUTONOMOUS AGENTS',
    headline: 'Your phone now takes action.',
    subhead: 'Subagents plan, decompose tasks, and execute local tools with deterministic GBNF grammar constraints. No hallucinations, no broken JSON.',
    chips: ['GBNF Schema Clamped', 'Multi-Step Execution', 'Sandboxed Process Boundary'],
    imageSrc: '/img/screenshots/01_chat_interface_1080x1920.png',
    align: 'left'
  },
  {
    id: 'voice',
    tag: 'OFFLINE AUDIO',
    headline: 'Talk naturally. Even in airplane mode.',
    subhead: 'Streaming speech recognition and neural voice synthesis run directly on your local CPU. Seamless conversational turn-around under 150ms.',
    chips: ['Sherpa-ONNX Whisper', '<150ms Silero VAD', '22.05kHz Piper TTS Engine'],
    imageSrc: '/img/screenshots/02_live_voice_mode_1080x1920.png',
    align: 'right'
  },
  {
    id: 'vault',
    tag: 'LOCAL RETRIEVAL',
    headline: 'Your private documents stay private.',
    subhead: 'Index contracts, PDFs, and notes into an encrypted on-device SQLite vector database. Fast semantic retrieval with zero cloud exposure.',
    chips: ['Encrypted SQLite Vector', 'Local BGE-Small Embeddings', 'Zero Egress Guarantee'],
    imageSrc: '/img/screenshots/04_rag_documents_1080x1920.png',
    align: 'left'
  }
];

export const FeatureStories: React.FC = () => {
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  return (
    <section id="features" className="py-24 bg-black relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-32">
        {CHAPTERS.map((chap, idx) => {
          const isReversed = chap.align === 'right';

          return (
            <div
              key={chap.id}
              className={`flex flex-col lg:flex-row items-center gap-12 lg:gap-16 ${
                isReversed ? 'lg:flex-row-reverse' : ''
              }`}
            >
              {/* Text Side (One idea, one benefit) */}
              <div className="flex-1 space-y-6 max-w-xl">
                <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider">
                  <span>{chap.tag}</span>
                </div>

                <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-[1.08]">
                  {chap.headline}
                </h2>

                <p className="text-base sm:text-lg text-zinc-400 leading-relaxed font-normal">
                  {chap.subhead}
                </p>

                {/* Proof Chips */}
                <div className="space-y-2.5 pt-2">
                  {chap.chips.map((chip, cIdx) => (
                    <div key={cIdx} className="flex items-center gap-2.5 text-xs text-zinc-300 font-mono">
                      <div className="w-4 h-4 rounded-full bg-zinc-900 border border-white/15 flex items-center justify-center shrink-0">
                        <Check className="w-2.5 h-2.5 text-white" />
                      </div>
                      <span>{chip}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Visual Side (Big phone frame) */}
              <div className="flex-1 w-full max-w-md lg:max-w-lg">
                <motion.div
                  whileHover={{ y: -4 }}
                  transition={springs.snappy}
                  onClick={() => setSelectedImage(chap.imageSrc)}
                  className="cursor-pointer relative overflow-hidden rounded-2xl bg-zinc-950 border border-white/15 p-2 shadow-[0_20px_60px_rgba(0,0,0,0.85)] group"
                >
                  <div className="relative overflow-hidden rounded-xl bg-black aspect-[9/16] max-h-[580px] flex items-center justify-center">
                    <img
                      src={chap.imageSrc}
                      alt={chap.headline}
                      className="w-full h-full object-cover object-top rounded-lg group-hover:scale-[1.01] transition-transform duration-300 filter contrast-105"
                      loading="lazy"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent pointer-events-none" />
                    <div className="absolute bottom-4 right-4 z-10 px-3 py-1.5 rounded-lg bg-black/80 backdrop-blur-md border border-white/10 text-xs font-mono text-zinc-300 flex items-center gap-2 opacity-90 group-hover:opacity-100 transition-opacity">
                      <Eye className="w-3.5 h-3.5" />
                      <span>Inspect Screen</span>
                    </div>
                  </div>
                </motion.div>
              </div>
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
                className="absolute top-4 right-4 z-20 p-2 rounded-full bg-black/80 border border-white/20 text-white hover:bg-zinc-800 transition-colors tactile-button"
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
