import React from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { TrustRow } from './components/TrustRow';
import { FeatureStories } from './components/FeatureStories';
import { UserPainComparison } from './components/UserPainComparison';
import { SimpleHowItWorks } from './components/SimpleHowItWorks';
import { FounderNote } from './components/FounderNote';
import { FaqSection } from './components/FaqSection';
import { FinalCta } from './components/FinalCta';
import { Footer } from './components/Footer';
import { ScrollProgress } from './components/motion/ScrollProgress';
import { CustomCursor } from './components/motion/CustomCursor';
import { useLenis } from './lib/useLenis';

export const App: React.FC = () => {
  // Initialize smooth scrolling with Lenis
  useLenis();

  return (
    <div className="min-h-screen bg-white text-[#6b6b6b] flex flex-col selection:bg-[#0a0a0a] selection:text-white">
      {/* 1px Scroll Progress Hairline at very top */}
      <ScrollProgress />

      {/* Additive Pointer Indicator for desktop */}
      <CustomCursor />

      {/* 01. Floating Pill Navigation */}
      <Navbar />

      <main className="flex-1">
        {/* 02. Minimal Light Hero with Choreography & Alive Mockup */}
        <Hero />

        {/* 03. Section A: Stats Row (4-col grid with vertical hairlines) */}
        <TrustRow />

        {/* 04. Section B: Feature Sections (reusable open layout with live traces) */}
        <FeatureStories />

        {/* 05. Section C: Cloud vs. On-Device (Two-column hairline table) */}
        <UserPainComparison />

        {/* 06. Section D: How It Works (01 02 03 outline numerals with scroll connector) */}
        <SimpleHowItWorks />

        {/* 07. Section E: Founder Note (640px open column with avatar ring) */}
        <FounderNote />

        {/* 08. Section E: FAQ Accordion (hairline rows with +/- morph) */}
        <FaqSection />

        {/* 09. Section E: Final Dark CTA Card with clip-path & sheen sweep */}
        <FinalCta />
      </main>

      {/* 10. Minimal 1-Row Footer with growing link underlines */}
      <Footer />
    </div>
  );
};

export default App;
