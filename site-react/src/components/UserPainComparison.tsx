import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { ShieldCheck, WifiOff, Lock, Cpu, Zap, AlertTriangle, Check, ArrowRight } from 'lucide-react';
import { springs } from '../lib/motion';

interface ComparisonPoint {
  id: string;
  category: string;
  cloudPain: {
    title: string;
    description: string;
    metric: string;
  };
  sovereignBenefit: {
    title: string;
    description: string;
    metric: string;
  };
}

const COMPARISONS: ComparisonPoint[] = [
  {
    id: 'privacy',
    category: 'DATA SURVEILLANCE',
    cloudPain: {
      title: 'Prompt & Voice Telemetry',
      description: 'Your private chats, microphone recordings, documents, and device tokens are dispatched to third-party data centers.',
      metric: 'Every request leaves phone'
    },
    sovereignBenefit: {
      title: 'Zero Egress Guarantee',
      description: 'Encrypted SQLite vector storage and local model inference. Everything stays in your phone sandboxed process.',
      metric: '0 Bytes network egress'
    }
  },
  {
    id: 'offline',
    category: 'NETWORK RELIABILITY',
    cloudPain: {
      title: 'Locked Out in Dead Zones',
      description: 'Airplane mode, remote transit, server capacity errors (503), or provider outages render cloud assistants useless.',
      metric: '100% Cloud dependent'
    },
    sovereignBenefit: {
      title: 'True Airplane Mode Autonomy',
      description: 'Speech recognition, reasoning, and speech synthesis execute completely offline with zero cell service.',
      metric: '100% Offline execution'
    }
  },
  {
    id: 'reliability',
    category: 'TOOL ACCURACY',
    cloudPain: {
      title: 'Hallucinated JSON Arguments',
      description: 'Unconstrained models guess JSON formats, drop required fields, or invent imaginary API endpoints.',
      metric: 'Frequent schema drift'
    },
    sovereignBenefit: {
      title: 'GBNF Grammar Clamped',
      description: 'Backus-Naur context-free grammars constrain token logits at decode time. Malformed JSON is mathematically impossible.',
      metric: '100% Valid JSON AST'
    }
  },
  {
    id: 'cost',
    category: 'FINANCIAL CONTROL',
    cloudPain: {
      title: 'Subscription Tollgates',
      description: '$20 to $200 recurring monthly subscriptions for access to your own thought process and automated workflows.',
      metric: '$240+ / year recurring'
    },
    sovereignBenefit: {
      title: 'Free & Open Source Forever',
      description: 'Apache-2.0 licensed code. Own your intelligence. Download verified open-weights from HuggingFace without tollgates.',
      metric: '$0.00 forever'
    }
  }
];

export const UserPainComparison: React.FC = () => {
  const [activeMode, setActiveMode] = useState<'sovereign' | 'cloud'>('sovereign');

  return (
    <section id="comparison" className="py-24 bg-black border-t border-white/[0.08] relative overflow-hidden">
      {/* Background architectural grid */}
      <div className="absolute inset-0 bg-grid-architectural opacity-40 pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        {/* Header */}
        <div className="max-w-3xl mx-auto text-center mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
            <ShieldCheck className="w-3.5 h-3.5 text-zinc-300" />
            <span>The Reality of AI Today</span>
          </div>
          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white mb-4">
            Why sovereign AI is non-negotiable.
          </h2>
          <p className="text-sm sm:text-base text-zinc-400 leading-relaxed max-w-2xl mx-auto">
            Cloud AI treats your private data as training fuel and charges you a monthly toll. BIT restores complete ownership directly to your silicon.
          </p>

          {/* Mode Switcher Pill */}
          <div className="mt-8 inline-flex items-center p-1.5 rounded-xl bg-zinc-950 border border-white/10 shadow-lg">
            <button
              type="button"
              onClick={() => setActiveMode('sovereign')}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-lg text-xs font-mono transition-all duration-150 tactile-button ${
                activeMode === 'sovereign'
                  ? 'bg-zinc-100 text-black font-bold shadow-md'
                  : 'text-zinc-400 hover:text-white'
              }`}
            >
              <Check className="w-3.5 h-3.5" />
              <span>BIT Sovereign</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveMode('cloud')}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-lg text-xs font-mono transition-all duration-150 tactile-button ${
                activeMode === 'cloud'
                  ? 'bg-zinc-800 text-white font-bold shadow-md'
                  : 'text-zinc-400 hover:text-white'
              }`}
            >
              <AlertTriangle className="w-3.5 h-3.5" />
              <span>Cloud Assistants</span>
            </button>
          </div>
        </div>

        {/* Dynamic Comparison Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {COMPARISONS.map((item) => {
            const isSovereign = activeMode === 'sovereign';
            const data = isSovereign ? item.sovereignBenefit : item.cloudPain;

            return (
              <motion.div
                key={item.id}
                layout
                transition={springs.snappy}
                className={`p-6 rounded-2xl flex flex-col justify-between border transition-all duration-200 ${
                  isSovereign
                    ? 'bg-zinc-950/80 border-white/[0.12] hover:border-white/25 shadow-[0_4px_24px_rgba(0,0,0,0.6)]'
                    : 'bg-zinc-950/40 border-zinc-800/80 hover:border-zinc-700'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-4">
                    <span className="text-[10px] font-mono tracking-wider text-zinc-500 uppercase">
                      {item.category}
                    </span>
                    <span
                      className={`text-[10px] font-mono px-2 py-0.5 rounded border ${
                        isSovereign
                          ? 'bg-zinc-900 text-zinc-200 border-white/10'
                          : 'bg-zinc-900/60 text-zinc-500 border-zinc-800'
                      }`}
                    >
                      {data.metric}
                    </span>
                  </div>

                  <h3 className="text-base font-bold text-white mb-2 tracking-tight">
                    {data.title}
                  </h3>

                  <p className="text-xs text-zinc-400 leading-relaxed mb-6 font-normal">
                    {data.description}
                  </p>
                </div>

                <div className="pt-4 border-t border-white/5 flex items-center justify-between text-[11px] font-mono text-zinc-400">
                  <span>Architecture:</span>
                  <span className="text-zinc-200 font-medium">
                    {isSovereign ? '100% Local Silicon' : 'Remote Data Center'}
                  </span>
                </div>
              </motion.div>
            );
          })}
        </div>

        {/* Bottom Banner */}
        <div className="mt-12 p-6 rounded-2xl bg-zinc-950 border border-white/10 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-zinc-900 border border-white/10 flex items-center justify-center shrink-0">
              <Lock className="w-5 h-5 text-zinc-300" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-white">Your privacy is not a feature toggle.</h4>
              <p className="text-xs text-zinc-400">BIT contains zero telemetry, analytics, ad networks, or cloud sync daemons.</p>
            </div>
          </div>
          <a
            href="#terminal"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-zinc-100 hover:bg-white text-black font-semibold text-xs transition-all tactile-button shrink-0"
          >
            <span>Deploy to Android</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>
    </section>
  );
};
