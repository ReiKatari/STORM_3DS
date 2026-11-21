<p align="center">
  <img src="logo.png" width="220" alt="STORM 3DS Logo" />
</p>

<h1 align="center">STORM 3DS</h1>

<p align="center">
  <strong>Продвинутый гибридный эмулятор Nintendo 3DS для Android (Vulkan + OpenGL ES 3.2 + интерфейс STORM)</strong><br />
  Версия <code>1.0.0</code> | Архитектуры: <code>arm64-v8a</code> и <code>x86_64</code> | Лицензия: <code>GPL-3.0-or-later</code>
</p>

<p align="center">
  <a href="https://github.com/ReiKatari/STORM_3DS">GitHub Repository</a> -
  <a href="https://github.com/ReiKatari/STORM_3DS/releases/tag/1.0.0">Релиз STORM 3DS 1.0.0</a> -
  <a href="https://stormgamesworld.ru/programs?repo=STORM_3DS">Страница на STORM GAMES WORLD</a>
</p>

---

## О проекте

**STORM 3DS** - это высокопроизводительный эмулятор семейства консолей Nintendo 3DS для Android 10+ (API 29+), объединяющий современное C++20 ядро **Azahar**, оптимизации и спидхаки из **Citra MMJ** и **Borked3DS**, полную поддержку расшифровки образов **AzaharPlus**, сетевые и удобные доработки **Mandarine**, а также фирменный визуальный интерфейс и экосистему инструментов **STORM DS** (Jetpack Compose + Material, 9 тем, Live OCR-переводчик с TTS, сканер RAM, шейдеры RetroArch `.slangp`, менеджер Vulkan-драйверов, Quick Resume, статистика игрока и кастомные скины).

---

## Ключевые особенности и улучшения

### 1. Универсальная поддержка ВСЕХ форматов 3DS и архивов
- **Нативные и сжатые форматы (16)**: `.3ds`, `.cci`, `.cxi`, `.app`, `.cia`, `.3dsx`, `.elf`, `.axf`, `.zcci`, `.zcxi`, `.zcia`, `.z3dsx` (Z3DS), `.bcci`, `.bcxi`, `.bcia` (Bundle) и `.artic` (Artic Base).
- **Архивы (3)**: `.zip`, `.7z`, `.rar` с автоматической фоновой распаковкой в `STORM 3DS/cache/extracted_3ds/`, сохранением исходного расширения контейнера и LRU-очисткой кэша.
- **Полная расшифровка зашифрованных ROM / NCCH / CIA**: встроенный конвейер ключей `boot9.bin`, `keys.txt`, `aes_keys.txt` и `seeddb.bin` в `STORM 3DS/sysdata/` (включая расшифровку локальных CIA без дампа аппаратного `otp.bin`).
- **Автоматические метаданные и обложки**: чтение SMDH-иконок, названий и Title ID через нативный `GameInfo`, а также загрузка бокс-артов из Libretro `Nintendo - Nintendo 3DS`.

### 2. Графика Vulkan / OpenGL ES 3.2, SPIRV-Tools и RetroArch `.slangp`
- **Borked3DS SPIR-V Optimizer**: оптимизация, валидация и легализация шейдерных байт-кодов через `SPIRV-Tools-opt` (`OptimizeSPIRV`).
- **Zero-copy интеграция `liblibrashader.so`**: динамическая загрузка `dlopen("liblibrashader.so")` с минимальными накладными расходами как для Vulkan (`CopyToSwapchain`), так и для OpenGL ES 3.2 (`TryPresent`).
- **Встроенные пресеты шейдеров (`.slangp` + `.slang`)**: `lcd-3ds`, `crt-scanlines`, `sharp-bilinear`, `vibrant-hd`, `grayscale-pocket`.
- **Citra MMJ Speedhacks**: FMV Core Downcount Hack, Skip Slow Draw, Skip CPU Texture Copy, Accurate Shader Multiplication, Audio Time Stretching, Separable Shaders.
- **Управление драйверами AdrenoTools / Turnip** (в основной сборке `STORM_3DS_1.0.0.apk`) и чистый профиль совместимости с системными драйверами GPU (в `STORM_3DS_1.0.0_LEGACY.apk`).

### 3. Продвинутый движок стереоскопического 3D
- **7 режимов вывода 3D**: `Off`, `Side-by-Side`, `Side-by-Side Full`, `Anaglyph (Dubois)`, `Interlaced`, `Reverse Interlaced`, `Cardboard VR` и фирменный режим **`Gyro Parallax`** (`vulkan_present_gyro.frag` / `opengl_present_gyro.frag`), реагирующий на наклон устройства.
- **Живой аппаратный слайдер 3D глубины (`0..100`)**: с тактильным щелчком в позиции `OFF` и светящимся изумрудным светодиодом `3D`.
- **Оптимизация рендера правого глаза**: опция `Disable Right Eye Render` для прироста производительности в поддерживаемых играх.

### 4. Интерфейс STORM, 3DS-управление и 30 скинов
- **Оригинальная геометрия экранов 3DS**: верхний экран строго `5:3` (`400x240`), нижний сенсорный экран строго `4:3` (`320x240`), гибкий редактор макетов с прямой синхронизацией прямоугольников в C++ (`setCustomScreenLayout`) и поддержкой второго внешнего дисплея (`SecondaryDisplay`).
- **Нативные 3DS-элементы управления**: аналоговый `Circle Pad`, мини-стик `C-Stick` (New 3DS), кнопки `ZL`, `ZR`, `HOME`, слайдер 3D глубины и программируемые макро-кнопки **`Combo 1` / `Combo 2`** (до 4 одновременных кнопок).
- **10 новых 3DS-стилей кнопок** (34 стиля всего): *New 3DS SFC Colored*, *STORM Cyber 3D*, *Hyrule Gold 3D*, *Metroid Varia Visor*, *Galaxy Nebula*, *Animal Leaf*, *Monster Hunter Forge*, *PokeBall Dual*, *Persona Velvet*, *Crystal Prism 3D*.
- **30 консольных скинов Nintendo 3DS / 2DS / STORM** и **3D-анимация раскрытия раскладушки 3DS** при запуске игры (`Console3DBootScreen`).
- **9 тем оформления и Live-инструменты**: тема *STORM NIGHT* и еще 8 палитр, живой экранный переводчик **ML Kit OCR + TTS** (`PixelCopy`), нативный C++ поиск читов в оперативной памяти (**RAM Cheat Finder**) с генератором кодов Gateway 3DS, встроенная база читов на 123 игры из **Citra MMJ** + `3dstdb`, асинхронный Zstd-буфер перемотки (**Auto-Timeline Rewind**), 29 профилей авто-настройки игр по Title ID, 1-тап установка обновлений/DLC `.cia`, подготовка папок для модов и HD-текстур, загрузка Amiibo `.bin`, клиент **Artic Base**, выбор сервера **Pretendo / Nimbus**, комнаты **NetPlay**, фильтрация логов по регулярным выражениям и фоновый сервис `Storm3dsForegroundService` для Android 14+.

---

## Версии релиза 1.0.0

| Файл APK | Назначение | Поддержка Turnip / AdrenoTools |
| --- | --- | --- |
| `STORM_3DS_1.0.0.apk` | Основная сборка для смартфонов Snapdragon (Adreno GPU) и современных Android-устройств | Включена (`STORM_ENABLE_ADRENOTOOLS=ON`) |
| `STORM_3DS_1.0.0_LEGACY.apk` | Сборка повышенной совместимости для устройств без кастомных Adreno-драйверов (Mali, PowerVR, Xclipse, MediaTek, stock Android) | Отключена (`STORM_ENABLE_ADRENOTOOLS=OFF`) |

---

## Сборка из исходного кода

### Требования
- **JDK**: 21
- **Android SDK**: Platform 36 (`compileSdk = 36`, `minSdk = 29`)
- **Android NDK**: `28.0.13004108`
- **CMake**: `3.22.1` + `Ninja`
- **Rust (`rustup` + `cargo`)**: канал `stable` с целями `aarch64-linux-android` и `x86_64-linux-android` (для сборки `liblibrashader.so`)

### Команды сборки
```bash
git clone --recursive https://github.com/ReiKatari/STORM_3DS.git
cd STORM_3DS

# Низконагруженная релизная сборка обеих версий (рекомендуется)
./gradlew assembleGitHubProdRelease assembleLegacyProdRelease --max-workers=3 -Dorg.gradle.priority=low
```
Готовые подписанные APK сохраняются в папку `releases/`:
- `releases/STORM_3DS_1.0.0.apk`
- `releases/STORM_3DS_1.0.0_LEGACY.apk`

---

## Благодарности и открытые проекты (Credits)

Проект **STORM 3DS** создан благодаря труду разработчиков и сообществ следующих проектов с открытым исходным кодом:

- **[Azahar Emulator](https://github.com/azahar-emu/azahar)** (команда Azahar и авторы объединения PabloMK7 + Lime3DS) - основное C++20 ядро эмуляции Nintendo 3DS, поддержка Z3DS, Artic Base, Bundle и современный Android Vulkan/GLES бэкенд.
- **[Citra Emulator](https://citra-emu.org/)** - оригинальный открытый эмулятор Nintendo 3DS, на чьем фундаменте выросла вся экосистема 3DS-эмуляции.
- **[Citra MMJ](https://github.com/weihuoya/citra)** (**weihuoya**) - спидхаки мобильной производительности (`FMV Hack`, `Skip Slow Draw`, `Skip Texture Copy`), алгоритм сканирования виртуальной памяти для поиска читов и база читов/игровых ID (`cheats`, `3dstdb`).
- **[Borked3DS](https://github.com/Borked3DS/Borked3DS)** (релиз `v2025.01.23` и команда Borked3DS) - интеграция оптимизатора шейдеров `SPIRV-Tools-opt` (`OptimizeSPIRV`) с валидацией и легализацией SPIR-V.
- **[AzaharPlus](https://github.com/AzaharPlus/AzaharPlus)** и **Lime3DS** / **Mandarine** - улучшения совместимости расшифровки зашифрованных образов ROM/NCCH/CIA (`boot9`, `aes_keys`, `seeddb`), поддержка старых форматов без лишних ограничений и сетевые улучшения.
- **[STORM DS](https://github.com/ReiKatari/STORM_DS)** (**ReiKatari**), **[melonDS-android](https://github.com/rafaelvcaetano/melonDS-android)** (**Rafael V. Caetano**) и **[melonDS](https://github.com/melonDS-emu/melonDS)** (**Arisotura** и команда) - визуальная архитектура приложения на Jetpack Compose + Material, 9 тем, Live OCR+TTS переводчик, менеджер Vulkan-драйверов, RetroAchievements UI, Quick Resume, Player Stats, макеты экранов и компоненты управления.
- **[librashader](https://github.com/SnowflakePowered/librashader)** (**SnowflakePowered**) - движок постобработки RetroArch `.slangp` шейдеров для Vulkan и OpenGL ES.
- **[SPIRV-Tools](https://github.com/KhronosGroup/SPIRV-Tools)** и **Khronos Vulkan SDK / Glslang / VMA** - компиляция, оптимизация и управление памятью Vulkan-шейдеров.
- **[libadrenotools](https://github.com/bylaws/libadrenotools)** (**Bylaws / Bystander**) - безопасная загрузка пользовательских Turnip/Vulkan-драйверов на устройствах Qualcomm Adreno.
- **[RetroAchievements](https://retroachievements.org/)** и **[rcheevos](https://github.com/RetroAchievements/rcheevos)** - поддержка инфраструктуры достижений.
- **Эмуляторные библиотеки ядра и утилит**: **Dynarmic** (ARM JIT рекомпилятор), **Teakra** (DSP LLE), **SoundTouch**, **Cubeb**, **Zstandard (`zstd`)**, **Crypto++**, **Boost**, **xxHash**, **libyuv**, **faad2**, **enet**, **Sirit**, **Google ML Kit** (распознавание и офлайн-перевод текста), **Apache Commons Compress**, **junrar**, **XZ for Java**, **Coil** и **Markwon**.

---

## Лицензия

Исходный код STORM 3DS распространяется под лицензией **GNU General Public License v3.0 или новее** ([LICENSE](LICENSE)).
