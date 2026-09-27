import React from 'react';
import { Shield, Lock, ArrowUpRight, Terminal } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-white/[0.08] bg-black text-zinc-400 text-xs py-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-12">
          {/* Col 1 */}
          <div>
            <h4 className="text-white font-mono font-medium uppercase tracking-wider text-[11px] mb-4">
              Product
            </h4>
            <ul className="space-y-2.5">
              <li>
                <a href="#features" className="hover:text-white transition-colors">
                  Autonomous Subagents
                </a>
              </li>
              <li>
                <a href="#pipeline" className="hover:text-white transition-colors">
                  GBNF Tool Calling
                </a>
              </li>
              <li>
                <a href="#features" className="hover:text-white transition-colors">
                  Offline Voice & VAD
                </a>
              </li>
              <li>
                <a href="#features" className="hover:text-white transition-colors">
                  Memory Vault RAG
                </a>
              </li>
              <li>
                <a href="#models" className="hover:text-white transition-colors">
                  Quantized Model Catalog
                </a>
              </li>
            </ul>
          </div>

          {/* Col 2 */}
          <div>
            <h4 className="text-white font-mono font-medium uppercase tracking-wider text-[11px] mb-4">
              Architecture
            </h4>
            <ul className="space-y-2.5">
              <li>
                <a href="#pipeline" className="hover:text-white transition-colors">
                  llama.kt Native JNI
                </a>
              </li>
              <li>
                <a href="#pipeline" className="hover:text-white transition-colors">
                  ARM Neon SIMD Vectors
                </a>
              </li>
              <li>
                <a href="#pipeline" className="hover:text-white transition-colors">
                  Sherpa-ONNX Streaming STT
                </a>
              </li>
              <li>
                <a href="#pipeline" className="hover:text-white transition-colors">
                  Piper Neural TTS
                </a>
              </li>
              <li>
                <a href="#terminal" className="hover:text-white transition-colors">
                  GBNF Grammar BNF
                </a>
              </li>
            </ul>
          </div>

          {/* Col 3 */}
          <div>
            <h4 className="text-white font-mono font-medium uppercase tracking-wider text-[11px] mb-4">
              Distribution
            </h4>
            <ul className="space-y-2.5">
              <li>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 hover:text-white transition-colors"
                >
                  <span>GitHub Repository</span>
                  <ArrowUpRight className="w-3 h-3 text-zinc-600" />
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android/releases"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 hover:text-white transition-colors"
                >
                  <span>Latest Release (v2.1.1)</span>
                  <ArrowUpRight className="w-3 h-3 text-zinc-600" />
                </a>
              </li>
              <li>
                <a href="#terminal" className="hover:text-white transition-colors">
                  ADB Install Guide
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/jaswanthsanjay88/Bit_Android/blob/main/LICENSE"
                  target="_blank"
                  rel="noreferrer"
                  className="hover:text-white transition-colors"
                >
                  Apache 2.0 License
                </a>
              </li>
            </ul>
          </div>

          {/* Col 4 */}
          <div>
            <h4 className="text-white font-mono font-medium uppercase tracking-wider text-[11px] mb-4">
              Privacy Pledge
            </h4>
            <div className="space-y-3 text-zinc-400 leading-relaxed">
              <p>
                BIT does not collect analytics, logs, telemetry, device identifiers, or biometric audio recordings.
              </p>
              <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[10px] font-mono text-zinc-300">
                <Lock className="w-3 h-3 text-emerald-400" />
                <span>Zero telemetry guaranteed</span>
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="pt-8 border-t border-white/5 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 rounded bg-zinc-900 border border-white/10 flex items-center justify-center p-1">
              <img src="/img/ic_logo.svg" alt="BIT" className="w-full h-full filter invert opacity-80" />
            </div>
            <span className="font-mono text-xs text-zinc-300">
              BIT Sovereign Runtime &bull; Apache-2.0
            </span>
          </div>

          <div className="flex items-center gap-6 text-[11px] font-mono text-zinc-500">
            <span>ARM64 Android 12+</span>
            <span>&bull;</span>
            <span>100% On-Device</span>
            <span>&bull;</span>
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              className="text-zinc-400 hover:text-white transition-colors"
            >
              GitHub
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};
