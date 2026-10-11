// Картинки упражнений для TexasProgram/Resources/Exercises.
//   node render.js sheet sheet.png          — контактный лист для просмотра
//   SCALE=2.4 node render.js export out     — по PNG 240×240 на позу
// Нужен Playwright с Chromium (npm i playwright).
let chromium;
try { ({ chromium } = require('playwright')); } catch { ({ chromium } = require('/opt/node-tools/node_modules/playwright')); }
const { svg } = require('./figure');
const poses = require('./poses');
const fs = require('fs');

(async () => {
  const [mode, out] = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: Number(process.env.SCALE || 1.8) });
  const names = Object.keys(poses);
  if (mode === 'sheet') {
    const cells = names.map(n => `<figure><div class="t">${svg(poses[n]())}</div><figcaption>${n}</figcaption></figure>`).join('');
    await page.setContent(`<html><body style="margin:0;background:#000;font:11px sans-serif;color:#aaa">
      <style>figure{display:inline-block;margin:8px;text-align:center} .t{width:150px;height:150px;border-radius:32px;overflow:hidden} .t svg{width:150px;height:150px}</style>
      ${cells}</body></html>`);
    await page.screenshot({ path: out, fullPage: true });
  } else {
    fs.mkdirSync(out, { recursive: true });
    for (const n of names) {
      await page.setContent(`<html><body style="margin:0">${svg(poses[n]()).replace('width="100" height="100"', 'width="100" height="100" style="display:block"')}</body></html>`);
      await page.locator('svg').screenshot({ path: `${out}/ex_${n}.png` });
    }
  }
  await browser.close();
})();
