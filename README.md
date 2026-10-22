# Música — player de música offline para Android

Aplicativo para tocar as músicas salvas no celular, sem internet, inspirado no player que vem de fábrica nos celulares Xiaomi.
Feito em **Kotlin** com **Jetpack Compose** (interface) e **Media3/ExoPlayer** (reprodução).

> Status: protótipo (versão 0.2). Funciona de ponta a ponta, mas ainda não tem fila de reprodução visível, equalizador, letras nem timer.

---

## Sumário

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Requisitos](#requisitos)
- [Como compilar e gerar o APK](#como-compilar-e-gerar-o-apk)
- [Como instalar no celular](#como-instalar-no-celular)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Arquitetura](#arquitetura)
- [Detalhes de cada parte](#detalhes-de-cada-parte)
- [Permissões](#permissões)
- [Onde os dados ficam salvos](#onde-os-dados-ficam-salvos)
- [Testando no emulador](#testando-no-emulador)
- [Problemas conhecidos e soluções](#problemas-conhecidos-e-soluções)
- [Próximos passos](#próximos-passos)

---

## Funcionalidades

**Biblioteca**
- Lê automaticamente todas as músicas do aparelho (ignora áudios com menos de 10 s, como toques e notificações).
- Abas: **Músicas**, **Artistas**, **Álbuns** (grade com capas), **Pastas** e **Playlists**.
- Troca de aba tocando nas "pílulas" ou **deslizando o dedo** para os lados.
- **Barra de pesquisa** sempre visível: busca por título, artista ou álbum enquanto digita.
- Telas de detalhe para álbum, artista, pasta e playlist, com botões **Tocar** e **Aleatório**.

**Player**
- **Mini player** fixo na parte de baixo, com barra de progresso.
- Tela **Tocando agora**: capa grande, fundo com a cor extraída da capa, barra de posição, anterior/próxima, aleatório, repetir (todas / uma) e favoritar.
- Toca **em segundo plano**, com controles na **notificação**, na **tela de bloqueio** e em fones Bluetooth.
- Pausa sozinho quando o fone é desconectado ou quando chega uma ligação.

**Playlists**
- Criar, excluir, adicionar e remover músicas.
- Playlist especial **Favoritas** (coração na tela do player).
- Menu ⋮ de cada música: **Tocar a seguir**, **Adicionar à playlist**, **Remover da playlist**.

**Seleção múltipla**
- **Segurar** uma música entra no modo de seleção; tocar nas outras marca/desmarca.
- Barra superior: sair, contador e **Selecionar tudo**.
- Barra inferior: **Tocar**, **A seguir**, **Playlist**, **Remover** (dentro de playlist/Favoritas) e **Apagar**.
- **Apagar** remove os arquivos do celular (no Android 11+ o sistema pede confirmação) e limpa a música das playlists, favoritas e da fila.

**Procurar online (YouTube)**
- Botão **Online** no cabeçalho abre a busca no YouTube (por texto ou colando um link).
- Resultados com miniatura, duração e canal.
- **Baixar** salva só o áudio, **sem reconverter** (normalmente M4A/AAC, o formato que o próprio YouTube fornece), com título, artista e **capa quadrada** embutidos, em `Music/Música Online/`. A música aparece sozinha na biblioteca.
- Até **2 downloads ao mesmo tempo**; os demais ficam na fila. Dá para cancelar.
- Tela **Downloads** (ícone ⬇ com contador na tela Online, ou tocando na notificação): progresso de cada download com tempo restante, posição na fila, concluídos (com ▶ para tocar) e erros (com botão de tentar de novo).
- A busca por texto consulta o YouTube **direto** (~1 s); links e falhas da busca direta usam o yt-dlp. Continuam com o app fechado, com progresso na notificação.
- Usa o **yt-dlp** de verdade (via youtubedl-android), que se **atualiza sozinho** a cada 24 h (ou pelo menu ⋮ → *Atualizar yt-dlp*).
- Baixe apenas conteúdo que você tem direito de usar (seu, Creative Commons ou domínio público). Os termos do YouTube proíbem baixar fora dos recursos oficiais.

**Áudios de mensagens ficam de fora**
- Mensagens de voz e áudios do WhatsApp e do WhatsApp Business não aparecem na biblioteca.

**Continuar de onde parou**
- O app lembra a fila, a música, a posição, o aleatório e o repetir.
- Ao reabrir (mesmo depois de o sistema encerrar o app), a última música volta **pausada** na mesma posição.

**Aparência**
- Tema **Claro**, **Escuro** ou **Padrão do sistema** (botão no canto superior direito). A escolha fica salva.
- Cor de destaque laranja `#FF5A1F`.

---

## Tecnologias

| Parte | Tecnologia | Versão |
|---|---|---|
| Linguagem | Kotlin | 2.0.21 |
| Interface | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Ícones | Material Icons Extended | (via BOM) |
| Reprodução | AndroidX Media3 (ExoPlayer + MediaSession) | 1.5.1 |
| Cor da capa | AndroidX Palette | 1.0.0 |
| Busca e download online | youtubedl-android (yt-dlp + Python + ffmpeg + QuickJS) | 0.18.1 |
| Miniaturas online | Coil | 2.7.0 |
| Estado | ViewModel (Lifecycle) | 2.8.7 |
| Build | Gradle / Android Gradle Plugin | 8.11.1 / 8.7.3 |
| Java | JDK | 17+ (testado com 21) |

Configuração Android: `minSdk 26` (Android 8.0), `targetSdk 35` / `compileSdk 35` (Android 15).

A biblioteca, o player e as playlists funcionam 100% offline. Só a tela **Online** usa internet.

---

## Requisitos

- **JDK 17 ou superior** (`java -version`).
- **Android SDK** com:
  - `platforms;android-35`
  - `build-tools;35.0.0`
  - `platform-tools` (para o `adb`)
- O caminho do SDK fica em `local.properties` (este arquivo não vai para o git):

  ```properties
  sdk.dir=/home/lucas/Android/Sdk
  ```

Não é preciso instalar o Gradle: o projeto usa o **Gradle Wrapper** (`./gradlew`), que baixa a versão certa sozinho.

### Instalando o SDK sem o Android Studio (Linux)

```bash
mkdir -p ~/Android/Sdk/cmdline-tools && cd /tmp
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip commandlinetools-linux-13114758_latest.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest

export ANDROID_HOME=~/Android/Sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Com o **Android Studio** também funciona: basta abrir a pasta `MusicPlayer`.

---

## Como compilar e gerar o APK

Todos os comandos do Gradle rodam **dentro da pasta `MusicPlayer/`**.

```bash
cd ~/Downloads/projects/MusicPlayer

# APKs de release — um por tipo de processador
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-arm64-v8a-release.apk    (~54 MB, celulares atuais)
# -> app/build/outputs/apk/release/app-armeabi-v7a-release.apk  (~48 MB, celulares antigos 32 bits)
# -> app/build/outputs/apk/release/app-x86_64-release.apk       (~57 MB, emulador)

# APK de debug (maior, permite depuração com logcat/run-as)
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

A primeira compilação demora alguns minutos (baixa dependências). As seguintes levam segundos.

**Importante:** o Gradle continua rodando em segundo plano depois de compilar (usa ~3 GB de RAM e pode deixar o notebook barulhento). Para encerrar:

```bash
./gradlew --stop
```

### Sobre a assinatura

O build de release está assinado com a **chave de debug** (`~/.android/debug.keystore`), configurada em `app/build.gradle.kts`.
Isso basta para instalar e testar, mas **não serve para publicar na Play Store**. Para publicar, crie uma chave própria (`keytool -genkey ...`) e configure um `signingConfig` de release.

O release usa **R8** (`isMinifyEnabled = true` e `isShrinkResources = true`) para remover código não usado. O código do app em si ocupa ~2 MB; o resto do APK é o Python e o ffmpeg do yt-dlp (bibliotecas nativas). Por isso o projeto gera **um APK por arquitetura** (`splits.abi`), em vez de um único APK de ~190 MB.

Na raiz do repositório ficam prontos:
- `Musica.apk`: arm64-v8a, para quase todos os celulares de 2017 em diante (inclusive Xiaomi);
- `Musica-32bits.apk`: armeabi-v7a, para celulares antigos.

O youtubedl-android exige `android:extractNativeLibs="true"` e `jniLibs.useLegacyPackaging = true`, já configurados.

---

## Como instalar no celular

**Opção 1: pelo arquivo**
1. Copie o `Musica.apk` (na raiz do repositório) para o celular (cabo, Drive, Telegram...).
2. Abra o arquivo e permita **instalar apps de fontes desconhecidas**.
3. Abra o app e aceite a permissão de acesso às músicas (e às notificações).

**Opção 2: pelo cabo USB com adb**
1. No celular, ative **Opções do desenvolvedor → Depuração USB**. Nos Xiaomi, ative também **Instalar via USB**.
2. No computador:

   ```bash
   ~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
   ```

Para atualizar, basta instalar o novo APK por cima: as playlists e as configurações são mantidas.

---

## Estrutura do projeto

```
projects/                       # raiz do repositório
├── README.md                   # este arquivo
├── Musica.apk                  # último APK gerado, pronto para instalar
└── MusicPlayer/                # projeto Android (abra esta pasta no Android Studio)
    ├── build.gradle.kts            # plugins (AGP, Kotlin, Compose) e versões
    ├── settings.gradle.kts         # repositórios e módulo :app
    ├── gradle.properties           # memória do Gradle, AndroidX
    ├── gradlew / gradlew.bat       # Gradle Wrapper
    ├── local.properties            # caminho do Android SDK (não versionado)
    └── app/
        ├── build.gradle.kts        # config do app, dependências, assinatura
        ├── proguard-rules.pro      # regras extras do R8 (vazio)
        └── src/main/
            ├── AndroidManifest.xml # permissões, Activity e serviço de música
            ├── res/
            │   ├── values/strings.xml          # nome do app ("Música")
            │   ├── values/themes.xml           # tema da janela (claro) + cor do ícone
            │   ├── values-night/themes.xml     # tema da janela (escuro)
            │   ├── drawable/ic_launcher_fg.xml # nota musical do ícone
            │   └── mipmap-anydpi-v26/ic_launcher.xml  # ícone adaptativo
            └── java/com/lucas/musica/
                ├── MainActivity.kt         # entrada do app, permissões, tema
                ├── MusicViewModel.kt       # estado central e regras do app
                ├── data/
                │   ├── Song.kt             # modelos Song e Playlist, conversão p/ MediaItem
                │   ├── MusicRepository.kt  # leitura das músicas via MediaStore
                │   └── PlaylistStore.kt    # salvar playlists, favoritas, tema e última sessão
                ├── online/
                │   ├── YtDlp.kt            # yt-dlp: iniciar, atualizar, buscar e baixar
                │   ├── Downloads.kt        # fila de downloads (2 ao mesmo tempo) e estado de cada um
                │   ├── CoverArt.kt         # baixa a capa do vídeo e recorta em quadrado
                │   ├── YouTubeSearch.kt    # busca direta na API do YouTube (sem yt-dlp)
                │   ├── DownloadService.kt  # serviço em primeiro plano + notificação de progresso
                │   └── MediaPublisher.kt   # copia o áudio para Music/Música Online e indexa
                ├── playback/
                │   └── PlaybackService.kt  # ExoPlayer + MediaSession em segundo plano
                └── ui/
                    ├── MusicApp.kt         # raiz da interface, navegação, voltar, apagar
                    ├── HomeScreen.kt       # cabeçalho, busca, abas e listas
                    ├── DetailScreen.kt     # álbum / artista / pasta / playlist
                    ├── NowPlayingScreen.kt # tela "Tocando agora"
                    ├── OnlineScreen.kt     # tela "Procurar online"
                    ├── DownloadsScreen.kt  # tela "Downloads" (progresso e fila)
                    ├── Selection.kt        # barras e diálogo do modo de seleção
                    ├── Components.kt       # capa, linha de música, mini player, diálogos
                    ├── Theme.kt            # cores dos temas claro e escuro
                    └── StatusBar.kt        # cor dos ícones da barra de status
```

---

## Arquitetura

O app segue o padrão **MVVM** simplificado, com uma única Activity:

```
┌────────────────────────────── processo do app ──────────────────────────────┐
│                                                                              │
│  MainActivity ──► MusicApp (Compose) ──lê estado / chama ações──► MusicViewModel
│                                                                     │   │    │
│                                         MusicRepository (MediaStore)◄┘   │    │
│                                         PlaylistStore (SharedPrefs) ◄────┘    │
│                                                                     │         │
│                                                    MediaController  │         │
│                                                         │ (comandos e eventos)│
│  PlaybackService (MediaSessionService) ◄────────────────┘                    │
│     └── ExoPlayer ──► notificação, tela de bloqueio, Bluetooth               │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **A interface não toca música diretamente.** Ela chama métodos do `MusicViewModel` (`play`, `next`, `togglePlay`...).
- O ViewModel envia esses comandos por um **`MediaController`** ao **`PlaybackService`**, que é dono do ExoPlayer.
- O serviço continua vivo quando o app é fechado, por isso a música não para.
- O ViewModel ouve os eventos do player (`Player.Listener.onEvents`) e atualiza o `PlayerState` (música atual, tocando/pausado, posição, aleatório, repetir). Como o estado é `mutableStateOf`, o Compose redesenha a tela sozinho.
- A **navegação** não usa biblioteca: é uma pilha (`backStack`) de objetos `Detail` dentro do ViewModel. A tela "Tocando agora" é uma camada animada por cima de tudo.

---

## Detalhes de cada parte

### `MainActivity.kt`
- Cria o `MusicViewModel` e decide se o tema é claro ou escuro (`ThemeMode`: sistema, claro, escuro).
- Ajusta a cor dos ícones da barra de status/navegação conforme o tema (`enableEdgeToEdge` + `SystemBarStyle`).
- Pede as permissões (veja [Permissões](#permissões)). Sem permissão, mostra a `PermissionScreen`; com permissão, carrega as músicas e mostra o `MusicApp`.

### `MusicViewModel.kt`
O "cérebro" do app. Guarda todo o estado observável:

| Estado | Para que serve |
|---|---|
| `songs`, `loaded` | lista de músicas do aparelho e se já terminou de carregar |
| `player: PlayerState` | música atual, tocando, posição, duração, aleatório, repetir |
| `playlists`, `favorites` | playlists do usuário e IDs favoritos |
| `selection` | IDs marcados no modo de seleção (vazio = modo normal) |
| `query`, `homePage` | texto da busca e aba aberta (sobrevivem ao abrir um álbum e voltar) |
| `themeMode` | tema escolhido |
| `backStack` | pilha de telas de detalhe |
| `songsForPlaylistDialog` | músicas aguardando escolha de playlist (abre o diálogo) |

Principais ações: `play(lista, índice, aleatório)`, `togglePlay`, `next`, `previous`, `seekTo`, `toggleShuffle`, `cycleRepeat`, `playNext`, `toggleFavorite`, `createPlaylist`, `addToPlaylist`, `removeFromPlaylist`, `deletePlaylist`, ações de seleção (`toggleSelect`, `selectAll`, `playSelected`, `removeSelectedFrom`...) e exclusão de arquivos (`deleteRequest`, `deleteDirectly`, `onDeleted`).

A posição da música é atualizada a cada 500 ms enquanto toca (corrotina no `viewModelScope`).

### `data/MusicRepository.kt`
Consulta o **MediaStore** (`MediaStore.Audio.Media`) filtrando `IS_MUSIC != 0` e duração ≥ 10 s. Para cada arquivo lê ID, título, artista, álbum, ID do álbum, duração e pasta. Artistas `<unknown>` viram "Artista desconhecido". O resultado é ordenado por título.

### `data/Song.kt`
- `Song`: dados de uma música, incluindo o `uri` `content://` usado para tocar e apagar.
- `Playlist`: `id` (UUID), `name` e lista de IDs de músicas.
- `albumArtUri(albumId)`: endereço da capa do álbum.
- `Song.toMediaItem()`: converte para o formato do Media3, com título, artista e capa para a notificação.

### `data/PlaylistStore.kt`
Salva playlists (JSON), favoritas (conjunto de IDs) e o tema em **SharedPreferences** (arquivo `biblioteca`). É simples de propósito; numa versão maior deve ser trocado pelo banco **Room**.

### `playback/PlaybackService.kt`
`MediaSessionService` do Media3 com um `ExoPlayer` configurado para:
- **foco de áudio** (pausa se outro app tocar ou chegar ligação);
- **pausar ao desconectar o fone** (`setHandleAudioBecomingNoisy`);
- **wake lock** para não ser interrompido com a tela desligada.

O Media3 cria a notificação de mídia automaticamente. Se o app for removido dos recentes e nada estiver tocando, o serviço se encerra (`onTaskRemoved`).

### `online/`: busca e download no YouTube

```
OnlineScreen ─► MusicViewModel.searchOnline() ─► YtDlp.search() ─► YouTubeSearch (API interna do YouTube, ~1 s)
                                                                  └► se falhar ou for link: yt-dlp "ytsearch20:…" --flat-playlist -J
             └► Downloads.enqueue() ─► DownloadService (notificação, mantém o processo vivo)
                     └► YtDlp.download()
                           ├─ CoverArt.fetch()  (em paralelo: capa do i.ytimg.com, recortada em quadrado)
                           ├─ yt-dlp #1: -f "bestaudio[ext=m4a]/bestaudio" --dump-single-json
                           │     (o app troca as capas do JSON pela capa local: file://…/capa.jpg)
                           └─ yt-dlp #2: --load-info-json … -x --audio-format m4a/best --embed-metadata --embed-thumbnail
                                 └► MediaPublisher.publish() ─► MediaStore (Music/Música Online) + scanner
                                       └► Downloads.onSongSaved ─► MusicViewModel.loadSongs()
```

- **`YtDlp.ensureReady`** extrai o Python na primeira vez (alguns segundos) e atualiza o yt-dlp pelo GitHub se a última atualização tiver mais de 24 h. A versão embutida na biblioteca envelhece rápido e o YouTube muda com frequência, por isso a atualização automática é importante.
- **Por que duas chamadas ao yt-dlp:** sozinho, o yt-dlp testa as capas da maior para a menor, e várias não existem (~2 s cada, ~10 s por música). Extraindo as informações primeiro, o app troca a lista de capas pela que ele mesmo baixou, e a segunda chamada (`--load-info-json`) baixa sem extrair de novo.
- **Sem conversão:** o áudio M4A (AAC) do YouTube é salvo como veio. Converter para MP3 custava ~5 s no emulador (mais no celular) e ainda perdia qualidade.
- O cache do yt-dlp fica em `cache/yt-dlp` (`--cache-dir`). Os arquivos temporários ficam em `cache/downloads/<id>/` e são apagados depois de copiados para a pasta pública. `--parse-metadata "title:%(album)s"` faz cada música virar o próprio "álbum", para cada uma ter sua capa.
- O progresso vem do callback do yt-dlp (0–100; −1 enquanto prepara, mostrado como "Preparando…"). Ao chegar em 100% ainda falta embutir metadados e capa ("Finalizando…").
- O log `YtDlp` (logcat) mostra o tempo de cada etapa, útil para achar gargalos.

**Tempo por música** (Ice Flow, 2:23, medido no emulador):

| Etapa | Antes | Agora |
|---|---|---|
| Extrair informações (inclui o desafio JavaScript do YouTube, ~11 s) | ~15 s | ~18 s |
| Procurar a capa | ~10 s | ~0,2 s (em paralelo) |
| Baixar o áudio (~2,2 MB) | ~1,5 s | ~1 s |
| Converter para MP3 | ~5 s | 0 |
| Segunda chamada ao yt-dlp | 0 | ~2,5 s |
| **Total** | **~35 s** | **~23 s** |

O que sobra é quase todo o desafio JavaScript, resolvido pelo QuickJS (o único motor JS disponível no Android). Foi testado o cliente `android_vr` do YouTube, que dispensava o desafio, mas ele não entrega mais áudio.

- Cancelar chama `destroyProcessById` no processo do yt-dlp.

### Lembrar a última música

O `MusicViewModel` salva a fila (IDs), o índice, a posição, o aleatório e o repetir em `PlaylistStore` sempre que o player muda de música, pausa, faz *seek* ou muda de modo, e a posição a cada ~5 s enquanto toca. Ao abrir o app, `restoreSession()` espera o player conectar **e** a biblioteca carregar. Se o player estiver vazio, recarrega a fila pausada na posição salva, ignorando músicas apagadas.

### `ui/MusicApp.kt`
Raiz da interface:
- mostra a `HomeScreen` ou a `DetailScreen` do topo da pilha;
- embaixo, o **mini player**, ou a **barra de ações** quando há seleção;
- a tela **Tocando agora** desliza por cima;
- trata o botão **voltar** nesta ordem: sair da seleção → fechar o player → voltar do detalhe → limpar a busca;
- controla o fluxo de **apagar** (diálogo do sistema no Android 11+ ou diálogo próprio no 8–10).

### `ui/HomeScreen.kt`
- **Cabeçalho** com título e resumo da biblioteca, e o botão de **tema** (menu com 3 opções).
- **Barra de pesquisa** (`BasicTextField` em formato de pílula).
- **Abas em pílula** (`TabChips`) sincronizadas com um `HorizontalPager` (permite deslizar). O deslizar é desativado durante a seleção.
- Conteúdo de cada aba: `SongsTab`, `ArtistsTab`, `AlbumsTab` (grade adaptativa), `FoldersTab`, `PlaylistsTab`.

### `ui/DetailScreen.kt`
Cabeçalho com capa, título, contagem e botões Tocar/Aleatório, seguido da lista de músicas. Em playlists, tem a lixeira para excluir a playlist (com confirmação).

### `ui/NowPlayingScreen.kt`
Tela cheia do player. Extrai a cor dominante da capa com **Palette** e anima o fundo. O `Slider` só faz o "seek" quando o dedo é solto, para não ficar engasgando. Força ícones claros na barra de status (`LightStatusBarIcons`).

### `ui/Selection.kt`
`SelectionTopBar` (contador e selecionar tudo), `SelectionActions` (barra de ações inferior) e `ConfirmDeleteDialog`.

### `ui/Components.kt`
Componentes reaproveitados:
- `AlbumArt`: carrega a capa em segundo plano com cache (`LruCache` de 24 MB). Sem capa, desenha um gradiente com cor fixa por álbum (`albumHue`).
- `SongRow` / `SongList`: linha da música com menu ⋮, toque longo e caixa de seleção.
- `MiniPlayer`, `NewPlaylistDialog`, `AddToPlaylistDialog`, `PermissionScreen`, `EmptyMessage`, `formatTime`.

### `ui/Theme.kt`
Esquemas de cores claro e escuro do Material 3. Define também `LocalContentColor` para que textos sem cor explícita fiquem legíveis nos dois temas.

---

## Permissões

| Permissão | Quando | Por quê |
|---|---|---|
| `READ_MEDIA_AUDIO` | Android 13+ | ler as músicas |
| `READ_EXTERNAL_STORAGE` | Android 8–12 | ler as músicas |
| `WRITE_EXTERNAL_STORAGE` | Android 8–10 | apagar arquivos |
| `POST_NOTIFICATIONS` | Android 13+ | mostrar a notificação do player |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | sempre | tocar em segundo plano |
| `WAKE_LOCK` | sempre | não parar com a tela desligada |
| `INTERNET` | sempre | busca, download e atualização do yt-dlp (só na tela Online) |
| `FOREGROUND_SERVICE_DATA_SYNC` | sempre | continuar os downloads com o app fechado |

`android:requestLegacyExternalStorage="true"` permite apagar diretamente no Android 10.
No Android 11+ o app usa `MediaStore.createDeleteRequest`, e o **sistema** mostra a confirmação.

---

## Onde os dados ficam salvos

- **Músicas:** não são copiadas; o app só lê os arquivos que já estão no celular.
- **Playlists, favoritas, tema e última sessão:** `/data/data/com.lucas.musica/shared_prefs/biblioteca.xml` (apagados se o app for desinstalado).
- **Músicas baixadas:** `Music/Música Online/` no armazenamento do celular (continuam lá mesmo desinstalando o app).
- **yt-dlp/Python:** `/data/data/com.lucas.musica/no_backup/youtubedl-android/` (extraído na primeira busca).
- As playlists guardam o **ID do MediaStore** de cada música. Se um arquivo for apagado fora do app, ele some da playlist automaticamente.

---

## Testando no emulador

```bash
export ANDROID_HOME=~/Android/Sdk
SDK=$ANDROID_HOME/cmdline-tools/latest/bin

# 1. Instalar o emulador e uma imagem do Android 14
$SDK/sdkmanager "emulator" "system-images;android-34;google_apis;x86_64"
echo no | $SDK/avdmanager create avd -n teste -k "system-images;android-34;google_apis;x86_64" -d pixel_6

# 2. Ligar (o avdmanager salva os dispositivos em ~/.config/.android/avd)
ANDROID_AVD_HOME=~/.config/.android/avd $ANDROID_HOME/emulator/emulator -avd teste &

# 3. Instalar o app e copiar músicas de teste
adb install -r app/build/outputs/apk/release/app-release.apk
adb push minhas-musicas/ /sdcard/Music/Teste/
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/Teste/arquivo.mp3

# 4. Ver erros do app
adb logcat -b crash

# 5. Desligar (o emulador usa bastante CPU e memória)
adb emu kill
./gradlew --stop
```

Para gerar MP3s de teste com `ffmpeg`:

```bash
ffmpeg -f lavfi -i "sine=frequency=440:duration=40" \
  -metadata title="Faixa 1" -metadata artist="Banda Azul" -metadata album="Primeiro" \
  -codec:a libmp3lame faixa1.mp3
```

> No emulador, logo após ligar, pode aparecer "System UI isn't responding". Isso é do emulador, não do app: toque em **Wait**.

---

## Problemas conhecidos e soluções

| Problema | Causa | Situação |
|---|---|---|
| App fechava ao abrir quando havia músicas | a cor das capas sem imagem usava `(albumId * 47) % 360`, que podia dar negativo e gerar uma cor inválida | **corrigido** com `Math.floorMod` (`albumHue`) |
| Textos pretos no tema escuro | faltava a cor padrão de conteúdo | **corrigido** em `Theme.kt` |
| Notebook barulhento depois de compilar | o Gradle fica rodando em segundo plano | rode `./gradlew --stop` |
| Lista ordena "Faixa 10" antes de "Faixa 2" | ordenação alfabética simples | pendente (usar ordenação natural) |
| Botão "Remover" e exclusão no Android 8–10 | ainda não testados em aparelho real | pendente |
| Downloads começam a falhar de repente | o YouTube mudou e o yt-dlp ficou desatualizado | menu ⋮ → **Atualizar yt-dlp** (ou esperar a atualização automática) |
| Com teclado físico, Enter na busca online pode fechar a tela | o Enter também "clica" no botão voltar que recebe o foco | pendente (no teclado da tela funciona normal) |

---

## Próximos passos

- [ ] Tela com a **fila de reprodução** (ver, reordenar e remover)
- [ ] Trocar SharedPreferences por **Room**
- [ ] **Equalizador** (`android.media.audiofx.Equalizer`)
- [ ] **Letras** sincronizadas (arquivos `.lrc`)
- [ ] **Timer para dormir**
- [ ] Ordenação configurável (nome, data, duração) e ordenação natural
- [x] Lembrar a última música e posição ao reabrir o app
- [x] Procurar e baixar músicas online (yt-dlp)
- [ ] Widget na tela inicial
- [ ] Chave de assinatura própria para publicar na Play Store
