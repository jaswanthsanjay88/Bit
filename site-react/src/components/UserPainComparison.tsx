import React from 'react';
import { motion } from 'motion/react';
import { X, Check, ShieldCheck, ArrowRight } from 'lucide-react';
import { springs } from '../lib/motion';

interface ComparisonRow {
  dimension: string;
  cloud: {
    title: string;
    detail: string;
  };
  bit: {
    title: string;
    detail: string;
  };
}

const COMPARISON_ROWS: ComparisonRow[] = [
  {
    dimension: 'Data Egress & Privacy',
    cloud: {
      title: 'Dispatched to Cloud Servers',
      detail: 'Every prompt, voice recording, and file leaves your phone for corporate data centers.'
    },
    bit: {
      title: '0.00 Bytes Network Egress',
      detail: 'Encrypted local SQLite storage. Inference executes entirely in your sandboxed process.'
    }
  },
  {
    dimension: 'Offline Reliability',
    cloud: {
      title: '100% Network Dependent',
      detail: 'Completely broken in airplane mode, subway transit, remote areas, or server 503 outages.'
    },
    bit: {
      title: 'True Airplane Mode Autonomy',
      detail: 'Speech-to-text, reasoning, tool execution, and voice synthesis run completely offline.'
    }
  },
  {
    dimension: 'Tool Calling Accuracy',
    cloud: {
      title: 'Hallucinated Schema Drift',
      detail: 'Unconstrained models guess JSON formats, drop parameters, and invent non-existent APIs.'
    },
    bit: {
      title: 'GBNF Grammar Clamped',
      detail: 'Context-free Backus-Naur grammars mathematically guarantee 100% valid JSON AST decoding.'
    }
  },
  {
    dimension: 'Pricing & Licensing',
    cloud: {
      title: '$240 to $600+ / Year',
      detail: 'Continuous recurring subscription tollgates just to access your own automated workflows.'
    },
    bit: {
      title: 'Free & Open Source Forever',
      detail: 'Licensed under Apache 2.0. Verified open weights downloaded directly with zero tollgates.'
    }
  },
  {
    dimension: 'Telemetry & Tracking',
    cloud: {
      title: 'Monetized & Retained',
      detail: 'User queries are cataloged for commercial retraining and analytics profiling.'
    },
    bit: {
      title: 'Zero Telemetry Audited',
      detail: 'Contains zero analytics SDKs, zero ad networks, and zero remote logging daemons.'
    }
  }
];

export const UserPainComparison: React.FC = () => {
  return (
    <section id="comparison" className="py-28 md:py-36 bg-[#07080A] relative">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-20">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B] mb-4">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Architecture Breakdown</span>
          </div>
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-4">
            Cloud AI vs Sovereign AI
          </h2>
          <p className="text-lg text-[#A1A1AA]">
            Why running intelligence on local silicon is fundamentally superior to renting cloud tokens.
          </p>
        </div>

        {/* Two-Column Comparison Table (Red-tinted Cloud vs Lime-tinted BIT) */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-stretch">
          
          {/* Column 1: Cloud AI (Red-tinted) */}
          <div className="rounded-2xl p-8 sm:p-10 bg-[#0E1015] border border-red-500/20 shadow-xl space-y-8 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between pb-6 border-b border-white/5">
                <div>
                  <span className="text-xs font-mono uppercase tracking-wider text-red-400">The Cloud Trap</span>
                  <h3 className="text-2xl sm:text-3xl font-bold text-white mt-1">Cloud Assistants</h3>
                </div>
                <div className="w-10 h-10 rounded-xl bg-red-950/40 border border-red-500/30 flex items-center justify-center text-red-400">
                  <X className="w-5 h-5" />
                </div>
              </div>

              <div className="space-y-6 pt-6">
                {COMPARISON_ROWS.map((row, idx) => (
                  <div key={idx} className="space-y-1.5 pb-5 border-b border-white/5 last:border-0 last:pb-0">
                    <span className="text-xs font-mono text-zinc-500 uppercase tracking-wider block">
                      {row.dimension}
                    </span>
                    <div className="text-base font-semibold text-zinc-200">
                      {row.cloud.title}
                    </div>
                    <p className="text-sm text-zinc-400 font-normal leading-relaxed">
                      {row.cloud.detail}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            <div className="pt-6 border-t border-white/5 text-xs text-red-400/80 font-mono">
              Result: You remain dependent on external servers and subscription fees.
            </div>
          </div>

          {/* Column 2: BIT Sovereign (Electric Lime-tinted with Glow) */}
          <div className="rounded-2xl p-8 sm:p-10 bg-[#0E1508]/80 border-2 border-[#B6FF3B]/40 shadow-[0_0_50px_rgba(182,255,59,0.12)] space-y-8 flex flex-col justify-between relative">
            <div>
              <div className="flex items-center justify-between pb-6 border-b border-[#B6FF3B]/15">
                <div>
                  <span className="text-xs font-mono uppercase tracking-wider text-[#B6FF3B]">True Sovereignty</span>
                  <h3 className="text-2xl sm:text-3xl font-bold text-white mt-1">BIT On-Device</h3>
                </div>
                <div className="w-10 h-10 rounded-xl bg-[#B6FF3B]/20 border border-[#B6FF3B]/40 flex items-center justify-center text-[#B6FF3B]">
                  <Check className="w-5 h-5" />
                </div>
              </div>

              <div className="space-y-6 pt-6">
                {COMPARISON_ROWS.map((row, idx) => (
                  <div key={idx} className="space-y-1.5 pb-5 border-b border-white/5 last:border-0 last:pb-0">
                    <span className="text-xs font-mono text-[#B6FF3B] uppercase tracking-wider block">
                      {row.dimension}
                    </span>
                    <div className="text-base font-semibold text-white">
                      {row.bit.title}
                    </div>
                    <p className="text-sm text-zinc-300 font-normal leading-relaxed">
                      {row.bit.detail}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            <div className="pt-6 border-t border-[#B6FF3B]/15 text-xs text-[#B6FF3B] font-mono flex items-center justify-between">
              <span>Result: Total data privacy, zero subscriptions, 100% offline.</span>
              <ShieldCheck className="w-4 h-4 shrink-0" />
            </div>
          </div>

        </div>
      </div>
    </section>
  );
};
