import React, { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Search, Download, Copy, Check, Database, ChevronDown, ChevronUp } from 'lucide-react';
import { springs } from '../lib/motion';

export interface ModelItem {
  id: string;
  name: string;
  description: string;
  type: string;
  url: string;
  size: string;
  minRamGb: number;
  tags: string[];
}

const VERIFIED_MODELS: ModelItem[] = [
  {
    id: "qwen3.5-0.8b-q4km",
    name: "Qwen 3.5 0.8B",
    description: "Ultra-lightweight chat model with native GBNF tool calling. Ideal for entry-level 4GB RAM phones.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
    size: "600 MB",
    minRamGb: 4,
    tags: ["Chat", "Tool Calling", "Tested"]
  },
  {
    id: "qwen3.5-4b-q4km",
    name: "Qwen 3.5 4B",
    description: "Balanced reasoning engine with high-throughput coding and planning. Recommended for mid-range devices.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
    size: "2.7 GB",
    minRamGb: 6,
    tags: ["Chat", "Tool Calling", "High Quality"]
  },
  {
    id: "lfm2-350m-q8",
    name: "LFM2 350M",
    description: "Ultra-fast liquid foundation model from Liquid AI. Instant responses delivering 60+ tokens/sec on Cortex-A78.",
    type: "GGUF",
    url: "https://huggingface.co/LiquidAI/LFM2-350M-GGUF/resolve/main/LFM2-350M-Q8_0.gguf",
    size: "400 MB",
    minRamGb: 3,
    tags: ["Chat", "Ultra Fast", "Tested"]
  },
  {
    id: "qwen3.5-9b-q4km",
    name: "Qwen 3.5 9B",
    description: "Heavyweight reasoning and agent synthesis. Recommended for flagships with 8GB to 12GB RAM.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf",
    size: "5.5 GB",
    minRamGb: 8,
    tags: ["Chat", "Tool Calling", "Flagship"]
  },
  {
    id: "lfm2-vl-450m-q8",
    name: "LFM2-VL 450M Multimodal",
    description: "On-device Vision-Language Model. Analyzes images, camera captures, and screenshots with local mmproj.",
    type: "VLM",
    url: "https://huggingface.co/LiquidAI/LFM2-VL-450M-GGUF/resolve/main/LFM2-VL-450M-Q8_0.gguf",
    size: "550 MB",
    minRamGb: 4,
    tags: ["Vision", "VLM", "Multimodal"]
  },
  {
    id: "moondream2-q4km",
    name: "Moondream 2 VLM",
    description: "Efficient small vision model for visual question answering, OCR, and document layout scanning.",
    type: "VLM",
    url: "https://huggingface.co/vikhyatk/moondream2/resolve/main/moondream2-text-model-q4_k.gguf",
    size: "1.1 GB",
    minRamGb: 4,
    tags: ["Vision", "OCR", "Tested"]
  },
  {
    id: "sherpa-whisper-tiny",
    name: "Sherpa-ONNX Whisper Tiny",
    description: "Streaming automatic speech recognition model for real-time offline voice mode.",
    type: "STT",
    url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2",
    size: "75 MB",
    minRamGb: 2,
    tags: ["Audio", "STT", "Streaming"]
  },
  {
    id: "piper-voice-en",
    name: "Piper ONNX Neural Voice",
    description: "Fast local text-to-speech engine producing natural 22kHz speech synthesis with zero GPU requirement.",
    type: "TTS",
    url: "https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-en-us-lessac-medium.tar.gz",
    size: "62 MB",
    minRamGb: 2,
    tags: ["Audio", "TTS", "Neural Voice"]
  },
  {
    id: "bge-small-en-v1.5",
    name: "BGE-Small-EN-v1.5 Embeddings",
    description: "384-dimensional dense semantic embedding model powering the local SQLite Memory Vault RAG.",
    type: "Embeddings",
    url: "https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main/model.safetensors",
    size: "133 MB",
    minRamGb: 2,
    tags: ["Embeddings", "RAG", "Vectors"]
  },
  {
    id: "qwen3.5-0.8b-q8",
    name: "Qwen 3.5 0.8B (Q8_0)",
    description: "Full 8-bit precision variant. Zero quantization perplexity degradation for critical tool parsing.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf",
    size: "900 MB",
    minRamGb: 4,
    tags: ["Chat", "High Precision"]
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
        (selectedType === 'CHAT' && m.type === 'GGUF') ||
        (selectedType === 'VISION' && m.type === 'VLM') ||
        (selectedType === 'AUDIO' && (m.type === 'STT' || m.type === 'TTS'));

      return matchesSearch && matchesType;
    });
  }, [models, searchQuery, selectedType]);

  const displayedModels = isExpanded ? filteredModels : models.slice(0, 3);

  return (
    <section id="models" className="py-24 md:py-32 bg-[#0D0F14] border-t border-white/[0.08] relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-6">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B] mb-4">
              <Database className="w-3.5 h-3.5" />
              <span>Offline Model Store</span>
            </div>
            <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-3">
              Verified Open Weights
            </h2>
            <p className="text-lg text-[#A1A1AA]">
              Pre-quantized GGUF, VLM, and ONNX weights validated for mobile ARM processors. Direct HuggingFace downloads.
            </p>
          </div>

          {/* Expand / Collapse Button */}
          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#141720] hover:bg-[#1C202C] border border-white/15 text-sm font-semibold text-white transition-all tactile-button self-start md:self-auto"
          >
            <span>{isExpanded ? 'Collapse Catalog' : 'Explore All 10 Models'}</span>
            {isExpanded ? <ChevronUp className="w-4 h-4 text-[#B6FF3B]" /> : <ChevronDown className="w-4 h-4 text-[#B6FF3B]" />}
          </button>
        </div>

        {/* Filter Bar (Only when expanded) */}
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

        {/* Cleaner Grid with Bigger Text */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {displayedModels.map((m) => {
            const isCopied = copiedId === m.id;

            return (
              <div
                key={m.id}
                className="rounded-2xl surface-2 p-6 flex flex-col justify-between border border-white/10 hover:border-white/20 transition-all group"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-3">
                    <span className="text-xs font-mono px-2.5 py-1 rounded bg-[#07080A] text-[#B6FF3B] border border-white/10 uppercase">
                      {m.type}
                    </span>
                    <span className="text-sm font-mono font-bold text-white tabular-nums">
                      {m.size}
                    </span>
                  </div>

                  <h3 className="text-xl font-bold text-white mb-2 group-hover:text-[#B6FF3B] transition-colors">
                    {m.name}
                  </h3>

                  <p className="text-sm text-[#A1A1AA] leading-relaxed mb-6 font-normal">
                    {m.description}
                  </p>
                </div>

                <div className="space-y-4 pt-4 border-t border-white/5">
                  <div className="flex items-center justify-between text-xs font-mono text-zinc-400">
                    <span>RAM Requirement</span>
                    <span className="text-white font-semibold">Min {m.minRamGb}GB RAM</span>
                  </div>

                  <div className="flex items-center gap-2">
                    <a
                      href={m.url}
                      target="_blank"
                      rel="noreferrer"
                      className="flex-1 inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-white/[0.08] hover:bg-[#B6FF3B] text-white hover:text-[#07080A] text-xs font-bold transition-all"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Download GGUF</span>
                    </a>
                    <button
                      type="button"
                      onClick={() => handleCopyLink(m.url, m.id)}
                      className="p-2.5 rounded-xl bg-white/[0.04] hover:bg-white/[0.1] border border-white/10 text-zinc-400 hover:text-white transition-colors"
                      title="Copy Link"
                    >
                      {isCopied ? <Check className="w-4 h-4 text-[#B6FF3B]" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
