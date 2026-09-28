import { useEffect } from 'react';
import Lenis from 'lenis';

export function useLenis(enabled = true) {
  useEffect(() => {
    if (!enabled || typeof window === 'undefined') return;

    // Do not run if reduced motion is preferred
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (media.matches) return;

    const lenis = new Lenis({
      lerp: 0.1,
      duration: 1.2,
      smoothWheel: true,
    });

    let rafId: number;
    function raf(time: number) {
      lenis.raf(time);
      rafId = requestAnimationFrame(raf);
    }

    rafId = requestAnimationFrame(raf);

    return () => {
      cancelAnimationFrame(rafId);
      lenis.destroy();
    };
  }, [enabled]);
}
