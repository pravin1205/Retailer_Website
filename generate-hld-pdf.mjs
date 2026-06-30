import puppeteer from 'puppeteer';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const htmlPath = join(__dirname, 'backend', 'docs', 'customer-service-hld.html');
const outPath  = join(__dirname, 'backend', 'docs', 'customer-service-hld-v2.pdf');

console.log('Launching browser...');
const browser = await puppeteer.launch({
  headless: true,
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
});

const page = await browser.newPage();
await page.setViewport({ width: 1500, height: 900 });
await page.goto('file:///' + htmlPath.replace(/\\/g, '/'), { waitUntil: 'networkidle0' });

console.log('Generating PDF...');
await page.pdf({
  path: outPath,
  format: 'A3',
  landscape: true,
  printBackground: true,
  margin: { top: '12mm', bottom: '12mm', left: '10mm', right: '10mm' },
  displayHeaderFooter: true,
  headerTemplate: `<div style="font-size:7pt;color:#94a3b8;width:100%;padding:0 10mm;box-sizing:border-box;display:flex;justify-content:space-between;">
    <span>Marketly — Customer Service High Level Architecture</span><span>Confidential</span></div>`,
  footerTemplate: `<div style="font-size:7pt;color:#94a3b8;width:100%;padding:0 10mm;box-sizing:border-box;display:flex;justify-content:space-between;">
    <span>© 2026 Marketly</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>`,
});

await browser.close();
console.log('✅  PDF saved →', outPath);
