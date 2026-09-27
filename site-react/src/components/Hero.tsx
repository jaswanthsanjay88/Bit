import React, { useState, useEffect } from 'react';
import { Download, Terminal, ArrowRight, ShieldCheck, Cpu, CheckCircle2, Activity, Play } from 'lucide-react';
import { GithubIcon } from './GithubIcon';

interface SimulationScenario {
  id: string;
  name: string;
  userPrompt: string;
  toolCall: {
    tool: string;
    input: string;
    duration: string;
  };
  agentSteps: string[];
  streamingText: string;
  tokensPerSec: number;
  ttft: number;
  ramUsage: string;
}

const SCENARIOS: SimulationScenario[] = [
  {
    id: 'search',
    name: 'Tool Orchestration',
    userPrompt: 'Find latest local offline models for ARM Cortex-A78 and benchmark their RAM footprints.',
    toolCall: {
      tool: 'models_catalog_query',
      input: '{"arch": "arm64-v8a", "min_ram_gb": 4, "format": "GGUF_Q4_K_M"}',
      duration: '42ms'
    },
    agentSteps: [
      'Decomposing query with GBNF grammar constraints',
      'Executing models_catalog_query against local SQLite store',
      'Evaluating SIMD tensor operations for 4-bit quant'
    ],
    streamingText: 'Identified 3 optimal models: Qwen 3.5 0.8B (600MB RAM, 42.1 t/s), LFM2 350M (400MB RAM, 58.4 t/s), and SmolLM2 1.7B (1.1GB RAM, 28.6 t/s). Zero telemetry egress detected.',
    tokensPerSec: 42.1,
    ttft: 88,
    ramUsage: '1.24 GB'
  },
  {
    id: 'rag',
    name: 'Encrypted Document RAG',
    userPrompt: 'Audit contract_nda.pdf in Memory Vault and summarize non-disclosure obligations.',
    toolCall: {
      tool: 'memory_vault_search',
      input: '{"collection": "documents", "top_k": 3, "cosine_threshold": 0.82}',
      duration: '64ms'
    },
    agentSteps: [
      'Generating 384-dim BGE-small embeddings via llama.kt',
      'Searching local SQLite vector index with Cosine similarity',
      'Formatting context chunk for zero-leakage synthesis'
    ],
    streamingText: 'Summary: Section 4.2 mandates a 2-year confidentiality clause covering proprietary on-device model weights and native JNI bridge source code. No third-party transmission permitted.',
    tokensPerSec: 36.8,
    ttft: 114,
    ramUsage: '1.58 GB'
  },
  {
    id: 'voice',
    name: 'Local Voice & Audio',
    userPrompt: 'Transcribe ambient memo and synthesize spoken brief.',
    toolCall: {
      tool: 'piper_tts_synthesize',
      input: '{"voice": "en_US-lessac-medium", "speed": 1.05}',
      duration: '95ms'
    },
    agentSteps: [
      'Streaming Sherpa-ONNX Whisper STT transcription',
      'Validating punctuation via native regex filter',
      'Synthesizing 22.05kHz PCM audio buffer on-device'
    ],
    streamingText: 'Audio brief generated in 198ms via Piper ONNX neural engine. Zero audio bytes dispatched over network sockets.',
    tokensPerSec: 49.3,
    ttft: 76,
    ramUsage: '890 MB'
  }
];

export const Hero: React.FC = () => {
  const [activeScenarioIndex, setActiveScenarioIndex] = useState(0);
  const scenario = SCENARIOS[activeScenarioIndex];
  const [activeStep, setActiveStep] = useState(0);
  const [displayedText, setDisplayedText] = useState('');
  const [isSimulating, setIsSimulating] = useState(false);

  // Dynamic simulation progression
  useEffect(() => {
    let isMounted = true;
    setIsSimulating(true);
    setDisplayedText('');
    setActiveStep(0);

    const step1Timer = setTimeout(() => {
      if (isMounted) setActiveStep(1);
    }, 450);

    const step2Timer = setTimeout(() => {
      if (isMounted) setActiveStep(2);
    }, 900);

    const streamTimer = setTimeout(() => {
      if (!isMounted) return;
      let currentIndex = 0;
      const fullText = scenario.streamingText;
      const interval = setInterval(() => {
        if (!isMounted) {
          clearInterval(interval);
          return;
        }
        currentIndex += 3;
        if (currentIndex <= fullText.length) {
          setDisplayedText(fullText.slice(0, currentIndex));
        } else {
          setDisplayedText(fullText);
          setIsSimulating(false);
          clearInterval(interval);
        }
      }, 25);
    }, 1300);

    return () => {
      isMounted = false;
      clearTimeout(step1Timer);
      clearTimeout(step2Timer);
      clearTimeout(streamTimer);
    };
  }, [activeScenarioIndex]);

  return (
    <section className="relative pt-32 pb-20 md:pt-40 md:pb-28 overflow-hidden bg-grid-subtle">
      {/* Top subtle radial aura */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[350px] bg-gradient-to-b from-zinc-800/20 via-zinc-900/10 to-transparent blur-3xl pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        {/* Release Pill Badge */}
        <div className="flex justify-center mb-6">
          <a
            href="https://github.com/jaswanthsanjay88/Bit_Android/releases"
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-zinc-900/90 border border-white/10 text-xs text-zinc-300 hover:border-white/20 transition-all hover:bg-zinc-850 group"
          >
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="font-mono text-zinc-400">BIT v2.1.1</span>
            <span className="text-zinc-600">|</span>
            <span className="text-zinc-300 group-hover:text-white transition-colors">
              Autonomous on-device agentic runtime
            </span>
            <ArrowRight className="w-3 h-3 text-zinc-400 group-hover:translate-x-0.5 transition-transform" />
          </a>
        </div>

        {/* Massive Vercel-style Headline */}
        <div className="text-center max-w-4xl mx-auto mb-6">
          <h1 className="text-4xl sm:text-6xl md:text-7xl font-extrabold tracking-tight text-white leading-[1.06]">
            AI that never leaves <br className="hidden sm:inline" />
            <span className="bg-gradient-to-b from-white via-zinc-200 to-zinc-500 bg-clip-text text-transparent">
              your phone.
            </span>
          </h1>
        </div>

        {/* Crisp Subhead */}
        <div className="text-center max-w-2xl mx-auto mb-10">
          <p className="text-base sm:text-lg text-zinc-400 leading-relaxed font-normal">
            Autonomous local subagents, GBNF-constrained tool execution, and real-time offline voice. Powered natively by GGUF and llama.cpp. Zero telemetry, zero cloud dependency.
          </p>
        </div>

        {/* Dual Primary CTAs */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4 mb-16">
          <a
            href="#terminal"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-xl bg-white text-black font-semibold text-sm hover:bg-zinc-200 transition-all duration-150 active:scale-[0.98] shadow-[0_0_30px_rgba(255,255,255,0.2)]"
          >
            <Download className="w-4 h-4" />
            <span>Download APK</span>
          </a>
          <a
            href="https://github.com/jaswanthsanjay88/Bit_Android"
            target="_blank"
            rel="noreferrer"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-xl bg-zinc-900/80 hover:bg-zinc-800 text-zinc-200 hover:text-white font-medium text-sm border border-white/10 hover:border-white/20 transition-all duration-150 active:scale-[0.98]"
          >
            <GithubIcon className="w-4 h-4" />
            <span>View on GitHub</span>
            <span className="text-xs font-mono text-zinc-500 ml-1">2.1k</span>
          </a>
        </div>

        {/* Live Product Visual: Interactive On-Device Agent Simulator */}
        <div className="max-w-4xl mx-auto">
          <div className="relative rounded-2xl glass-panel-elevated p-1 md:p-2 border border-white/10 shadow-[0_20px_70px_rgba(0,0,0,0.8)]">
            {/* Top Device Status Bar / Agent Dynamic Island */}
            <div className="bg-zinc-950/90 rounded-xl border border-white/5 p-4 md:p-6 space-y-5">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/5 pb-4">
                <div className="flex items-center gap-3">
                  <div className="w-3 h-3 rounded-full bg-emerald-500/80 ring-4 ring-emerald-500/10" />
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono font-medium text-zinc-200">BIT Runtime Agent</span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-zinc-900 text-zinc-400 border border-white/10">
                      ARM64 Neon SIMD
                    </span>
                  </div>
                </div>

                {/* Scenario Switcher Tabs */}
                <div className="flex items-center gap-1.5 p-1 rounded-lg bg-zinc-900/90 border border-white/5 text-xs">
                  {SCENARIOS.map((sc, idx) => (
                    <button
                      key={sc.id}
                      type="button"
                      onClick={() => setActiveScenarioIndex(idx)}
                      className={`px-3 py-1 rounded-md font-mono text-[11px] transition-all ${
                        activeScenarioIndex === idx
                          ? 'bg-zinc-800 text-white font-semibold shadow-sm'
                          : 'text-zinc-400 hover:text-zinc-200'
                      }`}
                    >
                      {sc.name}
                    </button>
                  ))}
                </div>
              </div>

              {/* Dynamic Island / Agent State Indicator */}
              <div className="flex items-center justify-between bg-zinc-900/60 rounded-lg px-4 py-2.5 border border-white/5">
                <div className="flex items-center gap-2.5">
                  <Activity className="w-3.5 h-3.5 text-zinc-400 animate-spin" />
                  <span className="text-xs font-mono text-zinc-300">
                    State: {isSimulating ? 'EXECUTING GBNF PIPELINE' : 'SYNTHESIS COMPLETE'}
                  </span>
                </div>
                <div className="flex items-center gap-4 text-[11px] font-mono text-zinc-400">
                  <span>RAM: <strong className="text-zinc-200 tabular-nums">{scenario.ramUsage}</strong></span>
                  <span className="hidden sm:inline">Speed: <strong className="text-zinc-200 tabular-nums">{scenario.tokensPerSec} t/s</strong></span>
                  <span className="text-emerald-400 flex items-center gap-1">
                    <ShieldCheck className="w-3.5 h-3.5" />
                    <span>0B Egress</span>
                  </span>
                </div>
              </div>

              {/* Chat Simulation Area */}
              <div className="space-y-4 pt-1">
                {/* User Prompt */}
                <div className="flex justify-end">
                  <div className="max-w-[85%] rounded-xl bg-zinc-850 border border-white/10 px-4 py-2.5 text-xs sm:text-sm text-zinc-200">
                    <p className="font-mono text-zinc-400 text-[10px] mb-1">USER INTENT</p>
                    {scenario.userPrompt}
                  </div>
                </div>

                {/* Subagent Reasoning Box */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-[11px] font-mono text-zinc-500 uppercase tracking-wider">
                    <Terminal className="w-3.5 h-3.5" />
                    <span>Subagent Execution Trace</span>
                  </div>

                  <div className="rounded-xl bg-black/60 border border-white/5 p-3.5 font-mono text-xs space-y-2">
                    {/* Step Execution Badges */}
                    {scenario.agentSteps.map((step, idx) => (
                      <div
                        key={idx}
                        className={`flex items-center gap-2.5 transition-opacity duration-200 ${
                          idx <= activeStep ? 'opacity-100' : 'opacity-25'
                        }`}
                      >
                        <CheckCircle2
                          className={`w-3.5 h-3.5 ${
                            idx <= activeStep ? 'text-emerald-400' : 'text-zinc-600'
                          }`}
                        />
                        <span className="text-zinc-300 text-[11px]">{step}</span>
                      </div>
                    ))}

                    {/* Tool Call Box */}
                    <div className="mt-3 pt-2.5 border-t border-white/5 flex flex-wrap items-center justify-between gap-2 text-[11px]">
                      <div className="flex items-center gap-2">
                        <span className="px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-300 font-semibold">
                          CALL
                        </span>
                        <code className="text-zinc-300">{scenario.toolCall.tool}</code>
                      </div>
                      <div className="flex items-center gap-2 text-zinc-500">
                        <span>Latency: <strong className="text-zinc-400 tabular-nums">{scenario.toolCall.duration}</strong></span>
                        <span className="px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-medium">
                          GRAMMAR VALID
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Agent Response Stream */}
                <div className="rounded-xl bg-zinc-900/40 border border-white/10 p-4 text-xs sm:text-sm text-zinc-100">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-[10px] text-zinc-400 uppercase tracking-wider">
                      SYNTHESIZED OUTPUT
                    </span>
                    <span className="font-mono text-[10px] text-zinc-500 tabular-nums">
                      TTFT: {scenario.ttft}ms
                    </span>
                  </div>
                  <p className="leading-relaxed font-sans text-zinc-200">
                    {displayedText}
                    {isSimulating && (
                      <span className="inline-block w-2 h-4 ml-1 bg-white animate-pulse align-middle" />
                    )}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
