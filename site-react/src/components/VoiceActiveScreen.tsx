import React, { useRef, useState, useEffect } from 'react';
import { useInView, useReducedMotion } from 'motion/react';
import { X, Plus, AudioWaveform, ChevronLeft } from 'lucide-react';
import { BitLogo } from './BitLogo';

export const VoiceActiveScreen: React.FC = () => {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, amount: 0.3 });
  const shouldReduceMotion = useReducedMotion();

  // Word-by-word streaming effect for assistant speech
  const fullText = 'Three core pillars: on-device GBNF grammars, zero-cloud SQLite vector retrieval, and local streaming Whisper audio under 150ms.';
  const words = fullText.split(' ');
  const [revealedWordCount, setRevealedWordCount] = useState(words.length);

  useEffect(() => {
    if (shouldReduceMotion) {
      setRevealedWordCount(words.length);
      return;
    }

    setRevealedWordCount(1);
    const interval = setInterval(() => {
      setRevealedWordCount((prev) => {
        if (prev < words.length) {
          return prev + 1;
        }
        clearInterval(interval);
        return prev;
      });
    }, 90);

    return () => clearInterval(interval);
  }, [shouldReduceMotion]);

  // Dynamic waveform bars with staggered negative delays
  const waveformDelays = ['-0.2s', '-0.5s', '-0.1s', '-0.7s', '-0.3s', '-0.6s', '-0.4s', '-0.8s'];

  return (
    <div
      ref={ref}
      className="w-full h-full bg-[#07080A] text-zinc-100 flex flex-col justify-between p-4 font-sans select-none text-left relative overflow-hidden"
    >
      {/* Background Soft Glow behind Orb */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-48 h-48 bg-white/[0.04] rounded-full blur-2xl pointer-events-none" />

      {/* Android Status Bar */}
      <div className="flex items-center justify-between text-xs text-zinc-400 pt-0.5 pb-2 font-mono relative z-10">
        <span className="font-semibold text-white tracking-tight">12:47</span>
        <div className="flex items-center gap-2">
          <span className="text-[#A8A29E] bg-white/[0.08] px-1.5 py-0.5 rounded text-[10px] font-sans">
            Airplane mode
          </span>
          <div className="w-4 h-2 border border-zinc-500 rounded-sm p-[1px] flex items-center">
            <div className="w-full h-full bg-white rounded-[1px]" />
          </div>
        </div>
      </div>

      {/* Voice Mode Header */}
      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] relative z-10">
        <div className="flex items-center gap-2">
          <ChevronLeft className="w-4 h-4 text-zinc-400" />
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/[0.08] border border-white/10 text-xs">
            <BitLogo className="w-2.5 h-2.5 text-white" />
            <span className="font-medium text-white">Live Voice</span>
          </div>
        </div>
        <button className="p-1 rounded-full text-zinc-400 hover:text-white" aria-label="Close voice">
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* Live Transcript Area */}
      <div className="flex-1 py-3 space-y-3 overflow-hidden flex flex-col justify-start relative z-10">
        {/* User live speech transcription */}
        <div className="flex justify-end">
          <div className="max-w-[88%] rounded-2xl rounded-tr-sm bg-neutral-800/90 text-white px-3.5 py-2 text-[12.5px] leading-snug shadow-sm">
            &ldquo;Summarize the key architectural decisions from the offline spec.&rdquo;
          </div>
        </div>

        {/* Assistant live streaming response */}
        <div className="flex justify-start">
          <div className="max-w-[92%] rounded-2xl rounded-tl-sm bg-[#14161C] border border-white/10 p-3 text-[12px] leading-relaxed text-zinc-200 shadow-md">
            <span className="text-zinc-400 text-[10.5px] font-mono block mb-1">
              BIT Voice &bull; Piper Neural
            </span>
            &ldquo;
            {words.slice(0, revealedWordCount).join(' ')}
            {revealedWordCount < words.length && (
              <span className="inline-block w-1.5 h-3 ml-1 bg-white animate-pulse" />
            )}
            &rdquo;
          </div>
        </div>

        {/* Pulsing Voice Orb & Waveform Visualization */}
        <div className="my-auto py-4 flex flex-col items-center justify-center">
          <div className="relative flex items-center justify-center">
            {/* Breathing Ring */}
            <div className="absolute w-24 h-24 rounded-full border border-white/20 animate-breath pointer-events-none" />
            <div className="absolute w-20 h-20 rounded-full border border-white/10 pointer-events-none" />

            {/* Center Orb */}
            <div className="w-14 h-14 rounded-full bg-white text-black flex items-center justify-center shadow-[0_0_24px_rgba(255,255,255,0.3)]">
              <AudioWaveform className="w-7 h-7 text-black" />
            </div>
          </div>

          {/* Real Animated Waveform Bars */}
          <div className="flex items-center gap-1.5 mt-5 h-8">
            {waveformDelays.map((delay, idx) => (
              <span
                key={idx}
                className="w-1 h-7 bg-white rounded-full origin-bottom"
                style={{
                  animation: shouldReduceMotion ? 'none' : 'wave-bar 0.9s ease-in-out infinite',
                  animationDelay: delay,
                }}
              />
            ))}
          </div>

          <div className="mt-3 text-[11px] font-mono text-zinc-400">
            Speaking &bull; 118ms latency &bull; <span className="text-white animate-shimmer">0B cloud</span>
          </div>
        </div>
      </div>

      {/* Bottom Floating Voice Controls */}
      <div className="pt-2 flex items-center justify-center relative z-10">
        <div className="inline-flex items-center gap-4 px-5 py-2.5 rounded-full bg-[#181A20] border border-white/15 shadow-xl">
          <button className="p-2 rounded-full bg-white/[0.08] text-zinc-300 hover:text-white" aria-label="Add context">
            <Plus className="w-4 h-4" />
          </button>
          <button
            className="w-10 h-10 rounded-full bg-white text-black flex items-center justify-center shadow-md hover:scale-105 transition-transform"
            aria-label="Microphone"
          >
            <AudioWaveform className="w-5 h-5 text-black" />
          </button>
          <button className="p-2 rounded-full bg-white/[0.08] text-zinc-300 hover:text-white" aria-label="Mute or exit">
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
