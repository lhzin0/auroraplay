const menuButton = document.querySelector('.menu-button');
const navigation = document.querySelector('#navigation');
menuButton?.addEventListener('click', () => {
  const open = menuButton.getAttribute('aria-expanded') !== 'true';
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
  navigation?.classList.toggle('open', open);
});
navigation?.addEventListener('click', (event) => {
  if (event.target.closest('a')) { navigation.classList.remove('open'); menuButton?.setAttribute('aria-expanded', 'false'); menuButton?.setAttribute('aria-label', 'Abrir menu'); }
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && menuButton?.getAttribute('aria-expanded') === 'true') { menuButton.click(); menuButton.focus(); }
});
const previews = {
  inicio: ['SUA BIBLIOTECA', 'Continue de onde parou', 'O próximo capítulo é seu.', 'Seus favoritos. No seu perfil.', 'Tudo no seu lugar', 'Canais ao vivo', 'Seus filmes', 'Suas séries'],
  'ao-vivo': ['AO VIVO', 'Acompanhe a programação', 'Seu canal. Seu momento.', 'Guia disponível conforme a playlist.', 'Escolha o que assistir', 'Categorias', 'Prévia do canal', 'Guia EPG'],
  series: ['SUAS SÉRIES', 'Uma temporada de cada vez', 'Só mais um episódio.', 'Retome de onde parou no seu perfil.', 'Sua próxima maratona', 'Temporadas', 'Favoritos', 'Próximo episódio']
};
document.querySelectorAll('[data-preview]').forEach(button => button.addEventListener('click', () => {
  document.querySelectorAll('[data-preview]').forEach(item => { item.classList.toggle('selected', item === button); item.setAttribute('aria-pressed', String(item === button)); });
  ['demo-tag','demo-kicker','demo-title','demo-note','demo-rail-title','demo-card-a','demo-card-b','demo-card-c'].forEach((id, index) => { document.getElementById(id).textContent = previews[button.dataset.preview][index]; });
}));
async function loadRelease() {
  try {
    const response = await fetch('./release.json', { cache: 'no-cache' });
    if (!response.ok) return;
    const release = await response.json();
    if (!/^\d+\.\d+\.\d+$/.test(release.version) || !Number.isSafeInteger(release.sizeBytes) || release.sizeBytes <= 0) return;
    document.querySelectorAll('[data-version]').forEach(item => item.textContent = release.version);
    document.querySelectorAll('[data-size]').forEach(item => item.textContent = `${(release.sizeBytes / 1048576).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`);
    document.querySelectorAll('[data-sha256]').forEach(item => item.textContent = /^[a-f0-9]{64}$/i.test(release.sha256) ? release.sha256 : 'Não disponível');
    document.querySelectorAll('[data-min-android]').forEach(item => item.textContent = /^\d+(\.\d+)?$/.test(release.minAndroid) ? release.minAndroid : '7.0');
    if (/^\d{4}-\d{2}-\d{2}$/.test(release.publishedAt)) document.querySelectorAll('[data-release-date]').forEach(item => {
      item.dateTime = release.publishedAt;
      item.textContent = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${release.publishedAt}T12:00:00Z`));
    });
    if (Array.isArray(release.notes) && release.notes.length) document.querySelectorAll('[data-release-notes]').forEach(item => {
      item.replaceChildren(...release.notes.filter(note => typeof note === 'string').slice(0, 12).map(note => { const li = document.createElement('li'); li.textContent = note; return li; }));
    });
    const download = new URL(release.downloadUrl, window.location.href);
    const sameOrigin = download.origin === location.origin && download.pathname.endsWith('.apk');
    const githubAsset = download.protocol === 'https:' && download.hostname === 'github.com' && /^\/[^/]+\/[^/]+\/releases\/download\/.+\.apk$/.test(download.pathname);
    if (sameOrigin || githubAsset) document.querySelectorAll('[data-download]').forEach(item => item.href = download.href);
  } catch { /* The bundled link remains usable offline or during a metadata failure. */ }
}
loadRelease();

document.querySelector('[data-copy-hash]')?.addEventListener('click', async (event) => {
  const hash = document.querySelector('[data-sha256]')?.textContent.trim();
  if (!/^[a-f0-9]{64}$/i.test(hash || '')) return;
  try { await navigator.clipboard.writeText(hash); event.target.textContent = 'Código copiado'; }
  catch { event.target.textContent = 'Selecione o código acima para copiar'; }
});

// Open a directly linked answer, including links arriving from another page.
function revealAnswer() {
  let target;
  try { target = document.getElementById(decodeURIComponent(location.hash.slice(1))); } catch { return; }
  if (target?.tagName === 'DETAILS') target.open = true;
}
addEventListener('hashchange', revealAnswer);
revealAnswer();
