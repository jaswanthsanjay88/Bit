import React, { useState } from 'react';
import { Mic, GitBranch, Cpu, Volume2, ArrowRight, ShieldCheck, Check } from 'lucide-react';

interface PipelineStage {
  step: string;
  name: string;
  tagline: string;
  icon: React.ReactNode;
  specs: { label: string; value: string }[];
  details: string;
  codeSnippet: string;
}

const STAGES: PipelineStage[] = [
  {
    step: '01',
    name: 'Input Ingestion',
    tagline: 'Speech, Text & Vision Preprocessing',
    icon: <Mic className="w-5 h-5 text-zinc-300" />,
    specs: [
      { label: 'Audio Engine', value: 'Sherpa-ONNX Whisper' },
      { label: 'Sampling Rate', value: '16kHz Mono PCM' },
      { label: 'VAD Turnaround', value: '<120ms latency' }
    ],
    details: 'Acoustic audio frames from the device microphone are streamed directly into on-device Sherpa-ONNX Whisper. Zero audio chunks are written to temporary disk files or transmitted over sockets.',
    codeSnippet: `// Sherpa-ONNX Native Pipeline
val recognizer = OnlineRecognizer.create(config)
val stream = recognizer.createStream()
stream.acceptWaveform(samples, sampleRate = 16000)`
  },
  {
    step: '02',
    name: 'GBNF Grammar Router',
    tagline: 'Constrained Context-Free Decoding',
    icon: <GitBranch className="w-5 h-5 text-zinc-300" />,
    specs: [
      { label: 'Grammar Engine', value: 'GBNF (llama.cpp)' },
      { label: 'Parser Overhead', value: '<1.5ms per token' },
      { label: 'Schema Guarantee', value: '100% Valid JSON' }
    ],
    details: 'Every tool invocation is clamped by a deterministic GBNF context-free grammar. The neural network cannot emit invalid parameter formats, malformed types, or hallucinated method names.',
    codeSnippet: `root ::= ToolCall
ToolCall ::= "{\\"tool\\":\\"" [a-z_]+ "\\",\\"params\\":" Object "}"
Object ::= "{" (String ":" Value ("," String ":" Value)*)? "}"`
  },
  {
    step: '03',
    name: 'Planner & Subagent Runner',
    tagline: 'Multi-Step Sandboxed Orchestration',
    icon: <Cpu className="w-5 h-5 text-zinc-300" />,
    specs: [
      { label: 'Concurrency', value: 'Kotlin Coroutines Flow' },
      { label: 'Sandbox Isolation', value: 'Android Process Boundary' },
      { label: 'Tool Trace', value: 'Immutable Event Journal' }
    ],
    details: 'The agent harness evaluates subgoals, triggers atomic tools (local document search, battery telemetry, offline calculator), and feeds intermediate outputs back into the context window.',
    codeSnippet: `suspend fun executeStep(plan: AgentPlan): StepResult {
    val tool = registry.find(plan.toolId)
    val verifiedArgs = gbnfValidator.parse(plan.rawArgs)
    return tool.execute(verifiedArgs)
}`
  },
  {
    step: '04',
    name: 'llama.kt Native JNI Core',
    tagline: 'Hardware SIMD & NPU Acceleration',
    icon: <Cpu className="w-5 h-5 text-zinc-300" />,
    specs: [
      { label: 'Architecture', value: 'arm64-v8a + ARM Neon' },
      { label: 'Quantization', value: 'Q4_K_M / Q8_0 / INT4' },
      { label: 'Inference Speed', value: '35 - 65 tokens/sec' }
    ],
    details: 'Direct JNI bridge compiles upstream llama.cpp directly for Android bionic libc. Matrix multiplications leverage ARM Neon SIMD vector registers and Qualcomm Hexagon DSP / Dimensity NPU.',
    codeSnippet: `// JNI Core Invocation
external fun llamaInitModel(path: String, params: ModelParams): Long
external fun llamaEvalTokens(ctx: Long, tokens: IntArray, nTokens: Int): Int`
  },
  {
    step: '05',
    name: 'Synthesis & Voice Output',
    tagline: 'Streaming Markdown & Neural Audio',
    icon: <Volume2 className="w-5 h-5 text-zinc-300" />,
    specs: [
      { label: 'TTS Engine', value: 'Piper ONNX VITS' },
      { label: 'Audio Quality', value: '22.05kHz 16-bit' },
      { label: 'Vault Persistence', value: 'Local Encrypted SQLite' }
    ],
    details: 'Tokens stream instantly into Jetpack Compose UI with real-time markdown parsing. If voice mode is active, sentences are piped directly to Piper ONNX for low-latency neural speech.',
    codeSnippet: `// Piper ONNX Synthesis Stream
val audioTrack = AudioTrack.Builder().setAudioFormat(pcmFormat).build()
piperSynthesizer.streamSentence(sentenceChunk) { pcmBytes ->
    audioTrack.write(pcmBytes, 0, pcmBytes.size)
}`
  }
];

export const HorizontalPipeline: React.FC = () => {
  const [activeStageIndex, setActiveStageIndex] = useState(1);
  const activeStage = STAGES[activeStageIndex];

  return (
    <section id="pipeline" className="py-24 bg-zinc-950 border-t border-white/[0.08] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="max-w-3xl mb-16">
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
            <span>Execution Architecture</span>
          </div>
          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white mb-5">
            The on-device agent pipeline.
          </h2>
          <p className="text-base sm:text-lg text-zinc-400 font-normal leading-relaxed">
            Zero cloud mediation. From acoustic microphone waveform to GBNF-constrained reasoning and neural audio synthesis.
          </p>
        </div>

        {/* Horizontal Pipeline Steps Bar */}
        <div className="grid grid-cols-1 md:grid-cols-5 gap-3 mb-10">
          {STAGES.map((stg, idx) => {
            const isSelected = activeStageIndex === idx;
            return (
              <button
                key={stg.step}
                type="button"
                onClick={() => setActiveStageIndex(idx)}
                className={`text-left p-4 rounded-xl border transition-all duration-150 interactive-card relative ${
                  isSelected
                    ? 'bg-zinc-900 border-white/30 shadow-[0_0_25px_rgba(255,255,255,0.06)]'
                    : 'bg-zinc-900/30 border-white/[0.06] hover:border-white/15'
                }`}
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="font-mono text-xs font-bold text-zinc-500 tabular-nums">
                    STAGE {stg.step}
                  </span>
                  <div
                    className={`w-7 h-7 rounded-lg flex items-center justify-center ${
                      isSelected ? 'bg-zinc-800 text-white' : 'bg-zinc-900 text-zinc-400'
                    }`}
                  >
                    {stg.icon}
                  </div>
                </div>
                <h4 className="text-sm font-bold text-white mb-1">{stg.name}</h4>
                <p className="text-[11px] text-zinc-400 line-clamp-1">{stg.tagline}</p>
              </button>
            );
          })}
        </div>

        {/* Detailed Stage Deep Dive Card */}
        <div className="rounded-2xl glass-panel-elevated p-6 sm:p-8 border border-white/15">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left 6 cols: Description & Specs */}
            <div className="lg:col-span-6 space-y-6">
              <div className="flex items-center gap-3">
                <span className="font-mono text-xs px-2.5 py-1 rounded bg-zinc-800 border border-white/10 text-zinc-300">
                  STAGE {activeStage.step}
                </span>
                <h3 className="text-2xl font-bold text-white tracking-tight">
                  {activeStage.name}
                </h3>
              </div>

              <p className="text-sm text-zinc-300 leading-relaxed font-normal">
                {activeStage.details}
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
                {activeStage.specs.map((sp, sIdx) => (
                  <div key={sIdx} className="p-3 rounded-lg bg-zinc-900/80 border border-white/5">
                    <span className="text-[10px] font-mono text-zinc-500 block mb-1 uppercase tracking-wider">
                      {sp.label}
                    </span>
                    <span className="text-xs font-semibold text-zinc-200 block">
                      {sp.value}
                    </span>
                  </div>
                ))}
              </div>

              <div className="flex items-center gap-2 text-xs font-mono text-emerald-400 pt-2">
                <ShieldCheck className="w-4 h-4" />
                <span>Zero telemetry: 100% on-device memory scope</span>
              </div>
            </div>

            {/* Right 6 cols: Code Snippet / AST visualization */}
            <div className="lg:col-span-6">
              <div className="rounded-xl bg-black border border-white/10 overflow-hidden shadow-2xl">
                <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/5 bg-zinc-900/60">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-zinc-700" />
                    <span className="w-2.5 h-2.5 rounded-full bg-zinc-700" />
                    <span className="w-2.5 h-2.5 rounded-full bg-zinc-700" />
                    <span className="text-xs font-mono text-zinc-400 ml-2">pipeline_stage_{activeStage.step}.kt</span>
                  </div>
                  <span className="text-[10px] font-mono text-zinc-500 uppercase">SYNTAX VERIFIED</span>
                </div>
                <div className="p-4 overflow-x-auto text-xs font-mono leading-relaxed text-zinc-300">
                  <pre>
                    <code>{activeStage.codeSnippet}</code>
                  </pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
