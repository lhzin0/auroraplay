import { readFile, writeFile, mkdir, cp, rm, stat } from 'node:fs/promises';
import { resolve, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, 'dist');
const parseJson = async path => JSON.parse((await readFile(path, 'utf8')).replace(/^\uFEFF/, ''));
const config = await parseJson(resolve(root, 'site.config.json'));
const repository = process.env.GITHUB_REPOSITORY || config.repository;
if (repository && !/^[\w.-]+\/[\w.-]+$/.test(repository)) throw new Error('Invalid GitHub repository.');
let origin = process.env.SITE_URL || config.siteUrl || (repository ? `https://${repository.split('/')[0]}.github.io/${repository.split('/')[1]}/` : '');
if (origin) {
  const url = new URL(origin);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error('Invalid public site URL.');
  origin = url.href.replace(/\/?$/, '/');
}
let release = await parseJson(resolve(root, 'release.json'));
let apk = null;

// The public site can follow a stable GitHub release without exposing a token in JavaScript.
if (process.env.RELEASE_FROM_GITHUB === 'true' && repository) {
  const headers = { Accept: 'application/vnd.github+json' };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  const response = await fetch(`https://api.github.com/repos/${repository}/releases/latest`, { headers, signal: AbortSignal.timeout(20000) });
  if (response.ok) {
    const latest = await response.json();
    const manifest = latest.assets?.find(asset => asset.name === 'release.json');
    if (!manifest) throw new Error('The stable release must include release.json alongside its APK.');
    const fetchAsset = async asset => {
      const url = new URL(asset.browser_download_url);
      if (url.origin !== 'https://github.com' || !url.pathname.startsWith(`/${repository}/releases/download/`)) throw new Error('Unexpected release asset host.');
      const response = await fetch(url, { signal: AbortSignal.timeout(120000) });
      if (!response.ok) throw new Error(`Unable to download release asset (${response.status}).`);
      return Buffer.from(await response.arrayBuffer());
    };
    const candidate = JSON.parse((await fetchAsset(manifest)).toString('utf8').replace(/^\uFEFF/, ''));
    const asset = latest.assets.find(asset => asset.name === candidate.fileName);
    if (!asset || candidate.versionCode < release.versionCode) throw new Error('Missing APK or release older than the bundled version.');
    apk = await fetchAsset(asset);
    release = { ...candidate, downloadUrl: asset.browser_download_url };
  } else if (response.status !== 404) {
    throw new Error(`GitHub release lookup failed (${response.status}).`);
  }
}

// No GitHub release was used (local build, or none published yet): fall back to
// the APK bundled in website/downloads/.
if (!apk) {
  try {
    apk = await readFile(resolve(root, 'downloads', release.fileName));
  } catch {
    throw new Error(`Missing ${release.fileName}. Publish a GitHub release (see .github/RELEASING.md) or place the APK in website/downloads/.`);
  }
}

if (!/^\d+\.\d+\.\d+$/.test(release.version) || !Number.isSafeInteger(release.versionCode) || release.versionCode <= 0) throw new Error('Invalid version.');
if (basename(release.fileName) !== release.fileName || !/^AuroraPlay-[\d.]+\.apk$/.test(release.fileName)) throw new Error('Invalid APK filename.');
if (!/^\d{4}-\d{2}-\d{2}$/.test(release.publishedAt) || !/^\d+(\.\d+)?$/.test(release.minAndroid)) throw new Error('Invalid release date or Android version.');
if (!Array.isArray(release.notes) || release.notes.some(note => typeof note !== 'string')) throw new Error('Invalid release notes.');
if (apk.length !== release.sizeBytes || createHash('sha256').update(apk).digest('hex') !== release.sha256) throw new Error('APK size/checksum does not match the release manifest.');
if (!release.downloadUrl.startsWith('https://github.com/')) release.downloadUrl = `./downloads/${release.fileName}`;

// Only this generated subdirectory may be cleared; never publish the parent Android project.
if (dirname(output) !== root || basename(output) !== 'dist') throw new Error('Unsafe output path.');
await rm(output, { recursive: true, force: true });
await mkdir(resolve(output, 'downloads'), { recursive: true });
for (const file of ['index.html', 'privacidade.html', '404.html', 'styles.css', 'app.js']) await cp(resolve(root, file), resolve(output, file));
await cp(resolve(root, 'assets'), resolve(output, 'assets'), { recursive: true });
await writeFile(resolve(output, 'downloads', release.fileName), apk);
await writeFile(resolve(output, 'release.json'), JSON.stringify(release, null, 2) + '\n');
await writeFile(resolve(output, '.nojekyll'), '');
const escapeHtml = value => String(value).replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));
for (const file of ['index.html', 'privacidade.html']) {
  let html = await readFile(resolve(output, file), 'utf8');
  if (file === 'index.html') {
    html = html.replace(/data-version>[^<]+</g, `data-version>${release.version}<`)
      .replace(/href="\.\/downloads\/[^"]+"/g, `href="${escapeHtml(release.downloadUrl)}"`)
      .replace(/data-size>[^<]+</g, `data-size>${(release.sizeBytes / 1048576).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB<`);
    html = html.replace(/(<code data-sha256>)[^<]*(<\/code>)/, `$1${release.sha256}$2`);
    html = html.replace(/(<ul data-release-notes>)[\s\S]*?(<\/ul>)/, `$1${release.notes.map(note => `<li>${escapeHtml(note)}</li>`).join('')}$2`);
    if (repository) html = html.replace('data-repository-issues href="#ajuda" hidden', `data-repository-issues href="https://github.com/${repository}/issues/new" target="_blank" rel="noopener noreferrer"`);
  }
  if (origin) {
    const canonical = new URL(file === 'index.html' ? './' : file, origin).href;
    const image = new URL('assets/og.png', origin).href;
    html = html.replace('</head>', `<link rel="canonical" href="${escapeHtml(canonical)}"><meta property="og:url" content="${escapeHtml(canonical)}"><meta property="og:image" content="${escapeHtml(image)}"><meta property="og:image:alt" content="AuroraPlay — Sua playlist. No seu ritmo."><meta property="og:image:width" content="1536"><meta property="og:image:height" content="1024"><meta name="twitter:image" content="${escapeHtml(image)}"></head>`);
  }
  await writeFile(resolve(output, file), html);
}
if (origin) {
  const notFound = (await readFile(resolve(output, '404.html'), 'utf8')).replace('href="./" id="home-link"', `href="${escapeHtml(origin)}" id="home-link"`);
  await writeFile(resolve(output, '404.html'), notFound);
  await writeFile(resolve(output, 'robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${origin}sitemap.xml\n`);
  await writeFile(resolve(output, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${escapeHtml(origin)}</loc></url><url><loc>${escapeHtml(new URL('privacidade.html', origin).href)}</loc></url></urlset>`);
}

// Verify local links, IDs and downloadable files in the exact public output.
for (const name of ['index.html', 'privacidade.html']) {
  const html = await readFile(resolve(output, name), 'utf8');
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
  if (new Set(ids).size !== ids.length) throw new Error(`Duplicate IDs in ${name}.`);
  for (const match of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
    const value = match[1];
    if (/^(https?:|mailto:|data:)/.test(value)) continue;
    const [path, anchor] = value.split('#');
    const target = resolve(output, !path ? name : path === './' ? 'index.html' : path);
    const file = (await stat(target)).isDirectory() ? resolve(target, 'index.html') : target;
    if (anchor && !(await readFile(file, 'utf8')).includes(`id="${anchor}"`)) throw new Error(`Broken anchor ${value} in ${name}.`);
  }
}
console.log(`Built AuroraPlay site for ${release.version}; APK checksum and local links verified.`);
console.log(origin ? `Public URL: ${origin}` : 'Publication is waiting for a GitHub account/repository.');
