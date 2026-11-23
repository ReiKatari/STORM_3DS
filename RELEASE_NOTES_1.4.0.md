# STORM 3DS 1.4.0 (`versionCode = 10400`)

Обновление 1.4.0 исправляет критическую ошибку запуска `.zcia`/`.cia`/`.bcia` (включая `Xenoblade Chronicles 3D`), устраняет ограничения 4 GiB для крупных образов Nintendo 3DS и проблемы распаковки `.zip`/`.7z`/`.rar`, задаёт имя пользователя 3DS по умолчанию `STORM 3DS`, добавляет нативную поддержку кастомных Vulkan-драйверов ARM Mali / PanVK (`malisx2`) и настройки Freedreno, а также переносит актуальные исправления стабильности и редактора раскладок линейки STORM.

## Что нового и исправлено в 1.4.0

- **Исправление запуска `.zcia`, `.cia`, `.bcia` и крупных игр до 4 GiB (включая `Xenoblade Chronicles 3D`)**:
  - **Поиск установленного `.app` по `title_id` (`MelonEmulator` + `findInstalledTitlePath`)**: исправлен баг сравнения `000400000f700200` со слэш-путём `.../00040000/0f700200/content/00000000.app`, из-за которого любой `.zcia`/`.cia` гарантированно завершался ошибкой `NDS_FAILED` и повторным откатом рендера с Vulkan на OpenGL.
  - **Мгновенный повторный запуск установленных `.zcia`/`.cia` и быстрое чтение метаданных Z3DS**: `getCiaTitleId` и `GameInfo` теперь читают `titleinfo` и `smdh` напрямую из заголовка Z3DS без декомпрессии 50+ МБ Zstd, а повторный запуск уже установленного тайтла выполняется мгновенно без переустановки.
  - **Защита от таймаута и зависаний (`EmulatorActivity`, `am.cpp`, `CiaInstallWorker`)**: установка крупных `.zcia` (3-4 ГБ) и распаковка архивов больше не обрывается жёстким таймаутом 90 секунд; в `InstallCIA` буфер увеличен до 1 MiB, добавлена защита от бесконечного цикла при `bytes_read == 0`, исправлен префикс `!` в `CiaInstallWorker` (без копирования 3+ ГБ в `cacheDir`), а `GetCIAInfos` и TAR-проверки поддерживают Z3DS и любой регистр расширений.
  - **Поддержка 4 GiB ROM и авто-режим New 3DS (`ncch_container.cpp`, `core.cpp`)**: устранено 32-битное переполнение смещений NCSD/RomFS/ExeFS для игр ~3.6-4 GiB (`MAX_IMPORT_FILE_SIZE = 4 GiB`, 64-битные смещения `u64`), а эксклюзивы New 3DS автоматически включают режим `New 3DS` (4 ядра + расширенное ОЗУ).
  - **Улучшения архивов `.zip`, `.7z`, `.rar` и `00040002` демо-версий**: `ZipRomFileProcessor` использует `ZipFile` с поддержкой Zip64, `SevenZRomFileProcessor` корректно работает с SAF-дескрипторами без `IOException`, а выбор файла внутри архива отдаёт приоритет прямым образам (`.3ds`/`.zcci`/`.cxi`) перед `.cia`/`.zcia`.
- **Имя пользователя Nintendo 3DS по умолчанию `STORM 3DS`**:
  - В нативном конфиге (`cfg_defaults.cpp`) и Java/Kotlin-слое (`SystemSaveGame`, `MelonEmulator`, `FirmwarePreferencesFragment`) имя по умолчанию изменено с `AZAHAR`/`Player` на `STORM 3DS` с автоматической миграцией стандартного имени и сохранением пользовательских имён.
- **Поддержка драйверов ARM Mali / PanVK (`malisx2`) и настройки Freedreno**:
  - Нативный загрузчик (`native.cpp`, `vk_platform.cpp`, `vk_instance.cpp`) поддерживает `vk_icdGetInstanceProcAddr` (ICD v5), `eMesaPanvk` и переменные среды PanVK, а менеджер драйверов открывает репозитории `bmdhacks/malisx2` и точный импорт для Mali Bifrost v7 - 5th Gen v12.
  - Для Qualcomm Adreno добавлен раздел **Настройки Freedreno** (`FD_DEV_FEATURES`, `TU_DEBUG`, `FD_MESA_DEBUG`).
- **Улучшения стабильности, локализации и редактора раскладок**:
  - Добавлена защита настроек `SharedPreferencesCompat`, надёжное усечение SAF-файлов (`UriFileHandler`), фоновая ротация логов с fallback (`AppLogFileRecorder`), тихая фоновая проверка обновлений при свайпе вверх (`checkForUpdatesSilently`) и живое превью скина консоли в редакторе раскладок (`LayoutEditorManagerView`).
  - Выполнен 100% паритет строк по всем 10 локалям (`en`, `ru`, `es`, `fr`, `de`, `it`, `pt-BR`, `id`, `zh-Hans`, `ja`) и русская вычитка интерфейса.
  - Публикуется единая универсальная подписанная сборка `STORM_3DS_1.4.0.apk` (`arm64-v8a` + `x86_64`, `STORM_ENABLE_ADRENOTOOLS=ON`).

## Файл релиза и контрольная сумма

| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `STORM_3DS_1.4.0.apk` | `82 379 898` байт (`78.56 MiB`) | `dd53e58bcb9d23302e278caf214e686c31753d2a010798ef1fc762cd6940dc1e` |
