<div align="center">

<img src="./website/assets/logo.svg" alt="AuroraPlay" width="88" height="88" />

# AuroraPlay

Reprodutor IPTV/Xtream para Android e Android TV

[![Site](https://img.shields.io/badge/site-lhzin0.github.io%2Fauroraplay-8476fa?labelColor=27303D)](https://lhzin0.github.io/auroraplay/)
[![Release](https://img.shields.io/github/v/release/lhzin0/auroraplay?maxAge=3600&label=Est%C3%A1vel&labelColor=06599d&color=043b69&filter=v*)](https://github.com/lhzin0/auroraplay/releases)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-propriet%C3%A1ria-lightgrey?labelColor=27303D)](./LICENSE)

## Download

*Requer Android 7.0 ou superior. Celular, tablet e Android TV.*

Baixe o APK pela página oficial — <https://lhzin0.github.io/auroraplay/> — ou
pela aba [Releases](https://github.com/lhzin0/auroraplay/releases). A partir da
1.34.0 o próprio app verifica e baixa novas versões (você escolhe quando
instalar).

</div>

## Sobre

AuroraPlay organiza e reproduz **as suas próprias** conexões Xtream Codes:
canais ao vivo, filmes e séries, com perfis locais, favoritos, "continuar
assistindo", busca por gênero, backup portátil e atualização pelo próprio app.
A interface se adapta a celular/tablet e a Android TV.

Histórico de versões em [CHANGELOG.md](./CHANGELOG.md). Este repositório
hospeda a página de download e as versões — o código-fonte do aplicativo
não é distribuído.

## Recursos

- Player próprio: play/pause, _seek_, próximo episódio, troca rápida de canais,
  prévia de quadros na linha do tempo, modo cinematográfico e Picture-in-Picture;
  transmissão para dispositivos Cast compatíveis.
- Canais, filmes e séries com categorias do servidor, detalhes, trailer _inline_,
  temporadas/episódios e "programa atual" quando há EPG.
- Busca de filmes e séries com filtros e por gênero — um ou vários ao mesmo
  tempo ("anime, romance"), com os resultados em trilhos por categoria.
- Perfis locais com favoritos e histórico próprios; PIN e biometria em aparelhos
  compatíveis; perfil infantil com filtro pelo catálogo.
- Conexões Xtream múltiplas, teste de acesso, credenciais em
  `EncryptedSharedPreferences` (AES-256) e sincronização periódica em segundo plano.
- Backup para um arquivo escolhido por você, opcionalmente cifrado por senha;
  downloads de filmes e episódios compatíveis para assistir offline.
- Atualização pelo app a partir das Releases do GitHub, com verificação de
  integridade, versão e certificado antes de instalar.

Disponibilidade de EPG, trailers, legendas e Cast depende do conteúdo, do
servidor e do aparelho.

## Aviso

Os desenvolvedores do AuroraPlay não têm afiliação com provedores de conteúdo.
O aplicativo **não hospeda nenhum conteúdo** e não fornece listas, canais,
filmes, séries ou assinaturas: você conecta uma playlist Xtream à qual já tem
acesso e é responsável pela origem e pela legalidade dela.

Falhas de segurança: consulte [SECURITY.md](./SECURITY.md).

## Licença

<pre>
Copyright © 2026 Henrique Luís Pereira. Todos os direitos reservados.

Software proprietário. Nenhuma licença é concedida sobre o aplicativo, seu
código-fonte ou seus materiais sem autorização prévia e por escrito do
titular. O aplicativo distribuído pelos canais oficiais é gratuito para uso
pessoal, sem garantia de qualquer natureza.

Texto completo em ./LICENSE
</pre>
