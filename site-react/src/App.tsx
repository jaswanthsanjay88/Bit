import React from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { SocialProof } from './components/SocialProof';
import { FeatureStories } from './components/FeatureStories';
import { SimpleHowItWorks } from './components/SimpleHowItWorks';
import { UserPainComparison } from './components/UserPainComparison';
import { DeviceRequirements } from './components/DeviceRequirements';
import { TerminalBlock } from './components/TerminalBlock';
import { ModelStore } from './components/ModelStore';
import { FaqSection } from './components/FaqSection';
import { FinalCta } from './components/FinalCta';
import { Footer } from './components/Footer';

export const App: React.FC = () => {
  return (
    <div className="min-h-screen bg-black text-zinc-100 flex flex-col selection:bg-zinc-800 selection:text-white">
      {/* 01. Minimal Navigation Bar */}
      <Navbar />

      <main className="flex-1">
        {/* 02. Hero with Live Cockpit Simulator */}
        <Hero />

        {/* 03. Social Proof & Architecture Credibility */}
        <SocialProof />

        {/* 04. The Big 3 Feature Stories (Full-Width Chapters) */}
        <FeatureStories />

        {/* 05. How It Works (Simple 3-Step Flow) */}
        <SimpleHowItWorks />

        {/* 06. Proof & Trust (Cloud Trap vs Sovereign Freedom) */}
        <UserPainComparison />

        {/* 07. Device Compatibility & RAM Tiers */}
        <DeviceRequirements />

        {/* 08. 1-Click ADB Install & Terminal Sideload */}
        <TerminalBlock />

        {/* 09. Verified Model Matrix (Clean Preview + Expandable) */}
        <ModelStore />

        {/* 10. Interactive FAQ Accordion */}
        <FaqSection />

        {/* 11. Final High-Impact Call to Action */}
        <FinalCta />
      </main>

      {/* 12. Minimalist Dense Footer */}
      <Footer />
    </div>
  );
};

export default App;
