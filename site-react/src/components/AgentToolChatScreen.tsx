import React, { useRef } from 'react';
import { motion, useInView, useReducedMotion } from 'motion/react';
import { Menu, ChevronDown, Cpu, HardDrive, Mic, AudioWaveform } from 'lucide-react';
import { BitLogo } from './BitLogo';
import { ease } from '../lib/motion';

export const AgentToolChatScreen: React.FC = () => {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, amount: 0.3 });
  const shouldReduceMotion = useReducedMotion();

  return (
    <div
      ref={ref}
      className="w-full h-full bg-[#0B0D11] text-zinc-200 flex flex-col justify-between p-3.5 sm:p-4 font-sans select-none text-left"
    >
      {/* Android Status Bar */}
      <div className="flex items-center justify-between text-xs text-zinc-400 pt-0.5 pb-2 font-mono">
        <span className="font-semibold text-white tracking-tight">12:47</span>
        <div className="flex items-center gap-2">
          <span className="text-[#A8A29E] bg-white/[0.06] px-1.5 py-0.5 rounded text-[10px] font-sans">
            Offline
          </span>
          <div className="w-4 h-2 border border-zinc-500 rounded-sm p-[1px] flex items-center">
            <div className="w-full h-full bg-white rounded-[1px]" />
          </div>
        </div>
      </div>

      {/* App Header Bar */}
      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] whitespace-nowrap">
        <div className="flex items-center gap-1.5 shrink-0">
          <Menu className="w-4 h-4 text-zinc-400" />
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/[0.08] border border-white/10 text-xs">
            <BitLogo className="w-2.5 h-2.5 text-white shrink-0" />
            <span className="font-medium text-white tracking-tight">Ready: mistral</span>
            <ChevronDown className="w-3 h-3 text-zinc-400" />
          </div>
        </div>
        <div className="flex items-center gap-1 text-[11px] text-zinc-400 font-mono">
          <span className="w-1.5 h-1.5 rounded-full bg-white" />
          <span>Local Agent</span>
        </div>
      </div>

      {/* Chat Messages Area - Sequential Live Log */}
      <div className="flex-1 py-2.5 space-y-2.5 overflow-hidden flex flex-col justify-start">
        {/* User Prompt */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease }}
          className="flex justify-end"
        >
          <div className="max-w-[92%] rounded-2xl rounded-tr-sm bg-neutral-800 text-white px-3.5 py-2 text-[13px] leading-snug shadow-sm">
            Find local models for my phone and compare RAM usage.
          </div>
        </motion.div>

        {/* Step 1 Tool Call */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, x: -8 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.35, delay: shouldReduceMotion ? 0 : 0.2, ease }}
          className="flex items-center gap-1.5 text-xs text-zinc-300 px-1 font-mono whitespace-nowrap"
        >
          <Cpu className="w-3.5 h-3.5 text-white shrink-0" />
          <span className="font-medium text-white">hw_specs_detect</span>
          <span className="text-zinc-500">&bull;</span>
          <span className="text-zinc-300">Snapdragon 8 Gen 2 &bull; 12GB RAM</span>
        </motion.div>

        {/* Step 2 Tool Call */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, x: -8 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.35, delay: shouldReduceMotion ? 0 : 0.4, ease }}
          className="flex items-center gap-1.5 text-xs text-zinc-300 px-1 font-mono whitespace-nowrap"
        >
          <HardDrive className="w-3.5 h-3.5 text-white shrink-0" />
          <span className="font-medium text-white">model_registry_filter</span>
          <span className="text-zinc-500">&bull;</span>
          <span className="text-zinc-300">3 compatible GGUF found</span>
        </motion.div>

        {/* Formatted Assistant Result Card */}
        <motion.div
          initial={shouldReduceMotion ? false : { opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: shouldReduceMotion ? 0 : 0.6, ease }}
          className="rounded-2xl rounded-tl-sm bg-[#14161C] border border-white/10 p-3 text-xs leading-relaxed space-y-2 text-zinc-200 shadow-md"
        >
          <div className="font-medium text-white text-[13px]">
            Compatible models for 12GB device:
          </div>

          <div className="space-y-1.5 text-xs">
            <div className="p-2 rounded-xl bg-white/[0.04] border border-white/5 flex items-center justify-between">
              <div>
                <div className="font-medium text-white text-[12.5px]">Qwen 2.5 7B Q4_K_M</div>
                <div className="text-[11px] text-zinc-400">4.8 GB RAM &bull; 14.8 tokens/s</div>
              </div>
              <span className="text-[10px] text-white font-mono bg-white/10 px-2 py-0.5 rounded">
                Recommended
              </span>
            </div>

            <div className="p-2 rounded-xl bg-white/[0.04] border border-white/5 flex items-center justify-between">
              <div>
                <div className="font-medium text-white text-[12.5px]">Mistral 7B v0.3 Q4</div>
                <div className="text-[11px] text-zinc-400">4.6 GB RAM &bull; 13.2 tokens/s</div>
              </div>
              <span className="text-[10px] text-zinc-400 font-mono">Tools</span>
            </div>

            <div className="p-2 rounded-xl bg-white/[0.04] border border-white/5 flex items-center justify-between">
              <div>
                <div className="font-medium text-white text-[12.5px]">Llama 3.2 3B Instruct</div>
                <div className="text-[11px] text-zinc-400">2.2 GB RAM &bull; 26.4 tokens/s</div>
              </div>
              <span className="text-[10px] text-zinc-400 font-mono">Ultra-fast</span>
            </div>
          </div>

          <div className="pt-1.5 border-t border-white/[0.06] flex items-center justify-between text-[11px] text-zinc-400 font-mono">
            <span>RAM Available: 7.2 GB</span>
            <span className="text-white font-medium animate-shimmer">0B network</span>
          </div>
        </motion.div>
      </div>

      {/* Bottom Controls */}
      <div className="pt-1 space-y-1.5">
        <div className="flex items-center gap-1">
          <div className="inline-flex rounded-full p-0.5 bg-white/[0.06] border border-white/10 text-[11px] font-medium">
            <span className="px-2 py-0.5 rounded-full bg-white text-black font-semibold">Agent</span>
            <span className="px-2 py-0.5 text-zinc-400">Chat</span>
          </div>
        </div>

        <div className="flex items-center justify-between bg-white/[0.06] border border-white/10 rounded-full px-3 py-2 text-xs text-zinc-400">
          <div className="flex items-center gap-2">
            <span className="text-white text-base leading-none font-light">+</span>
            <span>Ask me anything...</span>
          </div>
          <div className="flex items-center gap-2 text-zinc-300">
            <Mic className="w-3.5 h-3.5" />
            <div className="w-5 h-5 rounded-full bg-white flex items-center justify-center text-black">
              <AudioWaveform className="w-3 h-3 text-black" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
