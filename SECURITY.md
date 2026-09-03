# Política de Segurança

## Versões com suporte

Apenas a versão estável mais recente do AuroraPlay recebe correções de
segurança. A versão atual é publicada em
<https://github.com/lhzin0/auroraplay/releases> e anunciada em
<https://lhzin0.github.io/auroraplay/>.

| Versão | Suporte |
|---|---|
| Última estável | ✅ |
| Anteriores | ❌ |

## Como relatar uma vulnerabilidade

**Não abra uma _issue_ pública** para falhas de segurança.

- Preferencial: **GitHub → Security → Report a vulnerability** (GitHub Security
  Advisories), no repositório `lhzin0/auroraplay`.
- Alternativa: e-mail para **henriqueluispereira1@gmail.com** com o assunto
  `[AuroraPlay][security]`.

Inclua, se possível:

- versão do app (`Ajustes → Sobre`) e do Android / modelo do aparelho;
- passos para reproduzir, impacto e uma prova de conceito mínima;
- se a falha envolve rede, o comportamento observado (sem incluir credenciais
  reais ou o link completo da sua playlist).

### Expectativa de resposta

| Etapa | Prazo alvo |
|---|---|
| Confirmação de recebimento | 5 dias úteis |
| Avaliação inicial / severidade | 15 dias |
| Correção ou plano de mitigação | conforme severidade |

Como é um projeto pessoal mantido por uma pessoa, os prazos são metas, não
garantias. Divulgação coordenada: por favor aguarde uma versão corrigida
antes de tornar os detalhes públicos. Não há programa de recompensa.

## Nunca inclua em issues, PRs, logs ou prints

- Senha, login ou URL completa de playlist Xtream com credenciais.
- Arquivos de backup (`*.aurorabackup` ou o JSON de exportação).
- `local.properties`, `TMDB_API_KEY`, `SEED_XTREAM_*`.
- Qualquer material de assinatura: `*.jks`, `*.keystore`, `*.p12`,
  `*.lineage`, `*.password.dpapi`, `identity.json`.

O `.gitignore` já bloqueia esses padrões; ainda assim, confira antes de
commitar.

## Modelo de segurança do aplicativo (resumo)

- Credenciais de playlist são guardadas com `EncryptedSharedPreferences`
  (AES-256) no armazenamento privado do app.
- Backup é um arquivo escolhido pelo usuário (SAF). O `.aurorabackup` é
  cifrado com AES-256-GCM e chave derivada por PBKDF2. A exportação JSON
  sem senha é uma escolha explícita e contém as credenciais em texto legível.
- Auto Backup do Android e transferência entre dispositivos ficam
  **desativados** (`allowBackup="false"`, `data_extraction_rules.xml` exclui
  todos os domínios).
- Atualização in-app: só HTTPS, só os hosts de distribuição do GitHub, com o
  repositório de origem fixado. Antes de instalar, o app confere tamanho,
  SHA-256, `applicationId`, versão e o certificado do APK; o certificado de
  produção é fixado no aplicativo.
- Sem log de HTTP, inclusive em debug. Redirecionamento HTTPS→HTTP é rejeitado
  no cliente da API de conexão.
- Detalhes: [docs/seguranca-e-assinatura.md](docs/seguranca-e-assinatura.md)
  e [docs/atualizacoes-github.md](docs/atualizacoes-github.md).

O hash SHA-256 publicado detecta adulteração do arquivo distribuído; ele não
prova ausência de vulnerabilidades. Esta política não substitui uma auditoria
independente.
