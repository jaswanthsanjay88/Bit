import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Download, Search, Check, Copy, ChevronDown, ChevronUp } from 'lucide-react';
import { springs } from '../lib/motion';

interface ModelItem {
  id: string;
  name: string;
  type: 'LLM' | 'VLM' | 'AUDIO' | 'EMBEDDING';
  size: string;
  description: string;
  url: string;
  format: string;
  minRamGb: number;
  tags: string[];
}

const VERIFIED_MODELS: ModelItem[] = [
  {
    id: "qwen-35-08b",
    name: "Qwen 3.5 0.8B",
    type: "LLM",
    size: "0.8 GB",
    description: "Ultra-fast chat and tool routing on 4GB+ RAM devices.",
    url: "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
    format: "GGUF Q4_K_M",
    minRamGb: 4,
    tags: ["Fast", "Chat", "Tools"]
  },
  {
    id: "lfm2-350m",
    name: "LFM2 350M",
    type: "LLM",
    size: "350 MB",
    description: "Hybrid architecture for near-zero latency offline dialogue.",
    url: "https://huggingface.co/LiquidAI/LFM-350M-GGUF",
    format: "GGUF Q4_0",
    minRamGb: 4,
    tags: ["Low Memory", "Sub-100ms"]
  },
  {
    id: "moondream2-vlm",
    name: "Moondream 2",
    type: "VLM",
    size: "1.6 GB",
    description: "On-device vision model for image QA and document inspection.",
    url: "https://huggingface.co/vikhyatk/moondream2",
    format: "GGUF Q4_K_S",
    minRamGb: 6,
    tags: ["Vision", "Multimodal"]
  },
  {
    id: "kokoro-tts",
    name: "Kokoro 82M",
    type: "AUDIO",
    size: "86 MB",
    description: "Studio-grade neural voice synthesis in an 86MB footprint.",
    url: "https://huggingface.co/hexgrad/Kokoro-82M",
    format: "ONNX FP16",
    minRamGb: 4,
    tags: ["TTS", "Voice", "24kHz"]
  },
  {
    id: "qwen-35-4b",
    name: "Qwen 3.5 4B",
    type: "LLM",
    size: "2.8 GB",
    description: "Full coding and multi-turn agent planning on mid-range devices.",
    url: "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF",
    format: "GGUF Q4_K_M",
    minRamGb: 6,
    tags: ["Balanced", "Coding", "Reasoning"]
  },
  {
    id: "qwen-35-9b",
    name: "Qwen 3.5 9B",
    type: "LLM",
    size: "5.4 GB",
    description: "Deep multi-step reasoning and synthesis for flagship phones.",
    url: "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF",
    format: "GGUF Q4_K_M",
    minRamGb: 8,
    tags: ["Flagship", "Complex Tasks"]
  },
  {
    id: "lfm2-vl-450m",
    name: "LFM2-VL 450M",
    type: "VLM",
    size: "520 MB",
    description: "Lightweight visual reasoning optimized for mobile memory.",
    url: "https://huggingface.co/LiquidAI/LFM-VL-450M-GGUF",
    format: "GGUF Q4_K_M",
    minRamGb: 4,
    tags: ["Vision", "Mobile Optimized"]
  },
  {
    id: "nomic-embed-text",
    name: "Nomic Embed v1.5",
    type: "EMBEDDING",
    size: "82 MB",
    description: "Dense vector embeddings for on-device document RAG.",
    url: "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF",
    format: "GGUF Q4_K_M",
    minRamGb: 4,
    tags: ["Embeddings", "RAG", "Vault"]
  },
  {
    id: "piper-voice-us",
    name: "Piper US Amy",
    type: "AUDIO",
    size: "28 MB",
    description: "Ultra-light streaming voice synthesis on local CPU.",
    url: "https://huggingface.co/rhasspy/piper-voices",
    format: "ONNX",
    minRamGb: 4,
    tags: ["TTS", "Real-Time"]
  },
  {
    id: "whisper-base-onnx",
    name: "Whisper Base",
    type: "AUDIO",
    size: "145 MB",
    description: "High-accuracy multilingual speech-to-text recognition.",
    url: "https://huggingface.co/ggerganov/whisper.cpp",
    format: "Sherpa-ONNX",
    minRamGb: 4,
    tags: ["STT", "Speech", "Offline"]
  }
];

export const ModelStore: React.FC = () => {
  const [models] = useState<ModelItem[]>(VERIFIED_MODELS);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isExpanded, setIsExpanded] = useState(false);

  const handleCopyLink = (url: string, id: string) => {
    navigator.clipboard.writeText(url);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const filteredModels = useMemo(() => {
    return models.filter((m) => {
      const matchesSearch =
        m.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        m.description.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesType =
        selectedType === 'ALL' ||
        (selectedType === 'CHAT' && m.type === 'LLM') ||
        (selectedType === 'VISION' && m.type === 'VLM') ||
        (selectedType === 'AUDIO' && m.type === 'AUDIO');

      return matchesSearch && matchesType;
    });
  }, [models, searchQuery, selectedType]);

  const displayedModels = isExpanded ? filteredModels : models.slice(0, 3);

  return (
    <section id="models" className="py-28 md:py-36 bg-[#07080A] relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header without eyebrow */}
        <div className="text-center max-w-2xl mx-auto mb-16">
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-4">
            Verified Open Weights
          </h2>
          <p className="text-base sm:text-lg text-[#A1A1AA] leading-relaxed">
            Pre-quantized GGUF, VLM, and ONNX weights validated for mobile ARM processors. Direct HuggingFace downloads.
          </p>
        </div>

        {/* Filter Bar (Visible when expanded) */}
        <AnimatePresence>
          {isExpanded && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={springs.snappy}
              className="overflow-hidden mb-8"
            >
              <div className="p-4 rounded-xl surface-2 border border-white/10 flex flex-col sm:flex-row items-center justify-between gap-4">
                <div className="relative w-full sm:w-80">
                  <Search className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    placeholder="Search models..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 text-sm bg-[#07080A] border border-white/10 rounded-lg text-white placeholder-zinc-500 focus:outline-none focus:border-[#B6FF3B]/50 font-sans"
                  />
                </div>

                <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
                  {[
                    { label: 'All', value: 'ALL' },
                    { label: 'Chat LLMs', value: 'CHAT' },
                    { label: 'Vision (VLM)', value: 'VISION' },
                    { label: 'Audio', value: 'AUDIO' }
                  ].map((chip) => (
                    <button
                      key={chip.value}
                      type="button"
                      onClick={() => setSelectedType(chip.value)}
                      className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
                        selectedType === chip.value
                          ? 'bg-[#B6FF3B] text-[#07080A] font-bold shadow-sm'
                          : 'bg-[#07080A] text-zinc-400 hover:text-white border border-white/5'
                      }`}
                    >
                      {chip.label}
                    </button>
                  ))}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Larger Model Cards with Big Size Numbers */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-stretch">
          {displayedModels.map((m) => {
            const isCopied = copiedId === m.id;

            return (
              <div
                key={m.id}
                className="rounded-2xl surface-2 p-8 flex flex-col justify-between border border-white/10 hover:border-white/20 transition-all group shadow-xl"
              >
                <div>
                  {/* Top Row: Type Tag & Big Size Number */}
                  <div className="flex items-center justify-between mb-6">
                    <span className="text-xs px-2.5 py-1 rounded bg-[#07080A] text-[#B6FF3B] border border-white/10 font-medium">
                      {m.type}
                    </span>
                    <div className="text-3xl sm:text-4xl font-extrabold text-[#B6FF3B] tracking-tight tabular-nums">
                      {m.size}
                    </div>
                  </div>

                  <h3 className="text-2xl font-bold text-white mb-3 group-hover:text-[#B6FF3B] transition-colors">
                    {m.name}
                  </h3>

                  {/* Short 1-line description, min 15px */}
                  <p className="text-[15px] sm:text-base text-[#A1A1AA] leading-relaxed mb-6 font-normal">
                    {m.description}
                  </p>
                </div>

                <div className="space-y-4 pt-6 border-t border-white/5">
                  <div className="flex items-center justify-between text-xs text-zinc-400">
                    <span className="font-mono text-zinc-500">{m.format}</span>
                    <span className="text-white font-medium">Min {m.minRamGb}GB RAM</span>
                  </div>

                  <div className="flex items-center gap-2">
                    <a
                      href={m.url}
                      target="_blank"
                      rel="noreferrer"
                      className="flex-1 inline-flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-white/[0.08] hover:bg-[#B6FF3B] text-white hover:text-[#07080A] text-sm font-bold transition-all"
                    >
                      <Download className="w-4 h-4" />
                      <span>Download GGUF</span>
                    </a>
                    <button
                      type="button"
                      onClick={() => handleCopyLink(m.url, m.id)}
                      className="p-3 rounded-xl bg-white/[0.04] hover:bg-white/[0.1] border border-white/10 text-zinc-400 hover:text-white transition-colors"
                      title="Copy Link"
                      aria-label="Copy Download Link"
                    >
                      {isCopied ? <Check className="w-4 h-4 text-[#B6FF3B]" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Full-Width "Explore All 10 Models" Button under the cards */}
        <div className="mt-10">
          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
            className="w-full py-4.5 rounded-2xl bg-[#0D0F12] border border-white/10 hover:border-[#B6FF3B]/50 hover:bg-[#141720] text-base font-semibold text-white transition-all flex items-center justify-center gap-2.5 shadow-lg group cursor-pointer"
          >
            <span>{isExpanded ? 'Collapse Model Catalog' : 'Explore All 10 Verified Models'}</span>
            {isExpanded ? (
              <ChevronUp className="w-5 h-5 text-[#B6FF3B] group-hover:-translate-y-0.5 transition-transform" />
            ) : (
              <ChevronDown className="w-5 h-5 text-[#B6FF3B] group-hover:translate-y-0.5 transition-transform" />
            )}
          </button>
        </div>

      </div>
    </section>
  );
};
