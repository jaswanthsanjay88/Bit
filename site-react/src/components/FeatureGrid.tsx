import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Layers, Mic, HardDrive, Database, Sliders, Cpu, Eye, X, CheckCircle } from 'lucide-react';
import { springs } from '../lib/motion';

interface FeatureCard {
  id: string;
  tag: string;
  title: string;
  description: string;
  imageSrc: string;
  specs: string[];
  icon: React.ReactNode;
}

const FEATURES: FeatureCard[] = [
  {
    id: 'chat-agents',
    tag: 'ORCHESTRATION',
    title: 'Autonomous Tool Chaining & GBNF Constraints',
    description: 'Subagents plan, decompose, and execute multi-step tool calls on-device. GBNF context-free grammars guarantee valid JSON argument schemas without token hallucination.',
    imageSrc: '/img/screenshots/01_chat_interface_1080x1920.png',
    specs: ['GBNF grammar enforcement', 'Sandboxed shell & web fetch', 'Multi-turn execution logs'],
    icon: <Layers className="w-4 h-4 text-zinc-300" />
  },
  {
    id: 'voice-vad',
    tag: 'OFFLINE AUDIO',
    title: 'Real-Time Voice Mode & Local VAD',
    description: 'A 100% offline speech pipeline. Sherpa-ONNX Whisper streams acoustic transcription, while Silero VAD detects speech pauses and Piper synthesizes natural neural speech.',
    imageSrc: '/img/screenshots/02_live_voice_mode_1080x1920.png',
    specs: ['<150ms VAD turn-around', 'Sherpa-ONNX streaming STT', 'Piper neural TTS engine'],
    icon: <Mic className="w-4 h-4 text-zinc-300" />
  },
  {
    id: 'model-store',
    tag: 'INFERENCE STORAGE',
    title: 'Quantized Model Store & RAM Diagnostics',
    description: 'Browse, benchmark, and sideload verified GGUF weights directly from HuggingFace. Automatic device memory safety validation prevents out-of-memory kernel panics.',
    imageSrc: '/img/screenshots/03_model_store_1080x1920.png',
    specs: ['Direct GGUF repository resolver', 'Dynamic RAM safety buffers', 'Background chunked downloads'],
    icon: <HardDrive className="w-4 h-4 text-zinc-300" />
  },
  {
    id: 'memory-rag',
    tag: 'LOCAL RETRIEVAL',
    title: 'Memory Vault & Vector Document RAG',
    description: 'Private knowledge retrieval. Documents, PDFs, and code repositories are embedded locally into an encrypted SQLite vector database with cosine similarity search.',
    imageSrc: '/img/screenshots/04_rag_documents_1080x1920.png',
    specs: ['Encrypted on-device storage', 'Local embedding inference', 'Zero cloud vector syncing'],
    icon: <Database className="w-4 h-4 text-zinc-300" />
  }
];

export const FeatureGrid: React.FC = () => {
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  return (
    <section id="features" className="py-24 relative bg-black">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="max-w-3xl mb-16">
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
            <span>Product Surfaces</span>
          </div>
          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white mb-5">
            Engineered for pure on-device sovereignty.
          </h2>
          <p className="text-base sm:text-lg text-zinc-400 font-normal leading-relaxed">
            Every feature runs on your local application processor. Native Jetpack Compose UI backed by hardened C++ execution cores.
          </p>
        </div>

        {/* Feature Cards Grid (2x2) */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {FEATURES.map((feat) => (
            <motion.div
              key={feat.id}
              whileHover={{ y: -3 }}
              transition={springs.snappy}
              className="rounded-2xl surface-card p-6 sm:p-8 flex flex-col justify-between group"
            >
              <div>
                <div className="flex items-center justify-between gap-3 mb-4">
                  <div className="flex items-center gap-2 text-xs font-mono text-zinc-400">
                    <span className="p-1.5 rounded-md bg-zinc-800 border border-white/10">
                      {feat.icon}
                    </span>
                    <span>{feat.tag}</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedImage(feat.imageSrc)}
                    className="inline-flex items-center gap-1.5 text-xs text-zinc-400 hover:text-white transition-colors tactile-button"
                  >
                    <Eye className="w-3.5 h-3.5" />
                    <span>Expand view</span>
                  </button>
                </div>

                <h3 className="text-xl sm:text-2xl font-bold text-white mb-3 tracking-tight">
                  {feat.title}
                </h3>
                <p className="text-sm text-zinc-400 leading-relaxed mb-6 font-normal">
                  {feat.description}
                </p>

                {/* Tech Specs List */}
                <div className="space-y-2 mb-8">
                  {feat.specs.map((spec, sIdx) => (
                    <div key={sIdx} className="flex items-center gap-2 text-xs text-zinc-300">
                      <CheckCircle className="w-3.5 h-3.5 text-zinc-500" />
                      <span>{spec}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Titanium Phone Mockup Screen */}
              <div
                onClick={() => setSelectedImage(feat.imageSrc)}
                className="cursor-pointer relative overflow-hidden rounded-xl bg-zinc-950 border border-white/10 group-hover:border-white/25 transition-all shadow-[0_12px_40px_rgba(0,0,0,0.8)] aspect-[16/10] flex items-center justify-center p-3"
              >
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent z-10 pointer-events-none" />
                <img
                  src={feat.imageSrc}
                  alt={feat.title}
                  className="w-full h-full object-cover object-top rounded-lg group-hover:scale-[1.02] transition-transform duration-300 filter contrast-105"
                  loading="lazy"
                />
                <div className="absolute bottom-3 right-3 z-20 px-2.5 py-1 rounded bg-black/80 backdrop-blur-md border border-white/10 text-[10px] font-mono text-zinc-300 flex items-center gap-1.5 opacity-90">
                  <Eye className="w-3 h-3" />
                  <span>Inspect UI</span>
                </div>
              </div>
            </motion.div>
          ))}
        </div>

        {/* Secondary Surface Cards: Sampler & Diagnostics */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mt-8">
          <motion.div
            whileHover={{ y: -2 }}
            transition={springs.snappy}
            className="rounded-2xl surface-card p-6 flex flex-col justify-between"
          >
            <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-3">
              <Sliders className="w-4 h-4 text-zinc-300" />
              <span>RUNTIME TUNING</span>
            </div>
            <h4 className="text-lg font-bold text-white mb-2">GBNF Sampler & Context Editor</h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Real-time parameter manipulation: temperature, top-k, min-p, repeat penalty, and custom grammar injections without rebuilding model weights.
            </p>
            <div
              onClick={() => setSelectedImage('/img/live_captures/5_editor.png')}
              className="cursor-pointer overflow-hidden rounded-lg bg-zinc-950 border border-white/10 h-48 relative group"
            >
              <img
                src="/img/live_captures/5_editor.png"
                alt="GBNF Sampler Editor"
                className="w-full h-full object-cover object-top group-hover:scale-105 transition-transform duration-200"
                loading="lazy"
              />
            </div>
          </motion.div>

          <motion.div
            whileHover={{ y: -2 }}
            transition={springs.snappy}
            className="rounded-2xl surface-card p-6 flex flex-col justify-between"
          >
            <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-3">
              <Cpu className="w-4 h-4 text-zinc-300" />
              <span>HARDWARE TELEMETRY</span>
            </div>
            <h4 className="text-lg font-bold text-white mb-2">Hardware & NPU Diagnostics</h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Real-time telemetry tracking CPU core affinity, GPU vRAM offloading, thermal throttling thresholds, and battery milliamp drain.
            </p>
            <div
              onClick={() => setSelectedImage('/img/live_captures/6_settings.png')}
              className="cursor-pointer overflow-hidden rounded-lg bg-zinc-950 border border-white/10 h-48 relative group"
            >
              <img
                src="/img/live_captures/6_settings.png"
                alt="Hardware Diagnostics"
                className="w-full h-full object-cover object-top group-hover:scale-105 transition-transform duration-200"
                loading="lazy"
              />
            </div>
          </motion.div>
        </div>
      </div>

      {/* Lightbox Modal with AnimatePresence */}
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
