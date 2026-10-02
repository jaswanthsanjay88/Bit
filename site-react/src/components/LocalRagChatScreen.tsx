import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence, useInView, useReducedMotion } from 'motion/react';
import { Plane, ShieldCheck, FileText, Mic, AudioWaveform, ChevronDown, Check, Menu } from 'lucide-react';
import { BitLogo } from './BitLogo';
import { spring, ease } from '../lib/motion';

export const LocalRagChatScreen: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isInView = useInView(containerRef, { amount: 0.2 });
  const shouldReduceMotion = useReducedMotion();

  const fullPrompt = 'Audit contract_nda.pdf in Memory Vault and summarize obligations.';
  const [typedPrompt, setTypedPrompt] = useState(fullPrompt);
  const [step, setStep] = useState(4); // 0: typing, 1: tool call, 2: response card, 3: saved pill, 4: complete
  const [cycleKey, setCycleKey] = useState(0);

  useEffect(() => {
    if (shouldReduceMotion) {
      setTypedPrompt(fullPrompt);
      setStep(4);
      return;
    }

    if (!isInView) return;

    let isVisible = !document.hidden;
    const handleVisibility = () => {
      isVisible = !document.hidden;
    };
    document.addEventListener('visibilitychange', handleVisibility);

    let typingTimeout: any;
    let toolTimeout: any;
    let cardTimeout: any;
    let pillTimeout: any;
    let resetTimeout: any;

    const startCycle = () => {
      if (!isVisible) return;
      setTypedPrompt('');
      setStep(0);

      // Typing loop at ~35 chars/sec (~28ms per char)
      let charIdx = 0;
      const typeNextChar = () => {
        if (!isVisible) return;
        if (charIdx < fullPrompt.length) {
          charIdx++;
          setTypedPrompt(fullPrompt.slice(0, charIdx));
          typingTimeout = setTimeout(typeNextChar, 28);
        } else {
          // Finished typing, proceed to tool call after 250ms
          toolTimeout = setTimeout(() => {
            setStep(1);
            // Response card appears after 200ms
            cardTimeout = setTimeout(() => {
              setStep(2);
              // Saved pill appears after 250ms
              pillTimeout = setTimeout(() => {
                setStep(3);
                // Keep showing complete state until ~9s loop reset
                resetTimeout = setTimeout(() => {
                  setCycleKey((k) => k + 1);
                }, 4500);
              }, 250);
            }, 200);
          }, 250);
        }
      };

      typeNextChar();
    };

    const initialDelay = setTimeout(() => {
      startCycle();
    }, 4000);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      clearTimeout(initialDelay);
      clearTimeout(typingTimeout);
      clearTimeout(toolTimeout);
      clearTimeout(cardTimeout);
      clearTimeout(pillTimeout);
      clearTimeout(resetTimeout);
    };
  }, [isInView, cycleKey, shouldReduceMotion]);

  return (
    <div
      ref={containerRef}
      className="w-full h-full bg-[#0B0D11] text-zinc-200 flex flex-col justify-between p-3 sm:p-3.5 font-sans select-none text-left"
    >
      {/* Android Status Bar */}
      <div className="flex items-center justify-between text-xs text-zinc-400 pt-0.5 pb-2 font-mono">
        <span className="font-semibold text-white tracking-tight">12:47</span>
        <div className="flex items-center gap-2">
          {/* Airplane Mode (Demonstrating 100% Offline) */}
          <span className="inline-flex items-center gap-1 text-[#A8A29E] bg-white/[0.06] px-1.5 py-0.5 rounded text-[10px]">
            <Plane className="w-2.5 h-2.5" />
            <span>Offline</span>
          </span>
          <div className="w-4 h-2 border border-zinc-500 rounded-sm p-[1px] flex items-center">
            <div className="w-full h-full bg-white rounded-[1px]" />
          </div>
        </div>
      </div>

      {/* App Header Bar - Strictly Single Line */}
      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] whitespace-nowrap">
        <div className="flex items-center gap-1.5 shrink-0">
          <Menu className="w-4 h-4 text-zinc-400" />
          <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-white/[0.08] border border-white/10 text-[11px]">
            <BitLogo className="w-2.5 h-2.5 text-white" />
            <span className="font-medium text-white tracking-tight">Ready: qwen-3.5</span>
            <ChevronDown className="w-3 h-3 text-zinc-400" />
          </div>
        </div>
        <div className="flex items-center gap-1 text-[11px] text-zinc-400 font-mono shrink-0">
          <ShieldCheck className="w-3.5 h-3.5 text-white" />
          <span>Local Vault</span>
        </div>
      </div>

      {/* Chat Messages Area */}
      <div className="flex-1 py-2 space-y-2 overflow-hidden flex flex-col justify-start">
        {/* User Prompt with Typing Simulation */}
        <div className="flex justify-end pt-0.5">
          <div className="max-w-[92%] rounded-2xl rounded-tr-sm bg-neutral-800 text-white px-3 py-1.5 text-[12.5px] leading-snug shadow-sm min-h-[32px] flex items-center">
            <span>{shouldReduceMotion ? fullPrompt : typedPrompt}</span>
            {step === 0 && !shouldReduceMotion && (
              <span className="inline-block w-1.5 h-3.5 bg-white ml-0.5 animate-pulse" />
            )}
          </div>
        </div>

        {/* Local Tool Execution Badge (Appears at step >= 1) */}
        {(shouldReduceMotion || step >= 1) && (
          <motion.div
            initial={shouldReduceMotion ? false : { opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease }}
            className="flex items-center gap-1.5 text-[10.5px] sm:text-xs text-zinc-300 px-0.5 font-mono whitespace-nowrap"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-white shrink-0" />
            <span className="font-medium text-white sm:hidden">vector_query</span>
            <span className="font-medium text-white hidden sm:inline">vault_vector_query</span>
            <span className="text-zinc-500">&bull;</span>
            <span className="text-zinc-300">38ms</span>
            <span className="text-zinc-500">&bull;</span>
            <span className="text-white font-medium whitespace-nowrap animate-shimmer">0B network</span>
          </motion.div>
        )}

        {/* Assistant Response Card (Appears at step >= 2) */}
        {(shouldReduceMotion || step >= 2) && (
          <motion.div
            initial={shouldReduceMotion ? false : { opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, ease }}
            className="rounded-2xl rounded-tl-sm bg-[#14161C] border border-white/10 p-3 text-xs leading-relaxed space-y-1.5 text-zinc-200 shadow-md"
          >
            <div className="flex items-center gap-1.5 font-medium text-white text-[12.5px]">
              <FileText className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
              <span>contract_nda.pdf &bull; Section 4</span>
            </div>

            <p className="text-zinc-300 text-xs">
              Audited offline via local 384-dim BGE embeddings:
            </p>

            <ul className="space-y-1 text-zinc-300 text-xs">
              <li className="flex items-start gap-1.5">
                <Check className="w-3.5 h-3.5 text-white shrink-0 mt-0.5" />
                <span><strong>Term:</strong> 2-year confidentiality clause for weights.</span>
              </li>
              <li className="flex items-start gap-1.5">
                <Check className="w-3.5 h-3.5 text-white shrink-0 mt-0.5" />
                <span><strong>Sovereignty:</strong> Zero cloud telemetry or external egress.</span>
              </li>
            </ul>

            <div className="pt-1.5 border-t border-white/[0.06] flex items-center justify-between text-xs text-zinc-400 font-mono">
              <span>RAM: 620 MB</span>
              <span className="text-white font-medium whitespace-nowrap">100% On-Device</span>
            </div>
          </motion.div>
        )}

        {/* Saved to Vault Chip (Appears at step >= 3 with scale spring) */}
        {(shouldReduceMotion || step >= 3) && (
          <motion.div
            initial={shouldReduceMotion ? false : { opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={spring}
            className="flex items-center justify-between bg-white/[0.04] border border-white/10 rounded-xl px-3 py-1.5 text-xs text-zinc-300 shadow-sm"
          >
            <div className="flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5 text-white shrink-0" />
              <span className="font-medium text-white text-xs">Saved audit to Memory Vault</span>
            </div>
            <span className="text-[10px] text-zinc-400 font-mono uppercase tracking-wider bg-white/[0.06] px-1.5 py-0.5 rounded">
              Encrypted
            </span>
          </motion.div>
        )}

        {/* Follow-up Action Card */}
        {(shouldReduceMotion || step >= 3) && (
          <motion.div
            initial={shouldReduceMotion ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.2 }}
            className="flex items-center justify-between bg-white/[0.03] border border-white/5 rounded-xl px-3 py-1.5 text-xs text-zinc-300"
          >
            <span className="text-zinc-300 text-[11.5px]">Export 2 clauses to Calendar?</span>
            <span className="text-white text-[11px] font-medium bg-white/10 border border-white/10 px-2 py-0.5 rounded-md cursor-pointer hover:bg-white/20">
              Confirm
            </span>
          </motion.div>
        )}
      </div>

      {/* Bottom Controls */}
      <div className="pt-1 space-y-1.5">
        <div className="flex items-center gap-1">
          <div className="inline-flex rounded-full p-0.5 bg-white/[0.06] border border-white/10 text-[11px] font-medium">
            <span className="px-2 py-0.5 rounded-full bg-white text-black font-semibold">Agent</span>
            <span className="px-2 py-0.5 text-zinc-400">Chat</span>
          </div>
        </div>

        <div className="flex items-center justify-between bg-white/[0.06] border border-white/10 rounded-full px-3 py-2 text-xs text-zinc-400">
          <div className="flex items-center gap-2">
            <span className="text-white text-base leading-none font-light">+</span>
            <span>Ask me anything...</span>
          </div>
          <div className="flex items-center gap-2 text-zinc-300">
            <Mic className="w-3.5 h-3.5" />
            <div className="w-5 h-5 rounded-full bg-white flex items-center justify-center text-black">
              <AudioWaveform className="w-3 h-3 text-black" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
