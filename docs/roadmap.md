# Roadmap — anotações para as próximas versões

Planejamento das próximas entregas. A **próxima versão** publicada será a
primeira a aparecer na aba [Releases](https://github.com/lhzin0/auroraplay/releases).

> **Status:** os 7 itens abaixo estão implementados na `main` (commits
> `6596a30`, `b3e10b2`, `816a064`) e aguardam verificação em dispositivo e o
> corte da versão. Ver notas de status ao fim de cada seção.

## 1. Melhorar classificação e pesquisa por gênero

- Melhorar a classificação por gênero de filmes e séries.
- Tornar a identificação e a organização dos gêneros mais precisas.
- Otimizar a procura por gênero diretamente pela barra de pesquisa.
- Ao pesquisar um gênero, exibir corretamente os filmes e séries relacionados.

**Status:** busca por gênero agora casa por palavra inteira
(`MetadataSanitizer.containsWord`), então "ação" não puxa mais "coração";
`GENRE_SYNONYMS` reescrito como grupos bidirecionais (~28 pares PT/EN). A
organização dos trilhos por gênero na Home (`SmartCategoryBuilder`) ainda não
foi revista.

## 2. Criar card de Histórico

- Novo card de **Histórico** abaixo do card de **Perfil**.
- Armazena os conteúdos assistidos pelo usuário.
- Permanece salvo até o usuário apagar manualmente — **sem limpeza automática**.
- Incluído nos dados de backup, com restauração posterior.
- Mantém as informações de progresso/tempo assistido associadas aos conteúdos.

**Status:** card **Histórico** em Ajustes, logo abaixo de **Perfil**, abrindo a
lista por perfil (`WatchHistoryScreen`). Reaproveita a tabela `watch_progress`
(`observeWatchHistory` / `clearWatchHistory`), que já faz parte do snapshot de
backup — restaura junto com o resto, com progresso preservado. Só o botão
**Limpar** apaga, com diálogo de confirmação; não há limpeza automática.

## 3. Remover conteúdo de "Continuar assistindo"

Adicionar a opção **"Remover de Continuar assistindo"**.

**Filmes**
- Remove apenas o filme selecionado da fileira Continuar assistindo.
- Não apaga o histórico.
- Não apaga o tempo/progresso assistido.

**Séries**
- Ao remover, tira da fileira **todos** os episódios daquela série.
- Não apaga o histórico da série ou dos episódios.
- Não apaga o tempo/progresso já assistido.
- Se o usuário voltar ao conteúdo depois, o progresso existente é preservado.

**Status:** novo campo `watch_progress.hiddenFromContinue` (migração aditiva
DB v5→v6, sem perda de dados). "Remover de Continuar assistindo" no menu do
card agora **oculta** em vez de apagar o progresso: filme oculta um card,
série oculta todos os episódios de uma vez; histórico e tempo assistido
permanecem, e retomar o título traz o card de volta.

## 4. GitHub e página de atualização

- Criar o repositório oficial do projeto no GitHub. ✅ (`lhzin0/auroraplay`, público)
- Preparar o repositório para publicação e gerenciamento das versões. ✅ (build no PC + `gh release create`; Pages reconstrói o site)
- Criar/ajustar a verificação de novas versões. ✅ (`update/` já consulta `releases/latest/download/release.json`)
- Usar as releases do GitHub como base do sistema de atualização, quando aplicável. ✅

**Status:** repositório e site no ar; o `update/` já aponta para
`lhzin0/auroraplay`. Falta apenas publicar a primeira release (ver
[.github/RELEASING.md](../.github/RELEASING.md)) para o fluxo ficar ativo
ponta a ponta.

## 5. Reorganizar o card de atualização do aplicativo

- Remover o card **Atualização do App** da posição atual.
- Mover a funcionalidade para dentro do card **Versão**.
- O card **Versão** permanece no fim da página de Configurações.
- Centralizar nesse card as informações de versão instalada e atualizações disponíveis.
- Evitar cards separados ou informações duplicadas sobre versão/atualização.

**Status:** feito. O card autônomo "Atualizações do app" saiu; a UI de
atualização (`AppUpdateSection`) agora vive dentro de um único card
**Versão** no fim das Configurações, que também carrega "Versão instalada".
A linha "Versão" separada foi removida (sem duplicação).

## 6. Melhorar a UI de "Editar Perfil"

- Revisar e melhorar toda a interface da tela/seção **Editar Perfil**.
- Melhorar organização visual, espaçamentos, alinhamentos e hierarquia.
- Padronizar botões, campos, ícones e componentes com o restante do app.
- Manter todas as funcionalidades atuais de edição de perfil.
- Priorizar uma interface mais limpa, intuitiva e consistente.

**Status:** `ProfileEditorScreen` reorganizado em cards arredondados como as
Configurações (Identidade / Perfil infantil / Bloqueio), botão de voltar
circular, coluna centralizada com largura máxima, campos e switches
padronizados (`ToggleRow`), espaçamento uniforme. Todas as funções
(foto/avatar, nome, emoji, cor, PIN, alterar PIN, biometria) preservadas.

## 7. Reposicionar o card de Backup

- O card **Backup** fica imediatamente abaixo do card **Dados**.
- Mudança apenas de organização da interface; todas as funcionalidades de
  backup são preservadas.
- A nova posição é mantida de forma consistente nos diferentes tamanhos de tela.

**Status:** feito. `FileBackupSection` foi movido para logo abaixo da seção
**Dados** na `LazyColumn` das Configurações (mesma ordem em qualquer largura).

## Regras gerais de implementação

- Não remover funcionalidades existentes que não estejam explicitamente
  relacionadas a estas alterações.
- Preservar a compatibilidade com os dados já existentes dos usuários.
- Evitar perda de histórico, progresso ou tempo assistido durante atualizações.
- Garantir que mudanças na UI não alterem indevidamente a lógica existente.
- Manter consistência visual com o restante do aplicativo.
- Testar as alterações em **filmes e séries separadamente**.
- Verificar **backup e restauração** após a inclusão do histórico.
