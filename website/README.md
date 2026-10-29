# AuroraPlay — site de distribuição

Site estático em português para Firebase Hosting e GitHub Pages: apresentação,
recursos, download do APK, instalação, novidades, perguntas frequentes, termos
de serviço e transparência sobre dados.
Sem dependências npm, banco de dados, login de visitantes ou serviços pagos.
Requer Node.js 20+ para os scripts de build.

Faz parte do monorepo do AuroraPlay. A automação (build do site, deploy no
Firebase Hosting, Pages e releases do APK) vive em `.github/workflows/` na **raiz** do repositório,
não aqui.

## Prévia local

```powershell
npm run dev
```

Abra `http://127.0.0.1:4173`. O download usa o APK em `downloads/`. A área de demonstração apresenta a lista M3U e as credenciais públicas de teste do serviço Xtream. O painel visual do topo é uma ilustração do catálogo.

## Build local

```powershell
npm run build      # gera dist/ e valida links, âncoras, IDs e o SHA-256 do APK
npm run preview    # serve dist/
```

Para gerar `dist/` já com canonical, Open Graph, `sitemap.xml`, `robots.txt` e
o endereço de retorno da 404, preencha `site.config.json` com `repository`
(`usuario/repositorio`) e `siteUrl`, ou exporte `GITHUB_REPOSITORY` / `SITE_URL`.
Nunca coloque tokens ou senhas em `site.config.json`.

## Publicação (GitHub Actions, na raiz do repo)

`.github/workflows/firebase-hosting-release.yml` publica em
`https://auroraplay.app.br/` e no endereço Firebase
`https://auroraplay-507422.web.app/` quando uma release estável é publicada,
quando o site muda na `main` ou por disparo manual. Os dois endereços usam o
mesmo site do Firebase Hosting. O workflow usa as variáveis de repositório
`GCP_WORKLOAD_IDENTITY_PROVIDER` e `GCP_SERVICE_ACCOUNT` para autenticação.

`.github/workflows/pages.yml` também publica no GitHub Pages. Ele roda quando:

- há `push` na `main` que toca em `website/**` (ou no próprio `pages.yml`);
- uma GitHub Release é publicada (`release.yml` cria a release do APK e o
  Pages reconstrói apontando para ela);
- disparo manual em **Actions → Pages → Run workflow**.

Pré-requisito único: **Settings → Pages → Source = GitHub Actions**.

No servidor o build roda com `RELEASE_FROM_GITHUB=true`: se existir uma GitHub
Release estável com `release.json` + APK, o site segue essa release; senão, usa
o APK e o `release.json` desta pasta. Um `release.json` de release sem manifesto
ou com hash divergente interrompe o build e preserva a última publicação.

## APK versionado x releases

O fluxo oficial é `release.yml` compilar e assinar o APK e criar a GitHub
Release. `downloads/*.apk` está no `.gitignore`, com exceção do APK da versão
atual (`AuroraPlay-1.34.0.apk`), mantido como semente até a primeira release
por CI. Depois disso o APK pode sair do controle de versão sem mudar código
(o build já usa o da release). Detalhes em `../.github/RELEASING.md`.

## Manutenção

- `index.html` — apresentação, recursos e ajuda.
- `privacidade.html` — funcionamento dos dados; acompanhe mudanças do app.
- `termos.html` — termos de uso do aplicativo e do site.
- `styles.css` — identidade visual, responsividade, redução de movimento.
- `app.js` — menu, prévia ilustrativa, metadados da release e cópia do checksum.
- `release.json` — manifesto público do APK (semente; o CI gera o oficial).
- `scripts/build.mjs` — publicação com lista explícita de arquivos e validação.
- `scripts/serve.mjs` — servidor de prévia (porta 4173).
- `assets/og.png` — arte de compartilhamento e identidade da página.

O site não envia backups nem credenciais e não tem formulário de autenticação.
Ao mudar o funcionamento do app, revise os textos e a página de privacidade.

Referências:
[GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
·
[GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
# Site e catálogo público de testes

A página inicial é responsiva, com navegação para celular e acesso ao catálogo em `#testar`.

- M3U: copiar a URL pública, baixar `demo/aurora-demo.m3u` e copiar o guia XMLTV.
- Xtream: servidor `https://auroraplay-xtream-demo.lhz1n.chatgpt.site`, usuário `aurora`, senha pública `teste`.
- O botão de verificação faz uma consulta ao servidor Xtream somente quando acionado, sem cookies.
- Os dois acessos usam o mesmo catálogo público. O serviço Xtream mantém uma cópia de segurança do catálogo se a consulta ao GitHub estiver indisponível.
- JavaScript habilita abas com navegação por teclado e cópia com alternativa para navegadores antigos. Sem JavaScript, os dois painéis continuam visíveis e os endereços podem ser selecionados.

Preserve os atributos `data-version`, `data-size`, `data-min-android`, `data-sha256` e `data-download`: o build e `app.js` usam o manifesto da versão para preencher os dados de download. A demonstração não altera esse fluxo de publicação do APK.

