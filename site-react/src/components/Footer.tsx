import React from 'react';
import { Lock, ArrowUpRight, ShieldCheck, Terminal, Download, Cpu } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-white/[0.08] bg-[#07080A] text-[#A1A1AA] text-sm py-16 sm:py-20">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-12 lg:gap-16 mb-16">
          {/* Column 1: Sovereign Product */}
          <div className="space-y-4">
            <div className="flex items-center gap-2.5 mb-2">
              <div className="w-6 h-6 rounded bg-[#141720] border border-white/10 flex items-center justify-center p-1">
                <img src="/img/ic_logo.svg" alt="BIT" className="w-full h-full filter invert opacity-90" />
              </div>
              <span className="font-bold text-white tracking-tight text-base">BIT Runtime</span>
            </div>
            <p className="text-sm text-[#A1A1AA] leading-relaxed">
              Autonomous on-device agent platform running quantized llama.cpp models directly on Android hardware with zero server dependency.
            </p>
            <ul className="space-y-2.5 pt-2 text-sm">
              <li>
                <a href="#features" className="text-zinc-300 hover:text-[#B6FF3B] transition-colors flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-600"></span>
                  Autonomous Subagents & Tool Calling
                </a>
              </li>
              <li>
                <a href="#features" className="text-zinc-300 hover:text-[#B6FF3B] transition-colors flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-600"></span>
                  Streaming Voice & Silero VAD
                </a>
              </li>
              <li>
                <a href="#features" className="text-zinc-300 hover:text-[#B6FF3B] transition-colors flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-600"></span>
                  Memory Vault & On-Device RAG
                </a>
              </li>
              <li>
                <a href="#models" className="text-zinc-300 hover:text-[#B6FF3B] transition-colors flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-600"></span>
                  Verified GGUF Model Matrix
                </a>
              </li>
            </ul>
          </div>

          {/* Column 2: Code & Distribution */}
          <div className="space-y-4">
            <h4 className="text-white font-bold text-base tracking-tight mb-2">
              Code & Distribution
            </h4>
            <p className="text-sm text-[#A1A1AA] leading-relaxed">
              100% open source under Apache 2.0. Inspect every C++ binding, verify every permission, and compile from source.
            </p>
            <ul className="space-y-2.5 pt-2 text-sm">
              <li>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-zinc-300 hover:text-[#B6FF3B] transition-colors"
                >
                  <span>GitHub Repository</span>
                  <ArrowUpRight className="w-3.5 h-3.5 text-zinc-500" />
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android/releases"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-zinc-300 hover:text-[#B6FF3B] transition-colors"
                >
                  <span>Production APK (v2.1.1)</span>
                  <ArrowUpRight className="w-3.5 h-3.5 text-zinc-500" />
                </a>
              </li>
              <li>
                <a href="#terminal" className="inline-flex items-center gap-1.5 text-zinc-300 hover:text-[#B6FF3B] transition-colors">
                  <span>ADB 1-Click Sideload Guide</span>
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android/blob/main/LICENSE"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-zinc-300 hover:text-[#B6FF3B] transition-colors"
                >
                  <span>Apache 2.0 Open License</span>
                  <ArrowUpRight className="w-3.5 h-3.5 text-zinc-500" />
                </a>
              </li>
            </ul>
          </div>

          {/* Column 3: Sovereignty & Privacy Pledge */}
          <div className="space-y-4">
            <h4 className="text-white font-bold text-base tracking-tight mb-2">
              Sovereignty Guarantee
            </h4>
            <p className="text-sm text-[#A1A1AA] leading-relaxed">
              BIT contains zero analytics SDKs, zero telemetry collectors, and zero hidden network endpoints. All inference runs locally in isolated process space.
            </p>
            <div className="p-4 rounded-xl bg-[#0D0F14] border border-white/[0.08] space-y-2.5">
              <div className="flex items-center gap-2 text-xs font-semibold text-white">
                <ShieldCheck className="w-4 h-4 text-[#B6FF3B]" />
                <span>Zero Network Egress Audited</span>
              </div>
              <p className="text-xs text-[#A1A1AA] leading-relaxed">
                Tokens, embeddings, chat transcripts, and synthesized voice stay inside device sandbox storage permanently.
              </p>
            </div>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="pt-8 border-t border-white/[0.08] flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-[#A1A1AA]">
          <div className="flex items-center gap-3">
            <span>BIT Android &bull; Apache-2.0 Open Source</span>
            <span>&bull;</span>
            <span className="text-zinc-500">ARM64 Android 12+</span>
          </div>

          <div className="flex items-center gap-6">
            <a href="#models" className="hover:text-white transition-colors">
              Model Catalog
            </a>
            <a href="#terminal" className="hover:text-white transition-colors">
              CLI Install
            </a>
            <a href="#faq" className="hover:text-white transition-colors">
              FAQ
            </a>
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              className="text-[#B6FF3B] hover:underline"
            >
              GitHub
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};
