import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence, useScroll, useReducedMotion } from 'motion/react';
import { Menu, X, Plane } from 'lucide-react';
import { BitLogo } from './BitLogo';
import { GithubIcon } from './GithubIcon';
import { Button } from './ui/Button';
import { Magnetic } from './motion/Magnetic';
import { spring, ease } from '../lib/motion';

export const Navbar: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<string | null>(null);
  const [isScrolled, setIsScrolled] = useState(false);
  const [isVisible, setIsVisible] = useState(true);
  const [showPlaneEasterEgg, setShowPlaneEasterEgg] = useState(false);
  const [showToast, setShowToast] = useState(false);

  const lastScrollY = useRef(0);
  const keyPressTimestamps = useRef<number[]>([]);

  // Track scroll direction for hide/show, >24px border darkening, and active section
  useEffect(() => {
    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      setIsScrolled(currentScrollY > 24);

      if (currentScrollY > 120) {
        if (currentScrollY > lastScrollY.current + 6) {
          setIsVisible(false); // scrolling down
        } else if (currentScrollY < lastScrollY.current - 6) {
          setIsVisible(true); // scrolling up
        }
      } else {
        setIsVisible(true);
      }

      // Active nav section tracking (null at top of page)
      if (currentScrollY < 300) {
        setActiveTab(null);
      } else {
        const sections = [
          { id: 'features', name: 'Features' },
          { id: 'comparison', name: 'Why BIT' },
          { id: 'faq', name: 'FAQ' },
        ];
        let currentSection: string | null = null;
        for (const sec of sections) {
          const el = document.getElementById(sec.id);
          if (el) {
            const rect = el.getBoundingClientRect();
            if (rect.top <= window.innerHeight * 0.45 && rect.bottom >= window.innerHeight * 0.2) {
              currentSection = sec.name;
            }
          }
        }
        if (currentSection) {
          setActiveTab(currentSection);
        }
      }

      lastScrollY.current = currentScrollY;
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Easter Egg: Pressing 'b' 3 times quickly toggles airplane mode animation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key.toLowerCase() === 'b') {
        const now = Date.now();
        keyPressTimestamps.current = [...keyPressTimestamps.current.filter((t) => now - t < 1200), now];
        if (keyPressTimestamps.current.length >= 3) {
          keyPressTimestamps.current = [];
          setShowPlaneEasterEgg(true);
          setShowToast(true);
          setTimeout(() => setShowPlaneEasterEgg(false), 2400);
          setTimeout(() => setShowToast(false), 3400);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const navLinks = [
    { name: 'Features', href: '#features' },
    { name: 'Why BIT', href: '#comparison' },
    { name: 'FAQ', href: '#faq' },
  ];

  return (
    <>
      <motion.header
        initial={shouldReduceMotion ? false : { y: -16, opacity: 0 }}
        animate={{
          y: isVisible ? 0 : -80,
          opacity: isVisible ? 1 : 0,
        }}
        transition={{ duration: 0.35, ease }}
        className="fixed top-4 sm:top-5 inset-x-0 mx-auto z-50 w-[calc(100%-2rem)] max-w-[800px] pointer-events-auto"
      >
        <div
          className={`relative h-11 sm:h-12 rounded-full px-3.5 sm:px-5 flex items-center justify-between transition-all duration-300 backdrop-blur-xl bg-white/85 ${
            isScrolled ? 'border border-[var(--line-strong)]' : 'border border-[var(--line)]'
          }`}
        >
          {/* Airplane easter egg glider */}
          <AnimatePresence>
            {showPlaneEasterEgg && !shouldReduceMotion && (
              <motion.div
                initial={{ x: '-10%', opacity: 0 }}
                animate={{ x: '110%', opacity: [0, 1, 1, 0] }}
                exit={{ opacity: 0 }}
                transition={{ duration: 2.2, ease: 'easeInOut' }}
                className="absolute inset-y-0 left-0 flex items-center pointer-events-none z-30 text-[#0a0a0a]"
              >
                <Plane className="w-4 h-4 rotate-45" />
              </motion.div>
            )}
          </AnimatePresence>

          {/* Brand Logo */}
          <a
            href="#"
            className="flex items-center gap-2 focus-ring rounded-full py-1 pr-2 group"
            aria-label="BIT Homepage"
          >
            <div className="w-6 h-6 rounded-md bg-[#0a0a0a] flex items-center justify-center text-white transition-transform group-hover:scale-105">
              <BitLogo className="w-3.5 h-3.5 text-white" />
            </div>
            <span className="font-semibold text-[#0a0a0a] tracking-tight text-[15px]">
              BIT
            </span>
          </a>

          {/* Desktop Nav Links with Shared Indicator */}
          <nav className="hidden md:flex items-center gap-1" aria-label="Main Navigation">
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={() => setActiveTab(link.name)}
                className="relative px-3 py-1.5 text-[14px] font-medium text-[#6b6b6b] hover:text-[#0a0a0a] transition-colors duration-150 focus-ring rounded-full"
              >
                {activeTab === link.name && !shouldReduceMotion && (
                  <motion.div
                    layoutId="nav-indicator"
                    transition={spring}
                    className="absolute inset-0 bg-[#0a0a0a]/[0.05] rounded-full -z-10"
                  />
                )}
                <span>{link.name}</span>
              </a>
            ))}

            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 px-3 py-1.5 text-[14px] font-medium text-[#6b6b6b] hover:text-[#0a0a0a] transition-colors duration-150 focus-ring rounded-full"
            >
              <GithubIcon className="w-3.5 h-3.5 text-[#6b6b6b]" />
              <span>GitHub</span>
            </a>
          </nav>

          {/* Action Button & Mobile Hamburger */}
          <div className="flex items-center gap-2">
            <Magnetic maxDistance={4} radius={50}>
              <Button
                variant="primary"
                size="sm"
                href="#download"
                className="text-xs px-3.5 py-1.5 h-8 font-medium"
              >
                Download
              </Button>
            </Magnetic>

            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden p-1.5 rounded-full text-[#6b6b6b] hover:text-[#0a0a0a] hover:bg-[#fafafa] focus-ring"
              aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            >
              {mobileMenuOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
            </button>
          </div>
        </div>
      </motion.header>

      {/* Mobile Menu Dropdown */}
      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2, ease }}
            className="fixed top-20 inset-x-4 z-40 p-4 rounded-2xl bg-white/95 backdrop-blur-xl border border-[var(--line-strong)] md:hidden flex flex-col gap-3 shadow-xl"
          >
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className="text-[15px] font-medium text-[#0a0a0a] py-2 px-3 hover:bg-[#fafafa] rounded-lg transition-colors"
              >
                {link.name}
              </a>
            ))}
            <a
              href="https://github.com/jaswanthsanjay88/Bit_Android"
              target="_blank"
              rel="noreferrer"
              onClick={() => setMobileMenuOpen(false)}
              className="flex items-center justify-between text-[15px] font-medium text-[#0a0a0a] py-2 px-3 hover:bg-[#fafafa] rounded-lg transition-colors"
            >
              <div className="flex items-center gap-2">
                <GithubIcon className="w-4 h-4 text-[#6b6b6b]" />
                <span>GitHub</span>
              </div>
            </a>
            <div className="pt-2 border-t border-[var(--line)]">
              <Button
                variant="primary"
                size="md"
                href="#download"
                className="w-full justify-center text-sm"
                onClick={() => setMobileMenuOpen(false)}
              >
                Download APK
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Airplane Easter Egg Toast */}
      <AnimatePresence>
        {showToast && (
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 16 }}
            transition={spring}
            className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-full bg-[#0a0a0a] text-white text-xs font-mono flex items-center gap-2 shadow-2xl"
          >
            <Plane className="w-3.5 h-3.5 rotate-45 text-white" />
            <span>Airplane mode: Wi-Fi off &bull; 0B network</span>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};
