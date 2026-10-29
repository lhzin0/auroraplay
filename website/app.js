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

// Enhance the two public connection options; both remain readable without JS.
const tablist = document.querySelector('.connection-tabs');
if (tablist) {
  const tabs = [...tablist.querySelectorAll('[role="tab"]')];
  function selectTab(tab, focus = false) {
    tabs.forEach(item => {
      const selected = item === tab;
      item.setAttribute('aria-selected', String(selected));
      item.tabIndex = selected ? 0 : -1;
      document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
    });
    const status = document.querySelector('[data-copy-status]');
    if (status) status.textContent = '';
    if (focus) tab.focus();
  }
  tablist.hidden = false;
  selectTab(tabs[0]);
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(tab));
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = tabs.length - 1;
      if (next !== undefined) { event.preventDefault(); selectTab(tabs[next], true); }
    });
  });
}

async function copyPublicText(value, field) {
  try { await navigator.clipboard.writeText(value); return true; }
  catch {
    // Older mobile browsers may deny Clipboard API. Keep manual copy usable.
    const input = field || Object.assign(document.createElement('textarea'), { value });
    if (!field) { input.style.position = 'fixed'; input.style.opacity = '0'; document.body.append(input); }
    input.focus(); input.select();
    if (!field || field.type === 'text') input.setSelectionRange(0, value.length);
    let copied = false;
    try { copied = document.execCommand('copy'); } catch { /* keep selected */ }
    if (!field) input.remove();
    return copied;
  }
}
const copyStatus = document.querySelector('[data-copy-status]');
document.querySelectorAll('[data-copy-target]').forEach(button => {
  button.addEventListener('click', async () => {
    const field = document.getElementById(button.dataset.copyTarget);
    const copied = await copyPublicText(field.value, field);
    copyStatus.textContent = copied ? 'Copiado! Agora cole no aplicativo.' : 'O campo foi selecionado. Toque e segure para copiar.';
  });
});
document.querySelector('[data-copy-connection]')?.addEventListener('click', async () => {
  const value = `URL: ${document.getElementById('xtream-url').value}\nUsuário: ${document.getElementById('xtream-user').value}\nSenha: ${document.getElementById('xtream-password').value}`;
  const copied = await copyPublicText(value);
  copyStatus.textContent = copied ? 'Dados Xtream copiados: URL, usuário e senha.' : 'Use o botão Copiar de cada campo ou selecione os dados para copiar.';
});
document.querySelectorAll('.copy-field input').forEach(input => {
  input.addEventListener('click', () => input.select());
});

document.querySelector('[data-test-xtream]')?.addEventListener('click', async event => {
  const button = event.currentTarget;
  const status = document.querySelector('[data-connection-result]');
  button.disabled = true;
  status.dataset.error = 'false'; status.textContent = 'Verificando o acesso público…';
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const endpoint = new URL('/player_api.php', document.getElementById('xtream-url').value);
    endpoint.searchParams.set('username', document.getElementById('xtream-user').value);
    endpoint.searchParams.set('password', document.getElementById('xtream-password').value);
    const response = await fetch(endpoint, { credentials: 'omit', cache: 'no-store', signal: controller.signal });
    const data = await response.json();
    if (!response.ok || Number(data.user_info?.auth) !== 1 || data.user_info?.status !== 'Active') throw new Error('Unavailable');
    status.textContent = 'Conexão funcionando. Pode usar estes dados no app!';
  } catch {
    status.dataset.error = 'true';
    status.textContent = 'Não foi possível verificar agora. Tente novamente em instantes.';
  } finally { clearTimeout(timeout); button.disabled = false; }
});

document.querySelectorAll('.mobile-menu a').forEach(link => {
  link.addEventListener('click', () => { link.closest('details').open = false; });
});
document.addEventListener('keydown', event => {
  const menu = document.querySelector('.mobile-menu[open]');
  if (event.key === 'Escape' && menu) { menu.open = false; menu.querySelector('summary').focus(); }
});
