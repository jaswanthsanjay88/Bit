import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Menu, X, Download, ArrowUpRight } from 'lucide-react';
import { GithubIcon } from './GithubIcon';
import { springs } from '../lib/motion';

export const Navbar: React.FC = () => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-200 ${
        isScrolled
          ? 'bg-black/90 backdrop-blur-md border-b border-white/[0.08] shadow-[0_4px_24px_rgba(0,0,0,0.8)]'
          : 'bg-transparent border-b border-transparent'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo & Brand */}
          <div className="flex items-center gap-3">
            <a href="#" className="flex items-center gap-2.5 group">
              <div className="w-8 h-8 rounded-lg bg-zinc-950 border border-white/10 flex items-center justify-center p-1.5 transition-colors group-hover:border-white/30 shadow-sm">
                <img src="/img/ic_logo.svg" alt="BIT Logo" className="w-full h-full object-contain filter invert opacity-90" />
              </div>
              <span className="font-bold tracking-tight text-white text-lg">BIT</span>
              <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-zinc-900 text-zinc-400 border border-white/10 tabular-nums">
                v2.1.1
              </span>
            </a>
          </div>

          {/* Desktop Nav Links (4 Big-Tech Links) */}
          <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-zinc-400">
            <a href="#features" className="hover:text-white transition-colors duration-150">
              Features
            </a>
            <a href="#comparison" className="hover:text-white transition-colors duration-150">
              Why Sovereign
            </a>
            <a href="#requirements" className="hover:text-white transition-colors duration-150">
              Requirements
            </a>
            <a href="#faq" className="hover:text-white transition-colors duration-150">
              FAQ
            </a>
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 hover:text-white transition-colors duration-150"
            >
              <GithubIcon className="w-4 h-4" />
              <span>GitHub</span>
              <ArrowUpRight className="w-3 h-3 text-zinc-600" />
            </a>
          </nav>

          {/* Action Button */}
          <div className="hidden md:flex items-center gap-3">
            <motion.a
              href="#terminal"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.96 }}
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg bg-white text-black hover:bg-zinc-200 transition-colors shadow-[0_0_20px_rgba(255,255,255,0.15)]"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download APK</span>
            </motion.a>
          </div>

          {/* Mobile menu trigger */}
          <div className="flex md:hidden items-center">
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 border border-white/5 transition-colors tactile-button"
              aria-label="Toggle Navigation Menu"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Drawer */}
      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={springs.snappy}
            className="md:hidden bg-zinc-950/95 backdrop-blur-xl border-b border-white/10 px-4 pt-3 pb-6 space-y-3 overflow-hidden"
          >
            <a
              href="#features"
              onClick={() => setMobileMenuOpen(false)}
              className="block px-3 py-2 rounded-md text-sm font-medium text-zinc-300 hover:bg-zinc-900 hover:text-white"
            >
              Features
            </a>
            <a
              href="#comparison"
              onClick={() => setMobileMenuOpen(false)}
              className="block px-3 py-2 rounded-md text-sm font-medium text-zinc-300 hover:bg-zinc-900 hover:text-white"
            >
              Why Sovereign
            </a>
            <a
              href="#requirements"
              onClick={() => setMobileMenuOpen(false)}
              className="block px-3 py-2 rounded-md text-sm font-medium text-zinc-300 hover:bg-zinc-900 hover:text-white"
            >
              Requirements
            </a>
            <a
              href="#faq"
              onClick={() => setMobileMenuOpen(false)}
              className="block px-3 py-2 rounded-md text-sm font-medium text-zinc-300 hover:bg-zinc-900 hover:text-white"
            >
              FAQ
            </a>
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-zinc-300 hover:bg-zinc-900 hover:text-white"
            >
              <GithubIcon className="w-4 h-4" />
              <span>GitHub Repository</span>
            </a>
            <div className="pt-2">
              <a
                href="#terminal"
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center justify-center gap-2 w-full px-4 py-2.5 text-xs font-semibold rounded-lg bg-white text-black hover:bg-zinc-200 transition-colors"
              >
                <Download className="w-4 h-4" />
                <span>Download APK (v2.1.1)</span>
              </a>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
};
