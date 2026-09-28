import React from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { StatsStrip } from './components/StatsStrip';
import { UserPainComparison } from './components/UserPainComparison';
import { FeatureGrid } from './components/FeatureGrid';
import { HorizontalPipeline } from './components/HorizontalPipeline';
import { TerminalBlock } from './components/TerminalBlock';
import { ModelStore } from './components/ModelStore';
import { Footer } from './components/Footer';

export const App: React.FC = () => {
  return (
    <div className="min-h-screen bg-black text-zinc-100 flex flex-col selection:bg-zinc-800 selection:text-white">
      {/* Top Fixed Header */}
      <Navbar />

      {/* Main Content Sections */}
      <main className="flex-1">
        {/* Hero with Interactive Agent Cockpit Simulator */}
        <Hero />

        {/* Proof / Stat Strip */}
        <StatsStrip />

        {/* User Pain vs Sovereign Freedom Comparison */}
        <UserPainComparison />

        {/* Real Product Surfaces Feature Grid */}
        <FeatureGrid />

        {/* Horizontal Pipeline (How It Works) */}
        <HorizontalPipeline />

        {/* Terminal ADB Install & GBNF Grammar Block */}
        <TerminalBlock />

        {/* Interactive Model Store Catalog */}
        <ModelStore />
      </main>

      {/* Minimalist Dense Footer */}
      <Footer />
    </div>
  );
};

export default App;
