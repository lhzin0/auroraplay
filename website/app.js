// Fills the version / size / checksum / download link from release.json.
// The values baked in by the build script stay usable if this fails.
async function loadRelease() {
  try {
    const response = await fetch('./release.json', { cache: 'no-cache' });
    if (!response.ok) return;
    const release = await response.json();
    if (!/^\d+\.\d+\.\d+$/.test(release.version) || !Number.isSafeInteger(release.sizeBytes) || release.sizeBytes <= 0) return;
    document.querySelectorAll('[data-version]').forEach(el => { el.textContent = release.version; });
    document.querySelectorAll('[data-size]').forEach(el => {
      el.textContent = `${(release.sizeBytes / 1048576).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
    });
    document.querySelectorAll('[data-sha256]').forEach(el => {
      el.textContent = /^[a-f0-9]{64}$/i.test(release.sha256) ? release.sha256 : 'Não disponível';
    });
    document.querySelectorAll('[data-min-android]').forEach(el => {
      el.textContent = /^\d+(\.\d+)?$/.test(release.minAndroid) ? release.minAndroid : '7.0';
    });
    const download = new URL(release.downloadUrl, window.location.href);
    const sameOrigin = download.origin === location.origin && download.pathname.endsWith('.apk');
    const githubAsset = download.protocol === 'https:' && download.hostname === 'github.com'
      && /^\/[^/]+\/[^/]+\/releases\/download\/.+\.apk$/.test(download.pathname);
    if (sameOrigin || githubAsset) document.querySelectorAll('[data-download]').forEach(el => { el.href = download.href; });
  } catch { /* keep the bundled link */ }
}
loadRelease();

document.querySelector('[data-copy-hash]')?.addEventListener('click', async (event) => {
  const hash = document.querySelector('[data-sha256]')?.textContent.trim();
  if (!/^[a-f0-9]{64}$/i.test(hash || '')) return;
  try { await navigator.clipboard.writeText(hash); event.target.textContent = 'Código copiado'; }
  catch { event.target.textContent = 'Selecione o código acima para copiar'; }
});

// Open a directly linked FAQ answer (e.g. ./ajuda.html#faq-backup).
function revealAnswer() {
  let target;
  try { target = document.getElementById(decodeURIComponent(location.hash.slice(1))); } catch { return; }
  if (target?.tagName === 'DETAILS') target.open = true;
}
addEventListener('hashchange', revealAnswer);
revealAnswer();
