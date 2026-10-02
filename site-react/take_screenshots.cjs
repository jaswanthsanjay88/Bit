const puppeteer = require('puppeteer-core');
const fs = require('fs');
const path = require('path');

async function main() {
  const browser = await puppeteer.launch({
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const viewports = [
    { name: 'screen_375.png', width: 375, height: 812, isMobile: true, hasTouch: true },
    { name: 'screen_768.png', width: 768, height: 1024, isMobile: true, hasTouch: true },
    { name: 'screen_1440.png', width: 1440, height: 900, isMobile: false, hasTouch: false }
  ];

  for (const vp of viewports) {
    const page = await browser.newPage();
    await page.setViewport({
      width: vp.width,
      height: vp.height,
      deviceScaleFactor: 2,
      isMobile: vp.isMobile,
      hasTouch: vp.hasTouch
    });

    await page.goto('http://localhost:3000/', { waitUntil: 'networkidle0' });
    // wait for any entry animations to settle
    await new Promise(r => setTimeout(r, 600));

    const metrics = await page.evaluate((expectedWidth) => {
      const docW = document.documentElement.scrollWidth;
      const bodyW = document.body.scrollWidth;
      const winW = window.innerWidth;
      
      const overflowing = [];
      const elements = document.querySelectorAll('*');
      for (const el of elements) {
        const rect = el.getBoundingClientRect();
        if (rect.right > winW + 1) {
          overflowing.push({
            tag: el.tagName,
            id: el.id,
            className: (el.className || '').toString().slice(0, 50),
            rectRight: Math.round(rect.right),
            rectWidth: Math.round(rect.width),
            scrollWidth: el.scrollWidth,
            text: (el.innerText || '').slice(0, 30).replace(/\n/g, ' ')
          });
        }
      }

      return {
        expectedWidth,
        winW,
        docW,
        bodyW,
        hasHorizontalScroll: docW > winW || bodyW > winW,
        overflowCount: overflowing.length,
        overflowing: overflowing.slice(0, 10)
      };
    }, vp.width);

    console.log(`\n=== Viewport ${vp.width}x${vp.height} ===`);
    console.log(JSON.stringify(metrics, null, 2));

    const outPath = path.resolve('..', vp.name);
    await page.screenshot({ path: outPath });
    console.log(`Saved screenshot: ${outPath}`);

    await page.close();
  }

  await browser.close();
}

main().catch(console.error);
