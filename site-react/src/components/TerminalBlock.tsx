import React, { useState } from 'react';
import { Terminal, Copy, Check, Download, GitBranch, ArrowUpRight } from 'lucide-react';

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
    name: 'ADB Install',
    filename: 'install_bit.sh',
    command: `# 1. Download release APK directly from GitHub
curl -L -O https://github.com/jaswanthsanjay88/Bit_Android/releases/download/v2.1.1/app-release-v2.1.1.apk

# 2. Sideload directly to connected Android device via ADB
adb install -r -d app-release-v2.1.1.apk

# 3. Launch BIT sovereign agent runtime
adb shell am start -n com.martha.app/.MainActivity`,
    description: 'Direct ADB command line deployment. Requires Developer Options enabled on Android 12+.'
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
    description: 'Backus-Naur grammar fed to llama_sampler to constrain model logits to valid tool calls.'
  },
  {
    id: 'sideload',
    name: 'Model Sideload',
    filename: 'push_weights.sh',
    command: `# Sideload any pre-downloaded GGUF directly to BIT model directory
adb push Qwen3.5-0.8B-Q4_K_M.gguf /sdcard/Android/data/com.martha.app/files/models/

# Verify permissions and checksum
adb shell sha256sum /sdcard/Android/data/com.martha.app/files/models/Qwen3.5-0.8B-Q4_K_M.gguf`,
    description: 'Bypasses in-app download for users with local model caches on their host machine.'
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
    <section id="terminal" className="py-24 bg-black relative">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-12">
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded bg-zinc-900 border border-white/10 text-[11px] font-mono text-zinc-400 uppercase tracking-wider mb-4">
            <Terminal className="w-3.5 h-3.5" />
            <span>Developer Sideload & Tool Schemas</span>
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-white mb-3">
            One-line terminal deployment.
          </h2>
          <p className="text-sm text-zinc-400">
            Install the release APK via ADB, sideload quantized GGUFs, or inspect the GBNF grammar constraints.
          </p>
        </div>

        {/* Monospace Terminal Card */}
        <div className="rounded-2xl glass-panel-elevated border border-white/15 overflow-hidden shadow-[0_20px_60px_rgba(0,0,0,0.9)]">
          {/* Top Tabs Bar */}
          <div className="flex flex-wrap items-center justify-between border-b border-white/10 bg-zinc-950/80 px-4 py-2.5">
            <div className="flex items-center gap-1.5 sm:gap-2">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => {
                    setActiveTabId(tab.id);
                    setCopied(false);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-mono transition-all duration-150 ${
                    activeTabId === tab.id
                      ? 'bg-zinc-800 text-white font-semibold border border-white/10'
                      : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900'
                  }`}
                >
                  {tab.name}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2 mt-2 sm:mt-0">
              <span className="text-[11px] font-mono text-zinc-500 hidden sm:inline">
                {activeTab.filename}
              </span>
              <button
                type="button"
                onClick={handleCopy}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-zinc-900 hover:bg-zinc-800 border border-white/10 text-xs font-mono text-zinc-300 hover:text-white transition-all active:scale-95"
              >
                {copied ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                    <span className="text-emerald-400 font-semibold">Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5 text-zinc-400" />
                    <span>Copy Command</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Terminal Code Display */}
          <div className="p-6 bg-zinc-950/95 overflow-x-auto text-xs sm:text-sm font-mono leading-relaxed text-zinc-200">
            <pre>
              <code>{activeTab.command}</code>
            </pre>
          </div>

          {/* Description footer */}
          <div className="px-6 py-3 border-t border-white/5 bg-zinc-900/40 flex flex-wrap items-center justify-between gap-4 text-xs text-zinc-400">
            <p>{activeTab.description}</p>
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android/releases"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-zinc-300 hover:text-white font-medium"
            >
              <span>GitHub Releases</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </a>
          </div>
        </div>
      </div>
    </section>
  );
};
