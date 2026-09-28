import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Download, Terminal, ArrowRight, ShieldCheck, CheckCircle2, Activity } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { springs } from '../lib/motion';

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
  
  // Interactive mouse spotlight coordinates
  const heroRef = useRef<HTMLDivElement>(null);
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!heroRef.current) return;
    const rect = heroRef.current.getBoundingClientRect();
    setMousePos({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
  };

  useEffect(() => {
    let isMounted = true;
    setIsSimulating(true);
    setDisplayedText('');
    setActiveStep(0);

    const step1Timer = setTimeout(() => {
      if (isMounted) setActiveStep(1);
    }, 400);

    const step2Timer = setTimeout(() => {
      if (isMounted) setActiveStep(2);
    }, 850);

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
      }, 22);
    }, 1200);

    return () => {
      isMounted = false;
      clearTimeout(step1Timer);
      clearTimeout(step2Timer);
      clearTimeout(streamTimer);
    };
  }, [activeScenarioIndex]);

  return (
    <section
      ref={heroRef}
      onMouseMove={handleMouseMove}
      className="relative pt-32 pb-24 md:pt-44 md:pb-36 overflow-hidden bg-[#07080A]"
    >
      {/* Interactive mouse spotlight with subtle lime tint */}
      <div
        className="pointer-events-none absolute -inset-px opacity-40 transition-opacity duration-300"
        style={{
          background: `radial-gradient(650px circle at ${mousePos.x}px ${mousePos.y}px, rgba(182, 255, 59, 0.08), transparent 75%)`,
        }}
      />

      {/* Background ambient grid */}
      <div className="absolute inset-0 bg-grid-ambient opacity-50 pointer-events-none" />

      {/* Hero Content Container */}
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* 1. Small Version Pill above Headline */}
        <div className="flex justify-center mb-7">
          <motion.a
            href="https://github.com/jaswanthsanjay88/Bit_Android/releases"
            target="_blank"
            rel="noreferrer"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-white/[0.04] border border-white/10 text-xs text-zinc-300 hover:border-[#B6FF3B]/40 transition-all shadow-sm group"
          >
            <span className="relative flex h-2 w-2">
              <span className="radar-ring absolute inline-flex h-full w-full rounded-full bg-[#B6FF3B] opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-[#B6FF3B]" />
            </span>
            <span className="font-mono text-[#B6FF3B] font-semibold">v2.1.1</span>
            <span className="text-zinc-600">&bull;</span>
            <span className="text-zinc-200 group-hover:text-white transition-colors">
              Sovereign On-Device Agent Runtime
            </span>
            <ArrowRight className="w-3 h-3 text-zinc-400 group-hover:text-[#B6FF3B] group-hover:translate-x-0.5 transition-all" />
          </motion.a>
        </div>

        {/* 2. Bigger Centered Headline */}
        <div className="text-center max-w-4xl mx-auto mb-6">
          <h1 className="text-5xl sm:text-7xl md:text-8xl font-extrabold tracking-[-0.045em] text-white leading-[0.98]">
            AI that never leaves <br />
            <span className="text-zinc-400">
              your phone.
            </span>
          </h1>
        </div>

        {/* 3. Shortened Crisp One-Line Subhead */}
        <div className="text-center max-w-2xl mx-auto mb-10">
          <p className="text-lg sm:text-xl text-[#A1A1AA] leading-relaxed font-normal">
            Autonomous local agents, GBNF tool calling, and offline voice powered directly by your phone's processor.
          </p>
        </div>

        {/* 4. Two High-Contrast CTAs */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-20">
          <motion.a
            href="#terminal"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-8 py-3.5 h-12 rounded-xl btn-accent text-sm"
          >
            <Download className="w-4 h-4" />
            <span>Download APK</span>
          </motion.a>

          <motion.a
            href="https://github.com/jaswanthsanjay88/Bit_Android"
            target="_blank"
            rel="noreferrer"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-7 py-3.5 h-12 rounded-xl btn-ghost text-sm font-medium"
          >
            <GithubIcon className="w-4 h-4 text-zinc-400" />
            <span>View on GitHub</span>
            <span className="text-xs font-mono text-[#B6FF3B] ml-1">2.1k</span>
          </motion.a>
        </div>

        {/* 5. LARGE Product Visual: 3 Fanned Overlapping Phones with Soft Glow */}
        <div className="relative mb-20 flex justify-center items-center">
          {/* Soft Lime Radial Glow behind phones */}
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[450px] bg-gradient-to-b from-[#B6FF3B]/10 via-[#B6FF3B]/3 to-transparent blur-3xl rounded-full pointer-events-none" />

          {/* Fanned Phone Mockups */}
          <div className="relative flex items-center justify-center w-full max-w-4xl h-[460px] sm:h-[540px]">
            {/* Left Phone: Chat Interface */}
            <div className="absolute left-[5%] sm:left-[12%] z-10 w-[210px] sm:w-[250px] aspect-[9/18] rounded-[32px] p-2 bg-[#0E1015] border border-white/10 shadow-[0_25px_60px_rgba(0,0,0,0.9)] animate-float-left origin-bottom-left">
              <div className="w-full h-full rounded-[24px] overflow-hidden bg-black">
                <img
                  src="/img/screenshots/01_chat_interface_1080x1920.png"
                  alt="BIT Autonomous Chat"
                  className="w-full h-full object-cover object-top filter contrast-105"
                  loading="eager"
                  width={250}
                  height={500}
                />
              </div>
            </div>

            {/* Right Phone: Document RAG */}
            <div className="absolute right-[5%] sm:right-[12%] z-10 w-[210px] sm:w-[250px] aspect-[9/18] rounded-[32px] p-2 bg-[#0E1015] border border-white/10 shadow-[0_25px_60px_rgba(0,0,0,0.9)] animate-float-right origin-bottom-right">
              <div className="w-full h-full rounded-[24px] overflow-hidden bg-black">
                <img
                  src="/img/screenshots/04_rag_documents_1080x1920.png"
                  alt="BIT Memory Vault RAG"
                  className="w-full h-full object-cover object-top filter contrast-105"
                  loading="eager"
                  width={250}
                  height={500}
                />
              </div>
            </div>

            {/* Center Phone: Real-Time Voice Mode (Elevated & Prominent) */}
            <div className="relative z-20 w-[240px] sm:w-[285px] aspect-[9/18] rounded-[36px] p-2.5 bg-[#141720] border-2 border-[#B6FF3B]/30 shadow-[0_30px_80px_rgba(0,0,0,0.95),0_0_40px_rgba(182,255,59,0.18)] animate-float-center">
              <div className="w-full h-full rounded-[26px] overflow-hidden bg-black relative">
                <img
                  src="/img/screenshots/02_live_voice_mode_1080x1920.png"
                  alt="BIT Real-Time Voice Mode"
                  className="w-full h-full object-cover object-top filter contrast-105"
                  loading="eager"
                  width={285}
                  height={570}
                />
                <div className="absolute top-3 left-1/2 -translate-x-1/2 px-2.5 py-0.5 rounded-full bg-black/80 backdrop-blur-md border border-white/15 text-[10px] font-mono text-[#B6FF3B]">
                  Live Voice &bull; Silero VAD
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* 6. Agent Simulator Cockpit (Shown Large & Wide Below) */}
        <div className="max-w-[1000px] mx-auto">
          <div className="surface-2 rounded-2xl p-2 md:p-3 border border-white/10 shadow-[0_20px_70px_rgba(0,0,0,0.95)]">
            <div className="bg-[#07080A] rounded-xl border border-white/5 p-5 md:p-7 space-y-6">
              {/* Header Bar */}
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-white/5 pb-4">
                <div className="flex items-center gap-3">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="radar-ring absolute inline-flex h-full w-full rounded-full bg-[#B6FF3B] opacity-75" />
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-[#B6FF3B]" />
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-white">BIT Runtime Harness</span>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/[0.05] text-[#B6FF3B] border border-white/10">
                      ARM64 Neon SIMD
                    </span>
                  </div>
                </div>

                {/* Scenario Switcher Tabs */}
                <div className="flex items-center gap-1.5 p-1 rounded-lg bg-[#0E1015] border border-white/5 text-xs">
                  {SCENARIOS.map((sc, idx) => (
                    <button
                      key={sc.id}
                      type="button"
                      onClick={() => setActiveScenarioIndex(idx)}
                      className={`px-3.5 py-1.5 rounded-md font-medium text-xs transition-all ${
                        activeScenarioIndex === idx
                          ? 'bg-[#1C202C] text-white shadow-sm font-semibold'
                          : 'text-[#A1A1AA] hover:text-white'
                      }`}
                    >
                      {sc.name}
                    </button>
                  ))}
                </div>
              </div>

              {/* Dynamic Island / Agent State Indicator */}
              <div className="flex items-center justify-between bg-[#0E1015] rounded-xl px-4 py-3 border border-white/5">
                <div className="flex items-center gap-2.5">
                  <Activity className="w-4 h-4 text-[#B6FF3B] animate-spin" />
                  <span className="text-xs font-mono text-zinc-200">
                    State: {isSimulating ? 'EXECUTING GBNF PIPELINE' : 'SYNTHESIS COMPLETE'}
                  </span>
                </div>
                <div className="flex items-center gap-5 text-xs font-mono text-zinc-400">
                  <span>RAM: <strong className="text-white tabular-nums">{scenario.ramUsage}</strong></span>
                  <span className="hidden sm:inline">Speed: <strong className="text-[#B6FF3B] tabular-nums">{scenario.tokensPerSec} t/s</strong></span>
                  <span className="text-[#B6FF3B] flex items-center gap-1">
                    <ShieldCheck className="w-4 h-4" />
                    <span>0B Egress</span>
                  </span>
                </div>
              </div>

              {/* Simulation Conversation Area */}
              <div className="space-y-4">
                {/* User Prompt Bubble */}
                <div className="flex justify-end">
                  <div className="max-w-[85%] rounded-xl bg-[#141720] border border-white/10 px-4 py-3 text-sm text-zinc-200">
                    <div className="text-[11px] font-mono text-[#A1A1AA] mb-1">USER INTENT</div>
                    {scenario.userPrompt}
                  </div>
                </div>

                {/* Subagent Reasoning Box */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-xs font-mono text-zinc-400">
                    <Terminal className="w-3.5 h-3.5 text-[#B6FF3B]" />
                    <span>Subagent Execution Trace</span>
                  </div>

                  <div className="rounded-xl bg-[#0B0D12] border border-white/5 p-4 font-mono text-xs space-y-2.5">
                    {scenario.agentSteps.map((step, idx) => (
                      <div
                        key={idx}
                        className={`flex items-center gap-2.5 transition-opacity duration-200 ${
                          idx <= activeStep ? 'opacity-100' : 'opacity-25'
                        }`}
                      >
                        <CheckCircle2
                          className={`w-4 h-4 ${
                            idx <= activeStep ? 'text-[#B6FF3B]' : 'text-zinc-700'
                          }`}
                        />
                        <span className="text-zinc-300 text-xs">{step}</span>
                      </div>
                    ))}

                    {/* Tool Call Box */}
                    <div className="mt-3 pt-3 border-t border-white/5 flex flex-wrap items-center justify-between gap-2 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 rounded bg-white/[0.08] text-[#B6FF3B] font-bold">
                          CALL
                        </span>
                        <code className="text-white">{scenario.toolCall.tool}</code>
                      </div>
                      <div className="flex items-center gap-3 text-zinc-400">
                        <span>Latency: <strong className="text-white tabular-nums">{scenario.toolCall.duration}</strong></span>
                        <span className="px-2 py-0.5 rounded bg-[#B6FF3B]/10 text-[#B6FF3B] font-medium border border-[#B6FF3B]/20">
                          GRAMMAR VALID
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Streaming Output */}
                <div className="rounded-xl bg-[#0E1015] border border-white/10 p-5 text-sm text-zinc-100">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-mono text-zinc-400">SYNTHESIZED OUTPUT</span>
                    <span className="text-xs font-mono text-[#B6FF3B] tabular-nums">TTFT: {scenario.ttft}ms</span>
                  </div>
                  <p className="leading-relaxed text-zinc-200 text-base">
                    {displayedText}
                    {isSimulating && (
                      <span className="inline-block w-2 h-4 ml-1 bg-[#B6FF3B] animate-pulse align-middle" />
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
