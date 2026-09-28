import React, { useRef } from 'react';
import { motion, useInView, useReducedMotion } from 'motion/react';
import { ArrowLeft, Search, FileText, Plus, MoreVertical, Edit3, Brain, DownloadCloud } from 'lucide-react';
import { ease } from '../lib/motion';

export const MemoryVaultPopulatedScreen: React.FC = () => {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, amount: 0.3 });
  const shouldReduceMotion = useReducedMotion();

  const documents = [
    { name: 'contract_nda_2025.pdf', size: '1.2 MB', vectors: '142 chunks' },
    { name: 'financial_projections_q3.xlsx', size: '840 KB', vectors: '96 chunks' },
    { name: 'health_records_biometrics.pdf', size: '2.1 MB', vectors: '210 chunks' },
    { name: 'patent_sovereign_ai.pdf', size: '3.4 MB', vectors: '380 chunks' },
    { name: 'project_architecture.md', size: '64 KB', vectors: '28 chunks' },
  ];

  return (
    <div
      ref={ref}
      className="w-full h-full bg-[#0B0D11] text-zinc-100 flex flex-col justify-between p-3.5 sm:p-4 font-sans select-none text-left"
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

      {/* Vault Top Bar */}
      <div className="flex items-center justify-between pb-2.5 border-b border-white/[0.08]">
        <div className="flex items-center gap-2">
          <ArrowLeft className="w-4 h-4 text-zinc-300" />
          <span className="font-semibold text-white text-[15px] tracking-tight">Memory vault</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-white/[0.08] text-xs text-zinc-300 border border-white/10">
            <DownloadCloud className="w-3 h-3 text-white" />
            <span>Backup</span>
          </div>
          <div className="w-8 h-4 bg-white rounded-full p-0.5 flex items-center justify-end">
            <div className="w-3 h-3 bg-black rounded-full" />
          </div>
          <MoreVertical className="w-4 h-4 text-zinc-400" />
        </div>
      </div>

      {/* Search Input */}
      <div className="pt-2">
        <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/[0.06] border border-white/10 text-xs text-zinc-400">
          <Search className="w-3.5 h-3.5 text-zinc-400" />
          <span>Search notes, memories, docs</span>
        </div>
      </div>

      {/* Top 2 Categories Cards */}
      <div className="grid grid-cols-2 gap-2 pt-2.5">
        <div className="p-3 rounded-2xl bg-[#14161C] border border-white/10">
          <div className="flex items-center gap-1.5 text-zinc-300 mb-1">
            <Edit3 className="w-3.5 h-3.5 text-white" />
            <span className="text-xs font-medium text-white">My notes</span>
          </div>
          <span className="text-[11px] text-zinc-400 font-mono">14 files</span>
        </div>

        <div className="p-3 rounded-2xl bg-[#14161C] border border-white/10">
          <div className="flex items-center gap-1.5 text-zinc-300 mb-1">
            <Brain className="w-3.5 h-3.5 text-white" />
            <span className="text-xs font-medium text-white">AI memory</span>
          </div>
          <span className="text-[11px] text-zinc-400 font-mono">82 facts</span>
        </div>
      </div>

      {/* Documents Section Header */}
      <div className="pt-3 flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-xs font-medium text-white">
          <FileText className="w-3.5 h-3.5 text-zinc-400" />
          <span>Documents (5 stored)</span>
        </div>
        <button className="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-white/10 border border-white/10 text-[11px] text-zinc-200">
          <Plus className="w-3 h-3 text-white" />
          <span>Add</span>
        </button>
      </div>

      {/* Staggered File Rows */}
      <div className="flex-1 py-1.5 space-y-1.5 overflow-hidden flex flex-col justify-start">
        {documents.map((doc, idx) => (
          <motion.div
            key={idx}
            initial={shouldReduceMotion ? false : { opacity: 0, x: -10 }}
            animate={isInView ? { opacity: 1, x: 0 } : {}}
            transition={{
              duration: 0.35,
              delay: shouldReduceMotion ? 0 : 0.2 + idx * 0.06,
              ease,
            }}
            className="p-2 rounded-xl bg-white/[0.03] border border-white/5 flex items-center justify-between text-xs"
          >
            <div className="flex items-center gap-2 overflow-hidden">
              <FileText className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
              <div className="truncate">
                <div className="font-medium text-white text-[12px] truncate">{doc.name}</div>
                <div className="text-[10px] text-zinc-400 font-mono">
                  {doc.size} &bull; {doc.vectors}
                </div>
              </div>
            </div>

            {/* Tiny Indexed Pill with Path-Drawn Checkmark */}
            <motion.div
              initial={shouldReduceMotion ? false : { opacity: 0, scale: 0.8 }}
              animate={isInView ? { opacity: 1, scale: 1 } : {}}
              transition={{
                duration: 0.3,
                delay: shouldReduceMotion ? 0 : 0.5 + idx * 0.08,
              }}
              className="inline-flex items-center gap-1 shrink-0 px-1.5 py-0.5 rounded bg-white/[0.06] text-[10px] text-zinc-300 font-mono"
            >
              <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 12 12" fill="none">
                <motion.path
                  d="M2 6.5L4.5 9L10 3.5"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  initial={shouldReduceMotion ? false : { pathLength: 0 }}
                  animate={isInView ? { pathLength: 1 } : {}}
                  transition={{
                    duration: 0.3,
                    delay: shouldReduceMotion ? 0 : 0.6 + idx * 0.08,
                    ease,
                  }}
                />
              </svg>
              <span>Indexed</span>
            </motion.div>
          </motion.div>
        ))}
      </div>

      {/* Floating Action Pill */}
      <div className="pt-1.5 flex justify-end">
        <button className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white text-black text-xs font-semibold shadow-md">
          <Plus className="w-3.5 h-3.5 text-black" />
          <span>New note</span>
        </button>
      </div>
    </div>
  );
};
