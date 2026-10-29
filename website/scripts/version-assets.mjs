import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, parse } from 'node:path';
import { createHash } from 'node:crypto';

// HTML must name the exact CSS/JS bytes it was built with. Reusing styles.css
// after a redesign can combine new markup with a browser's cached old styles.
export async function versionAssets(output, pages) {
  await mkdir(resolve(output, 'static'), { recursive: true });
  const references = new Map();
  for (const source of ['styles.css', 'app.js']) {
    const bytes = await readFile(resolve(output, source));
    const hash = createHash('sha256').update(bytes).digest('hex').slice(0, 16);
    const { name, ext } = parse(source);
    const versioned = `./static/${name}.${hash}${ext}`;
    await writeFile(resolve(output, versioned), bytes);
    references.set(`./${source}`, versioned);
  }
  for (const page of pages) {
    const path = resolve(output, page);
    let html = await readFile(path, 'utf8');
    for (const [source, target] of references) {
      html = html.replaceAll(`"${source}"`, `"${target}"`);
    }
    await writeFile(path, html);
  }
  return references;
}
