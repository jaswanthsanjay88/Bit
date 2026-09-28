import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Terminal, Copy, Check, ArrowUpRight } from 'lucide-react';
import { springs } from '../lib/motion';

interface TerminalTab {
  id: string;
  name: string;
  filename: string;
  command: string;
  description: string;
}

const TABS: TerminalTab[] = [
  {
    id: 'adb',
    name: 'ADB Direct Sideload',
    filename: 'install_bit.sh',
    command: `# 1. Download release APK directly from GitHub releases
curl -L -O https://github.com/jaswanthsanjay88/Bit_Android/releases/download/v2.1.1/app-release-v2.1.1.apk

# 2. Sideload directly to connected Android device via ADB
adb install -r -d app-release-v2.1.1.apk

# 3. Launch BIT sovereign runtime
adb shell am start -n com.martha.app/.MainActivity`,
    description: 'One-line terminal deployment. Requires Developer Options enabled on Android 12+.'
  },
  {
    id: 'gbnf',
    name: 'GBNF Tool Grammar',
    filename: 'tool_call.gbnf',
    command: `root ::= ToolCall
ToolCall ::= "{\\n" 
  "  \\"tool\\": \\"" ToolName "\\",\\n" 
  "  \\"parameters\\": " Parameters "\\n" 
"}"

ToolName ::= "web_search" | "web_fetch" | "calculator" | "sqlite_query"
Parameters ::= "{" ws (Entry ("," ws Entry)*)? ws "}"
Entry ::= "\\"" [a-zA-Z0-9_]+ "\\": " Value
Value ::= String | Number | Boolean
String ::= "\\"" ([^"\\\\] | "\\\\" .)* "\\""
Number ::= ("-"? [0-9]+ ("." [0-9]+)?)
Boolean ::= "true" | "false"
ws ::= [ \\t\\n]*`,
    description: 'Backus-Naur grammar fed to llama_sampler to mathematically clamp model logits.'
  },
  {
    id: 'sideload',
    name: 'Manual Model Push',
    filename: 'push_weights.sh',
    command: `# Push pre-downloaded GGUF directly to BIT app directory
adb push Qwen3.5-0.8B-Q4_K_M.gguf /sdcard/Android/data/com.martha.app/files/models/

# Verify permissions and SHA256 integrity
adb shell sha256sum /sdcard/Android/data/com.martha.app/files/models/Qwen3.5-0.8B-Q4_K_M.gguf`,
    description: 'Bypasses in-app download for developers with local model weight caches on their host machine.'
  }
];

export const TerminalBlock: React.FC = () => {
  const [activeTabId, setActiveTabId] = useState('adb');
  const [copied, setCopied] = useState(false);
  const activeTab = TABS.find((t) => t.id === activeTabId) || TABS[0];

  const handleCopy = () => {
    navigator.clipboard.writeText(activeTab.command);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section id="terminal" className="py-28 md:py-36 bg-[#07080A] relative">
      <div className="max-w-[1100px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs font-mono text-[#B6FF3B] mb-4">
            <Terminal className="w-3.5 h-3.5" />
            <span>Developer Deployment</span>
          </div>
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-[-0.04em] text-white mb-4">
            One command to install.
          </h2>
          <p className="text-lg text-[#A1A1AA]">
            Deploy directly to your connected Android phone via ADB or copy the GBNF tool grammar.
          </p>
        </div>

        {/* Wider Terminal Window with macOS Chrome */}
        <div className="rounded-2xl surface-2 border border-white/15 overflow-hidden shadow-[0_30px_90px_rgba(0,0,0,0.95)]">
          {/* Top Window Chrome: Three Dots & Tabs */}
          <div className="flex flex-wrap items-center justify-between border-b border-white/10 bg-[#0E1015] px-5 py-3.5">
            <div className="flex items-center gap-4">
              {/* Three Dots Window Chrome */}
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-[#FF5F56]" />
                <span className="w-3 h-3 rounded-full bg-[#FFBD2E]" />
                <span className="w-3 h-3 rounded-full bg-[#27C93F]" />
              </div>

              {/* Tabs */}
              <div className="flex items-center gap-1.5 ml-2">
                {TABS.map((tab) => {
                  const isActive = activeTabId === tab.id;
                  return (
                    <button
                      key={tab.id}
                      type="button"
                      onClick={() => {
                        setActiveTabId(tab.id);
                        setCopied(false);
                      }}
                      className={`relative px-3.5 py-1.5 rounded-lg text-xs font-mono transition-colors ${
                        isActive ? 'text-white font-semibold' : 'text-zinc-400 hover:text-white'
                      }`}
                    >
                      {isActive && (
                        <motion.div
                          layoutId="terminal-tab-pill"
                          className="absolute inset-0 rounded-lg bg-white/[0.08] border border-white/10"
                          transition={springs.snappy}
                        />
                      )}
                      <span className="relative z-10">{tab.name}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Large Copy Button */}
            <div className="flex items-center gap-3 mt-2 sm:mt-0">
              <span className="text-xs font-mono text-zinc-500 hidden sm:inline">
                {activeTab.filename}
              </span>
              <motion.button
                type="button"
                onClick={handleCopy}
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.96 }}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-[#07080A] hover:bg-[#141720] border border-white/15 text-xs font-mono font-medium text-white transition-all shadow-sm"
              >
                {copied ? (
                  <>
                    <Check className="w-4 h-4 text-[#B6FF3B]" />
                    <span className="text-[#B6FF3B] font-bold">Copied to Clipboard</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4 text-zinc-400" />
                    <span>Copy Command</span>
                  </>
                )}
              </motion.button>
            </div>
          </div>

          {/* Syntax-Colored Terminal Body */}
          <div className="p-6 sm:p-8 bg-[#07080A] overflow-x-auto text-sm sm:text-base font-mono leading-relaxed text-zinc-200">
            <pre className="text-zinc-300">
              <code>{activeTab.command}</code>
            </pre>
          </div>

          {/* Footer Bar */}
          <div className="px-6 sm:px-8 py-4 border-t border-white/5 bg-[#0E1015]/60 flex flex-wrap items-center justify-between gap-4 text-xs text-zinc-400">
            <p>{activeTab.description}</p>
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android/releases"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-zinc-300 hover:text-white font-medium"
            >
              <span>GitHub Release Packages</span>
              <ArrowUpRight className="w-3.5 h-3.5 text-[#B6FF3B]" />
            </a>
          </div>
        </div>
      </div>
    </section>
  );
};
