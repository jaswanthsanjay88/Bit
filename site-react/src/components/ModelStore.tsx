import React, { useState, useEffect, useMemo } from 'react';
import { Search, Download, Copy, Check, Filter, Cpu, Database, Volume2, Eye, ExternalLink } from 'lucide-react';

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

const FALLBACK_MODELS: ModelItem[] = [
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
    id: "qwen3.5-0.8b-q8",
    name: "Qwen 3.5 0.8B (Q8_0)",
    description: "Full 8-bit precision variant. Zero quantization perplexity degradation for critical tool parsing.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf",
    size: "900 MB",
    minRamGb: 4,
    tags: ["Chat", "Tool Calling", "High Quality"]
  },
  {
    id: "qwen3.5-4b-q4km",
    name: "Qwen 3.5 4B",
    description: "Balanced reasoning engine with high-throughput coding and planning abilities. Tested on Dimensity 9200 / 8 Gen 2.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
    size: "2.7 GB",
    minRamGb: 6,
    tags: ["Chat", "Tool Calling", "Tested"]
  },
  {
    id: "qwen3.5-9b-q4km",
    name: "Qwen 3.5 9B",
    description: "Heavyweight reasoning and agent synthesis. Recommended for flagships with 8GB to 12GB RAM.",
    type: "GGUF",
    url: "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf",
    size: "5.5 GB",
    minRamGb: 8,
    tags: ["Chat", "Tool Calling", "High Quality"]
  },
  {
    id: "lfm2-350m-q8",
    name: "LFM2 350M",
    description: "Ultra-fast liquid foundation model from Liquid AI. Delivers 60+ tokens/sec on Cortex-A78 cores.",
    type: "GGUF",
    url: "https://huggingface.co/LiquidAI/LFM2-350M-GGUF/resolve/main/LFM2-350M-Q8_0.gguf",
    size: "400 MB",
    minRamGb: 3,
    tags: ["Chat", "Ultra Fast", "Tested"]
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
    tags: ["Audio", "STT", "Ultra Fast"]
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
    tags: ["Embeddings", "RAG", "Tested"]
  }
];

export const ModelStore: React.FC = () => {
  const [models, setModels] = useState<ModelItem[]>(FALLBACK_MODELS);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedRam, setSelectedRam] = useState('ALL');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    fetch('/api/models.json')
      .then((res) => {
        if (!res.ok) throw new Error('Fetch failed');
        return res.json();
      })
      .then((data) => {
        if (data && data.models && Array.isArray(data.models)) {
          setModels(data.models);
        }
      })
      .catch(() => {
        // Keeps fallback models seamlessly
      });
  }, []);

  const handleCopyLink = (url: string, id: string) => {
    navigator.clipboard.writeText(url);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const filteredModels = useMemo(() => {
    return models.filter((m) => {
      // Search matching
      const matchesSearch =
        m.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        m.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
        m.tags.some((t) => t.toLowerCase().includes(searchQuery.toLowerCase()));

      // Category matching
      const matchesType =
        selectedType === 'ALL' ||
        (selectedType === 'CHAT' && m.type === 'GGUF') ||
        (selectedType === 'VISION' && m.type === 'VLM') ||
        (selectedType === 'AUDIO' && (m.type === 'STT' || m.type === 'TTS')) ||
        (selectedType === 'EMBEDDINGS' && m.type === 'Embeddings');

      // RAM matching
      const matchesRam =
        selectedRam === 'ALL' ||
        (selectedRam === 'LOW' && m.minRamGb <= 4) ||
        (selectedRam === 'MID' && m.minRamGb > 4 && m.minRamGb <= 6) ||
        (selectedRam === 'HIGH' && m.minRamGb >= 8);

      return matchesSearch && matchesType && matchesRam;
    });
  }, [models, searchQuery, selectedType, selectedRam]);

  return (
    <section id="models" className="py-24 bg-zinc-950 border-t border-white/[0.08] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-6">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
              <Database className="w-3.5 h-3.5" />
              <span>Offline Weights Matrix</span>
            </div>
            <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white mb-4">
              Model Store Catalog
            </h2>
            <p className="text-sm sm:text-base text-zinc-400 font-normal">
              Pre-quantized GGUF, VLM, and ONNX weights validated for mobile ARM processors. Direct HuggingFace links with zero DRM lock-in.
            </p>
          </div>

          <div className="flex items-center gap-2 text-xs font-mono text-zinc-400">
            <span>Showing:</span>
            <strong className="text-white tabular-nums">{filteredModels.length}</strong>
            <span>verified models</span>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="p-4 rounded-xl glass-panel border border-white/10 mb-8 space-y-4">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            {/* Search Input */}
            <div className="relative w-full md:w-80">
              <Search className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search models, tags, architecture..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-10 pr-4 py-2 text-xs bg-zinc-900/90 border border-white/10 rounded-lg text-white placeholder-zinc-500 focus:outline-none focus:border-white/30 font-sans"
              />
            </div>

            {/* Category Filter Chips */}
            <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
              {[
                { label: 'All', value: 'ALL' },
                { label: 'Chat LLMs', value: 'CHAT' },
                { label: 'Vision (VLM)', value: 'VISION' },
                { label: 'Audio (STT/TTS)', value: 'AUDIO' },
                { label: 'Embeddings', value: 'EMBEDDINGS' }
              ].map((chip) => (
                <button
                  key={chip.value}
                  type="button"
                  onClick={() => setSelectedType(chip.value)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-mono transition-all ${
                    selectedType === chip.value
                      ? 'bg-white text-black font-semibold shadow-sm'
                      : 'bg-zinc-900/80 text-zinc-400 hover:text-white border border-white/5'
                  }`}
                >
                  {chip.label}
                </button>
              ))}
            </div>

            {/* RAM Filter */}
            <div className="flex items-center gap-1.5 self-start md:self-auto">
              <span className="text-[11px] font-mono text-zinc-500 mr-1">RAM:</span>
              {[
                { label: 'All', value: 'ALL' },
                { label: '<=4GB', value: 'LOW' },
                { label: '6GB', value: 'MID' },
                { label: '8GB+', value: 'HIGH' }
              ].map((chip) => (
                <button
                  key={chip.value}
                  type="button"
                  onClick={() => setSelectedRam(chip.value)}
                  className={`px-2 py-1 rounded text-[11px] font-mono transition-all ${
                    selectedRam === chip.value
                      ? 'bg-zinc-800 text-white font-semibold border border-white/20'
                      : 'text-zinc-500 hover:text-zinc-300'
                  }`}
                >
                  {chip.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Models Grid */}
        {filteredModels.length === 0 ? (
          <div className="text-center py-16 rounded-xl border border-white/5 bg-zinc-900/30">
            <p className="text-sm font-mono text-zinc-500">No models match the selected filter criteria.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {filteredModels.map((m) => {
              const isCopied = copiedId === m.id;
              return (
                <div
                  key={m.id}
                  className="rounded-xl glass-panel p-5 border border-white/[0.08] hover:border-white/20 flex flex-col justify-between transition-all interactive-card group"
                >
                  <div>
                    {/* Header */}
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-zinc-900 text-zinc-400 border border-white/10 uppercase mr-2">
                          {m.type}
                        </span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-zinc-900 text-zinc-500 border border-white/5">
                          Min {m.minRamGb}GB RAM
                        </span>
                      </div>
                      <span className="text-xs font-mono font-bold text-zinc-300 tabular-nums">
                        {m.size}
                      </span>
                    </div>

                    <h3 className="text-base font-bold text-white mb-2 group-hover:text-zinc-100">
                      {m.name}
                    </h3>
                    <p className="text-xs text-zinc-400 leading-relaxed mb-4 font-normal">
                      {m.description}
                    </p>

                    {/* Tags */}
                    <div className="flex flex-wrap gap-1.5 mb-6">
                      {m.tags.map((tg, tIdx) => (
                        <span
                          key={tIdx}
                          className="text-[10px] font-mono px-2 py-0.5 rounded bg-zinc-900/60 text-zinc-400 border border-white/5"
                        >
                          {tg}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Action buttons */}
                  <div className="flex items-center gap-2 pt-3 border-t border-white/5">
                    <a
                      href={m.url}
                      target="_blank"
                      rel="noreferrer"
                      className="flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg bg-zinc-900 hover:bg-zinc-800 border border-white/10 text-xs font-medium text-white transition-colors"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Download GGUF</span>
                    </a>
                    <button
                      type="button"
                      onClick={() => handleCopyLink(m.url, m.id)}
                      className="p-2 rounded-lg bg-zinc-900 hover:bg-zinc-800 border border-white/10 text-zinc-400 hover:text-white transition-colors"
                      title="Copy Direct URL"
                    >
                      {isCopied ? (
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                      ) : (
                        <Copy className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
};
