import { useEffect } from 'react';
import { Hero } from './components/Hero';
import { TopBar } from './components/TopBar';
import { reduced } from './lib/anim';
import { Closing } from './sections/Closing';
import { Faq } from './sections/Faq';
import { AppStore } from './sections/AppStore';
import { GetSetup } from './sections/GetSetup';
import { HowItWorks } from './sections/HowItWorks';
import { More } from './sections/More';
import { TrySaying } from './sections/TrySaying';
import { Tour } from './tour/Tour';

/** Cards (.rv) and headings (.words) rise in once, as they scroll into view. */
function useReveal() {
  useEffect(() => {
    const els = document.querySelectorAll('.rv,.words');
    if (!('IntersectionObserver' in window) || reduced()) { els.forEach((el) => el.classList.add('in')); return; }
    const io = new IntersectionObserver((es) => es.forEach((e) => {
      if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
    }), { threshold: 0.15, rootMargin: '0px 0px -8% 0px' });
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);
}

export function App() {
  useReveal();
  return (
    <>
      <TopBar />
      <Hero />
      <Tour />
      <GetSetup />
      <AppStore />
      <More />
      <HowItWorks />
      <TrySaying />
      <Faq />
      <Closing />
    </>
  );
}
