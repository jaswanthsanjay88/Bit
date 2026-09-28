import React from 'react';
import { motion } from 'motion/react';
import { Smartphone, Check, Zap } from 'lucide-react';
import { springs } from '../lib/motion';

export const DeviceRequirements: React.FC = () => {
  return (
    <section id="requirements" className="py-28 md:py-36 bg-[#0D0F14] border-t border-white/[0.08] relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-20">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B] mb-4">
            <Smartphone className="w-3.5 h-3.5" />
            <span>Hardware Compatibility</span>
          </div>
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-4">
            Find your hardware tier.
          </h2>
          <p className="text-lg text-[#A1A1AA]">
            BIT dynamically optimizes quantization and thread pools for your specific Android silicon.
          </p>
          <div className="mt-4 inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#07080A] border border-white/10 text-xs font-mono text-zinc-300">
            <span className="w-2 h-2 rounded-full bg-[#B6FF3B]" />
            <span>Baseline: Android 12+ (API 31+) &bull; 64-bit ARM64-v8a</span>
          </div>
        </div>

        {/* 3 Tier Cards with Highlighted Middle Card */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-stretch">
          
          {/* Tier 1: Entry */}
          <div className="surface-2 rounded-2xl p-8 flex flex-col justify-between border border-white/10 hover:border-white/20 transition-all duration-200">
            <div>
              <div className="text-xs font-mono uppercase tracking-wider text-zinc-400 mb-2">Entry Phones</div>
              <div className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight tabular-nums mb-4">
                4GB <span className="text-lg font-normal text-zinc-500">RAM</span>
              </div>
              <p className="text-sm text-[#A1A1AA] mb-6 font-normal">
                Perfect for entry-level devices. Runs instant chat and speech recognition with zero frame drops.
              </p>

              <div className="space-y-3 mb-8">
                <span className="text-xs font-mono text-zinc-400 uppercase tracking-wider block">Recommended Models</span>
                {['Qwen 3.5 0.8B (Q4_K_M)', 'LFM2 350M (Ultra Fast)', 'Sherpa-ONNX Whisper Tiny'].map((m, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs text-zinc-300">
                    <Check className="w-3.5 h-3.5 text-zinc-500" />
                    <span>{m}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="pt-6 border-t border-white/5 text-xs text-zinc-400 space-y-1">
              <div>Silicon: Snapdragon 695, Helio G99</div>
              <div className="text-[#B6FF3B] font-mono">Speed: 45+ tokens/sec</div>
            </div>
          </div>

          {/* Tier 2: Balanced (RECOMMENDED - Highlighted with Lime Border & Glow) */}
          <div className="rounded-2xl p-8 flex flex-col justify-between bg-[#11170A] border-2 border-[#B6FF3B] shadow-[0_0_50px_rgba(182,255,59,0.25)] relative transform md:-translate-y-2">
            {/* Top Recommended Tag */}
            <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 px-4 py-1 rounded-full bg-[#B6FF3B] text-[#07080A] text-xs font-bold font-mono tracking-wider uppercase shadow-md flex items-center gap-1.5">
              <Zap className="w-3.5 h-3.5 fill-[#07080A]" />
              <span>Recommended</span>
            </div>

            <div>
              <div className="text-xs font-mono uppercase tracking-wider text-[#B6FF3B] mb-2 mt-2">Mid-Range Sweet Spot</div>
              <div className="text-5xl sm:text-6xl font-extrabold text-[#B6FF3B] tracking-tight tabular-nums mb-4">
                6GB <span className="text-lg font-normal text-zinc-400">RAM</span>
              </div>
              <p className="text-sm text-zinc-300 mb-6 font-normal">
                The optimal balance of deep reasoning, coding capabilities, and multimodal vision processing.
              </p>

              <div className="space-y-3 mb-8">
                <span className="text-xs font-mono text-[#B6FF3B] uppercase tracking-wider block">Recommended Models</span>
                {['Qwen 3.5 4B (High Quality)', 'Moondream 2 VLM (Vision)', 'Piper Neural TTS (22kHz)'].map((m, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs text-white">
                    <Check className="w-3.5 h-3.5 text-[#B6FF3B]" />
                    <span>{m}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="pt-6 border-t border-[#B6FF3B]/20 text-xs text-zinc-300 space-y-1">
              <div>Silicon: Snapdragon 7+ Gen 2, Dimensity 8100</div>
              <div className="text-[#B6FF3B] font-mono">Speed: 25 - 35 tokens/sec</div>
            </div>
          </div>

          {/* Tier 3: Flagship */}
          <div className="surface-2 rounded-2xl p-8 flex flex-col justify-between border border-white/10 hover:border-white/20 transition-all duration-200">
            <div>
              <div className="text-xs font-mono uppercase tracking-wider text-zinc-400 mb-2">Flagship Power</div>
              <div className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight tabular-nums mb-4">
                8GB+ <span className="text-lg font-normal text-zinc-500">RAM</span>
              </div>
              <p className="text-sm text-[#A1A1AA] mb-6 font-normal">
                Maximum context windows, dense SQLite vector document RAG, and heavyweight agent planning.
              </p>

              <div className="space-y-3 mb-8">
                <span className="text-xs font-mono text-zinc-400 uppercase tracking-wider block">Recommended Models</span>
                {['Qwen 3.5 9B (Complex Reasoning)', 'Large Context Memory Vault', 'High Precision Q8_0 Quants'].map((m, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs text-zinc-300">
                    <Check className="w-3.5 h-3.5 text-zinc-500" />
                    <span>{m}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="pt-6 border-t border-white/5 text-xs text-zinc-400 space-y-1">
              <div>Silicon: Snapdragon 8 Gen 2/3, Dimensity 9200+</div>
              <div className="text-[#B6FF3B] font-mono">Speed: 35 - 50 tokens/sec</div>
            </div>
          </div>

        </div>
      </div>
    </section>
  );
};
