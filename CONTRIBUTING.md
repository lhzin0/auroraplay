# Contribuindo com o AuroraPlay

Obrigado pelo interesse. O AuroraPlay é um projeto **proprietário** (veja
[LICENSE](LICENSE)); o acesso ao repositório e a aceitação de contribuições
ficam a critério do mantenedor. Este guia descreve como propor mudanças de
forma que possam ser revisadas e integradas rapidamente.

## Antes de começar

- Abra uma _issue_ descrevendo o problema ou a proposta antes de investir em
  uma mudança grande. Correções pequenas e óbvias podem ir direto para um PR.
- Não envie credenciais, listas Xtream, backups de usuário, `local.properties`,
  chaves de assinatura ou qualquer segredo — nem em código, testes, prints ou
  descrições de PR. Veja [SECURITY.md](SECURITY.md).
- Ao enviar uma contribuição você concorda com a cláusula 3 da
  [LICENSE](LICENSE) (licença não exclusiva ao titular para uso no projeto).

## Ambiente

| Item | Versão |
|---|---|
| Android Studio | Koala ou mais recente |
| JDK para o Gradle **pela linha de comando** | **21** (ex.: JetBrains Runtime 21) |
| Android SDK | `compileSdk 36`, `build-tools;36.0.0` |
| minSdk / targetSdk | 24 / 36 |

O build pela linha de comando **exige JDK 21**. O JDK 25 que acompanha versões
recentes do Android Studio quebra o Gradle 8.14.x. Aponte `JAVA_HOME` (ou
`org.gradle.java.home`) para um JDK 21 antes de rodar `./gradlew`.

O `org.gradle.configuration-cache` fica **desligado de propósito** — não o
ligue (veja o comentário em `gradle.properties`).

### Configuração local (`local.properties`)

O arquivo não é versionado. Chaves usadas pelo build (todas opcionais):

```properties
sdk.dir=<caminho do Android SDK>
TMDB_API_KEY=<sua chave TMDB, para metadados e trailers>
# Somente para o build de debug (pré-carrega uma playlist de teste):
SEED_XTREAM_NAME=
SEED_XTREAM_URL=
SEED_XTREAM_USER=
SEED_XTREAM_PASS=
```

Sem `TMDB_API_KEY` o app compila e roda; recursos de metadados/trailer ficam
limitados. O build de **release** nunca embute credenciais.

## Fluxo de trabalho

1. Crie um branch a partir de `main`: `feat/<resumo>`, `fix/<resumo>`,
   `docs/<resumo>`, `chore/<resumo>` ou `refactor/<resumo>`.
2. Faça commits pequenos e coerentes.
3. Rode as verificações locais (abaixo).
4. Abra o PR contra `main` preenchendo o template.

### Commits — Conventional Commits

O histórico segue [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(player): botão de modo cinema persistente
fix(sync): não apagar catálogo quando o fetch falha
docs(readme): reestrutura para o layout público
```

Escopos comuns: `player`, `sync`, `backup`, `update`, `connections`, `home`,
`search`, `settings`, `player`, `ci`, `website`, `docs`.

### Versionamento

`versionName`/`versionCode` vivem **apenas** em `app/build.gradle.kts`.

| Mudança | Incremento |
|---|---|
| Correção de bug | `x.x.PATCH` |
| Funcionalidade / redesign | `x.MINOR.x` |
| Quebra de compatibilidade | `MAJOR.x.x` |

`versionCode` sempre aumenta. Registre a mudança no topo de
[CHANGELOG.md](CHANGELOG.md), numa entrada `## X.Y.Z — data` com bullets.

## Verificações antes do PR

```bash
./gradlew testDebugUnitTest lint assembleDebug
```

Com um emulador/dispositivo conectado, quando a mudança tocar em código com
testes instrumentados:

```bash
./gradlew connectedDebugAndroidTest
```

Se mexeu no site:

```bash
node website/scripts/build.mjs
```

Rode isso localmente antes do PR — o CI (`.github/workflows/ci.yml`) só valida o
site; o app é compilado no PC.

## Estilo de código

- Kotlin oficial (`kotlin.code.style=official`); 4 espaços; máx. ~100 colunas.
- Siga os padrões já presentes no arquivo que você está editando: nomes,
  densidade de comentários, organização por _feature_.
- Compose: `@Composable` sem estado de negócio; a lógica vai para o
  `ViewModel`. Camadas: `presentation → domain ← data`, `player` isolado.
- Sem logs de HTTP, mesmo em debug. Sem URLs de mídia em logs.

## Revisão

PRs são revisados pelo mantenedor. Pode ser pedido: dividir o PR, ajustar
escopo, adicionar testes ou reverter mudanças fora do tema. `main` é a única
branch de integração e deve permanecer sempre compilável.
