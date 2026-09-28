import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Cpu, Smartphone, Check, ShieldCheck } from 'lucide-react';
import { springs } from '../lib/motion';

interface RamTier {
  id: string;
  name: string;
  ramLabel: string;
  models: string[];
  chips: string;
  perf: string;
}

const TIERS: RamTier[] = [
  {
    id: 'entry',
    name: 'Entry Tier',
    ramLabel: '4GB RAM',
    models: ['Qwen 3.5 0.8B', 'LFM2 350M', 'Whisper Tiny STT'],
    chips: 'Snapdragon 695, Helio G99, Exynos 1280 or equivalent',
    perf: 'Fast 45+ tokens/sec with under 800MB RAM footprint'
  },
  {
    id: 'balanced',
    name: 'Balanced Tier',
    ramLabel: '6GB RAM',
    models: ['Qwen 3.5 4B', 'Moondream 2 VLM', 'Piper Neural TTS'],
    chips: 'Snapdragon 7-series, Dimensity 8000 series or equivalent',
    perf: 'Strong coding, multimodal vision chat, 25-35 tokens/sec'
  },
  {
    id: 'flagship',
    name: 'Flagship Tier',
    ramLabel: '8GB+ RAM',
    models: ['Qwen 3.5 9B', 'Large Context Memory Vault RAG'],
    chips: 'Snapdragon 8 Gen 2/3, Dimensity 9200+ or equivalent',
    perf: 'Heavyweight reasoning and tool chaining, 35-50 tokens/sec'
  }
];

export const DeviceRequirements: React.FC = () => {
  const [activeTierId, setActiveTierId] = useState('balanced');
  const activeTier = TIERS.find((t) => t.id === activeTierId) || TIERS[1];

  return (
    <section id="requirements" className="py-20 bg-black border-t border-white/[0.08] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-6">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
              <Smartphone className="w-3.5 h-3.5 text-zinc-300" />
              <span>Hardware Compatibility</span>
            </div>
            <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white mb-4">
              Device requirements & RAM tiers.
            </h2>
            <p className="text-sm sm:text-base text-zinc-400">
              Check your hardware before downloading. BIT scales dynamically from entry-level phones to modern flagships.
            </p>
          </div>

          {/* OS Baseline Pill */}
          <div className="flex items-center gap-3 p-3 rounded-xl bg-zinc-900/60 border border-white/10 text-xs font-mono text-zinc-300">
            <div className="w-2 h-2 rounded-full bg-emerald-500" />
            <span>Android 12+ (API 31+) &bull; ARM64-v8a</span>
          </div>
        </div>

        {/* 3 Tier Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {TIERS.map((tier) => {
            const isSelected = activeTierId === tier.id;

            return (
              <motion.div
                key={tier.id}
                whileHover={{ y: -3 }}
                transition={springs.snappy}
                onClick={() => setActiveTierId(tier.id)}
                className={`cursor-pointer p-6 rounded-2xl flex flex-col justify-between border transition-all duration-150 ${
                  isSelected
                    ? 'bg-zinc-900/90 border-white/30 shadow-[0_0_30px_rgba(255,255,255,0.06)]'
                    : 'bg-zinc-950/60 border-white/[0.08] hover:border-white/15'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="text-xs font-mono text-zinc-400 uppercase tracking-wider">
                      {tier.name}
                    </span>
                    <span className="text-xs font-mono px-2.5 py-1 rounded-md bg-zinc-800 text-white font-bold border border-white/10">
                      {tier.ramLabel}
                    </span>
                  </div>

                  <h3 className="text-lg font-bold text-white mb-2">
                    Verified Models
                  </h3>

                  <div className="space-y-2 mb-6">
                    {tier.models.map((mod, mIdx) => (
                      <div key={mIdx} className="flex items-center gap-2 text-xs text-zinc-300 font-mono">
                        <Check className="w-3 h-3 text-zinc-400" />
                        <span>{mod}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="pt-4 border-t border-white/5 space-y-2 text-xs">
                  <div>
                    <span className="font-mono text-[10px] text-zinc-500 uppercase block">Supported Silicon</span>
                    <span className="text-zinc-300 text-[11px]">{tier.chips}</span>
                  </div>
                  <div>
                    <span className="font-mono text-[10px] text-zinc-500 uppercase block">Expected Throughput</span>
                    <span className="text-zinc-400 text-[11px]">{tier.perf}</span>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
