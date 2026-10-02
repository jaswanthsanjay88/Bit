const puppeteer = require('puppeteer-core');

async function triggerAllScrollAnimations(page) {
  const sections = await page.$$('section, footer');
  for (const sec of sections) {
    await sec.scrollIntoView();
    await new Promise((r) => setTimeout(r, 120));
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await new Promise((r) => setTimeout(r, 400));
}

async function verifyViewport(page, width, height, label) {
  await page.setViewport({ width, height, deviceScaleFactor: 1 });
  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle0' });
  await triggerAllScrollAnimations(page);
  
  await page.screenshot({ path: `E:/BIT/screen_${label}_full.png`, fullPage: true });

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth
  );
  
  const alignments = await page.evaluate(() => {
    const list = [
      { name: 'Stats', selector: 'section.border-y .container' },
      { name: 'Agents', selector: '#features .container' },
      { name: 'Cloud', selector: '#comparison .container' },
      { name: 'HowItWorks', selector: '#how-it-works .container' },
      { name: 'FinalCTA', selector: '#download .container' },
      { name: 'FounderNarrow', selector: '#founder .container--narrow' },
      { name: 'FAQNarrow', selector: '#faq .container--narrow' },
    ];
    return list.map(item => {
      const el = document.querySelector(item.selector);
      if (!el) return { name: item.name, found: false };
      const rect = el.getBoundingClientRect();
      return {
        name: item.name,
        found: true,
        left: Math.round(rect.left),
        width: Math.round(rect.width),
      };
    });
  });

  console.log(`=== Viewport: ${label} (${width}x${height}) ===`);
  console.log(`Horizontal Overflow: ${overflow}`);
  console.log('Alignments:', alignments);
}

async function run() {
  const browser = await puppeteer.launch({
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    headless: 'new',
    args: ['--no-sandbox'],
  });

  const page = await browser.newPage();

  // 1. 1440 Viewport
  await verifyViewport(page, 1440, 900, '1440');

  // Detail section captures at 1440
  const heroSec = await page.$('section:first-of-type');
  if (heroSec) await heroSec.screenshot({ path: 'E:/BIT/screen_hero.png' });

  const statsSec = await page.$('section.border-y');
  if (statsSec) await statsSec.screenshot({ path: 'E:/BIT/screen_stats.png' });

  const featSec = await page.$('#features');
  if (featSec) await featSec.screenshot({ path: 'E:/BIT/screen_features.png' });

  const compareSec = await page.$('#comparison');
  if (compareSec) await compareSec.screenshot({ path: 'E:/BIT/screen_section_c.png' });

  const howSec = await page.$('#how-it-works');
  if (howSec) await howSec.screenshot({ path: 'E:/BIT/screen_section_d.png' });

  const founderSec = await page.$('#founder');
  if (founderSec) await founderSec.screenshot({ path: 'E:/BIT/screen_founder_note.png' });

  const faqSec = await page.$('#faq');
  if (faqSec) await faqSec.screenshot({ path: 'E:/BIT/screen_faq.png' });

  const ctaSec = await page.$('#download');
  if (ctaSec) await ctaSec.screenshot({ path: 'E:/BIT/screen_final_cta.png' });

  // 2. 1024 Viewport
  await verifyViewport(page, 1024, 768, '1024');

  // 3. 390 Viewport (Modern iPhone / mobile)
  await verifyViewport(page, 390, 844, '390');

  await browser.close();
}

run();
