# Roadmap — anotações para as próximas versões

Planejamento das próximas entregas. A **próxima versão** publicada será a
primeira a aparecer na aba [Releases](https://github.com/lhzin0/auroraplay/releases).

## 1. Melhorar classificação e pesquisa por gênero

- Melhorar a classificação por gênero de filmes e séries.
- Tornar a identificação e a organização dos gêneros mais precisas.
- Otimizar a procura por gênero diretamente pela barra de pesquisa.
- Ao pesquisar um gênero, exibir corretamente os filmes e séries relacionados.

## 2. Criar card de Histórico

- Novo card de **Histórico** abaixo do card de **Perfil**.
- Armazena os conteúdos assistidos pelo usuário.
- Permanece salvo até o usuário apagar manualmente — **sem limpeza automática**.
- Incluído nos dados de backup, com restauração posterior.
- Mantém as informações de progresso/tempo assistido associadas aos conteúdos.

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

## 4. GitHub e página de atualização

- Criar o repositório oficial do projeto no GitHub. ✅ (`lhzin0/auroraplay`)
- Preparar o repositório para publicação e gerenciamento das versões.
- Criar/ajustar a verificação de novas versões.
- Usar as releases do GitHub como base do sistema de atualização, quando aplicável.

## 5. Reorganizar o card de atualização do aplicativo

- Remover o card **Atualização do App** da posição atual.
- Mover a funcionalidade para dentro do card **Versão**.
- O card **Versão** permanece no fim da página de Configurações.
- Centralizar nesse card as informações de versão instalada e atualizações disponíveis.
- Evitar cards separados ou informações duplicadas sobre versão/atualização.

## 6. Melhorar a UI de "Editar Perfil"

- Revisar e melhorar toda a interface da tela/seção **Editar Perfil**.
- Melhorar organização visual, espaçamentos, alinhamentos e hierarquia.
- Padronizar botões, campos, ícones e componentes com o restante do app.
- Manter todas as funcionalidades atuais de edição de perfil.
- Priorizar uma interface mais limpa, intuitiva e consistente.

## 7. Reposicionar o card de Backup

- O card **Backup** fica imediatamente abaixo do card **Dados**.
- Mudança apenas de organização da interface; todas as funcionalidades de
  backup são preservadas.
- A nova posição é mantida de forma consistente nos diferentes tamanhos de tela.

## Regras gerais de implementação

- Não remover funcionalidades existentes que não estejam explicitamente
  relacionadas a estas alterações.
- Preservar a compatibilidade com os dados já existentes dos usuários.
- Evitar perda de histórico, progresso ou tempo assistido durante atualizações.
- Garantir que mudanças na UI não alterem indevidamente a lógica existente.
- Manter consistência visual com o restante do aplicativo.
- Testar as alterações em **filmes e séries separadamente**.
- Verificar **backup e restauração** após a inclusão do histórico.
