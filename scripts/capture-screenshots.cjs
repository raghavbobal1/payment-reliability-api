// Capture the real Spring Boot demo with Playwright. No mocked API responses.
const { chromium } = require('playwright');
const fs = require('node:fs');

(async () => {
  fs.mkdirSync('screenshots', { recursive: true });
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    await page.goto('http://127.0.0.1:8080/', { waitUntil: 'networkidle' });
    await page.waitForFunction(() => document.querySelectorAll('#accounts .account').length === 3);
    await page.screenshot({ path: 'screenshots/01-dashboard.png', fullPage: true });

    await page.locator('#amount').fill('25.00');
    await page.locator('#send').click();
    await page.waitForFunction(() => document.getElementById('output').textContent.startsWith('HTTP 201'));
    await page.screenshot({ path: 'screenshots/02-created-transfer.png', fullPage: true });

    await page.locator('#retry').click();
    await page.waitForFunction(() => document.getElementById('output').textContent.startsWith('HTTP 200'));
    await page.screenshot({ path: 'screenshots/03-idempotent-retry.png', fullPage: true });

    await page.locator('#conflict').click();
    await page.waitForFunction(() => document.getElementById('output').textContent.startsWith('HTTP 409'));
    await page.screenshot({ path: 'screenshots/04-conflicting-retry.png', fullPage: true });
    console.log('Captured four real dashboard states.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
