import React from 'react';
import { Feature } from './sections/Feature';
import { Phone } from './ui/Phone';
import { AgentToolChatScreen } from './AgentToolChatScreen';
import { VoiceActiveScreen } from './VoiceActiveScreen';
import { MemoryVaultPopulatedScreen } from './MemoryVaultPopulatedScreen';

export const FeatureStories: React.FC = () => {
  return (
    <section id="features" className="w-full bg-white relative">
      {/* 01. Autonomous Agents */}
      <Feature
        eyebrow="Autonomous Agents"
        title="Agents that act on your phone"
        description="Subagents decompose complex goals, execute local device tools, and run deterministic tasks with zero cloud reliance."
        bullets={[
          'GBNF context-free grammars guarantee valid tool call outputs',
          'Multi-step agent planning executed in private on-device sandbox',
        ]}
        phone={
          <Phone variant="static" size="lg">
            <AgentToolChatScreen />
          </Phone>
        }
        side="left"
        showDivider={true}
      />

      {/* 02. Offline Voice Mode */}
      <Feature
        eyebrow="Offline Audio"
        title="Talk naturally, even in airplane mode"
        description="Real-time neural speech recognition and voice synthesis run directly on your phone's processor without sending audio to any server."
        bullets={[
          'Sherpa-ONNX streaming Whisper for instantaneous voice transcription',
          'On-device Piper neural TTS for responsive conversational dialogue',
        ]}
        phone={
          <Phone variant="static" size="lg">
            <VoiceActiveScreen />
          </Phone>
        }
        side="right"
        showDivider={true}
      />

      {/* 03. Private Vector Retrieval */}
      <Feature
        eyebrow="Private Retrieval"
        title="Your documents stay private"
        description="Index contracts, notes, and personal PDFs into an encrypted on-device vector vault for zero-leak retrieval and semantic search."
        bullets={[
          'Local 384-dimensional BGE vector embeddings generated on device',
          'Encrypted on-device SQLite database with zero external telemetry',
        ]}
        phone={
          <Phone variant="static" size="lg">
            <MemoryVaultPopulatedScreen />
          </Phone>
        }
        side="left"
        showDivider={false}
      />
    </section>
  );
};
