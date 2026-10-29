import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { versionAssets } from './version-assets.mjs';

test('new pages bypass stale CSS and JS while preserving page-relative links', async t => {
  const root = await mkdtemp(join(tmpdir(), 'aurora-assets-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const pages = ['index.html', 'ajuda.html', 'privacidade.html', 'termos.html', '404.html'];
  const markup = '<link rel="stylesheet" href="./styles.css"><script src="./app.js" defer></script><a href="./demo/aurora-demo.m3u">M3U</a>';
  const styles = '.home{display:grid}';
  const script = 'fetch("./release.json")';
  await writeFile(join(root, 'styles.css'), styles);
  await writeFile(join(root, 'app.js'), script);
  for (const page of pages) await writeFile(join(root, page), markup);
  const first = await versionAssets(root, pages);

  // Simulate an old browser cache/legacy URL serving different bytes.
  await writeFile(join(root, 'styles.css'), '.old{color:red}');
  await writeFile(join(root, 'app.js'), '/* old script */');
  for (const page of pages) {
    const html = await readFile(join(root, page), 'utf8');
    assert.ok(html.includes(first.get('./styles.css')));
    assert.ok(html.includes(first.get('./app.js')));
    assert.ok(!html.includes('"./styles.css"'));
    assert.ok(!html.includes('"./app.js"'));
    assert.ok(html.includes('href="./demo/aurora-demo.m3u"'));
    for (const reference of first.values()) {
      const prefix = 'https://example.test/auroraplay/';
      assert.ok(new URL(reference, prefix).href.startsWith(prefix + 'static/'));
    }
  }
  assert.equal(await readFile(join(root, first.get('./styles.css')), 'utf8'), styles);
  assert.equal(await readFile(join(root, first.get('./app.js')), 'utf8'), script);

  // Only files whose bytes changed get new URLs; identical builds stay stable.
  await writeFile(join(root, 'styles.css'), styles);
  await writeFile(join(root, 'app.js'), script);
  for (const page of pages) await writeFile(join(root, page), markup);
  assert.deepEqual(await versionAssets(root, pages), first);
  await writeFile(join(root, 'styles.css'), styles + '\n.new{gap:1rem}');
  const cssChanged = await versionAssets(root, []);
  assert.notEqual(cssChanged.get('./styles.css'), first.get('./styles.css'));
  assert.equal(cssChanged.get('./app.js'), first.get('./app.js'));
  await writeFile(join(root, 'app.js'), script + ';/* new */');
  const jsChanged = await versionAssets(root, []);
  assert.notEqual(jsChanged.get('./app.js'), first.get('./app.js'));
});
