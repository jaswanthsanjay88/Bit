import React from 'react';
import { Shield, Lock, Cpu, Star, Zap, HardDrive } from 'lucide-react';

interface StatItem {
  icon: React.ReactNode;
  value: string;
  label: string;
  detail: string;
}

const STATS: StatItem[] = [
  {
    icon: <Lock className="w-4 h-4 text-zinc-400" />,
    value: '100% Offline',
    label: 'Zero Network Egress',
    detail: 'Never pings an external server. Fully functional in airplane mode.'
  },
  {
    icon: <Shield className="w-4 h-4 text-zinc-400" />,
    value: '0 Bytes',
    label: 'Data Leaves Device',
    detail: 'Encrypted SQLite vector storage and local token caches.'
  },
  {
    icon: <Cpu className="w-4 h-4 text-zinc-400" />,
    value: 'Native C++',
    label: 'llama.cpp & GGUF',
    detail: 'Direct JNI bridge with ARM64 Neon SIMD and NPU acceleration.'
  },
  {
    icon: <Star className="w-4 h-4 text-zinc-400" />,
    value: '2.1k+ Stars',
    label: 'Apache 2.0 Open Source',
    detail: 'Auditable, transparent Android agent runtime codebase.'
  }
];

export const StatsStrip: React.FC = () => {
  return (
    <section className="border-y border-white/[0.08] bg-zinc-950/70 py-12 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 lg:gap-8">
          {STATS.map((stat, idx) => (
            <div
              key={idx}
              className="p-5 rounded-xl bg-zinc-900/40 border border-white/[0.06] hover:border-white/15 transition-all interactive-card group"
            >
              <div className="flex items-center gap-3 mb-3">
                <div className="w-8 h-8 rounded-lg bg-zinc-800/80 border border-white/5 flex items-center justify-center group-hover:border-white/20 transition-colors">
                  {stat.icon}
                </div>
                <span className="text-xl sm:text-2xl font-bold tracking-tight text-white tabular-nums">
                  {stat.value}
                </span>
              </div>
              <h4 className="text-xs font-mono font-medium text-zinc-300 uppercase tracking-wider mb-1">
                {stat.label}
              </h4>
              <p className="text-xs text-zinc-500 leading-normal">
                {stat.detail}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};
