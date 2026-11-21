# STORM 3DS 1.0.0

Первый публичный релиз гибридного эмулятора Nintendo 3DS для Android с интерфейсом STORM и расширенным нативным ядром.

## Основные возможности 1.0.0
- **Два варианта APK**:
  - `STORM_3DS_1.0.0.apk` - сборка с поддержкой пользовательских Turnip/Adreno-драйверов (`AdrenoTools ON`, для устройств Qualcomm Snapdragon).
  - `STORM_3DS_1.0.0_LEGACY.apk` - сборка совместимости без AdrenoTools (`AdrenoTools OFF`, для Mali, PowerVR, Xclipse, MediaTek и стоковых драйверов).
- **Все 3DS-форматы и архивы**: `.3ds`, `.cci`, `.cxi`, `.app`, `.cia`, `.3dsx`, `.elf`, `.axf`, `.zcci`, `.zcxi`, `.zcia`, `.z3dsx`, `.bcci`, `.bcxi`, `.bcia`, `.artic`, а также `.zip`, `.7z`, `.rar` с автоматической распаковкой.
- **Полная расшифровка ROM/NCCH/CIA**: поддержка `boot9.bin`, `keys.txt`, `aes_keys.txt` и `seeddb.bin` в `STORM 3DS/sysdata/`.
- **Графика и производительность**: Vulkan 1.1+ с `OptimizeSPIRV` (Borked3DS / SPIRV-Tools), OpenGL ES 3.2, Zero-copy шейдеры RetroArch `.slangp` (`liblibrashader.so`) и спидхаки Citra MMJ (`FMV Hack`, `Skip Slow Draw`, `Skip Texture Copy`, `Accurate Mul`, `Audio Stretching`).
- **Продвинутый движок 3D**: 7 стереоскопических режимов (включая `Gyro Parallax`), живой 3D-слайдер глубины со светодиодом `3D` и опция отключения рендера правого глаза.
- **Интерфейс STORM и 3DS-кастомизация**: точные пропорции экранов (`5:3` верхний, `4:3` нижний), `Circle Pad`, `C-Stick`, `ZL`/`ZR`/`HOME`, кнопки `Combo 1/2`, 10 новых 3DS-стилей кнопок (34 всего), 30 консольных скинов 3DS/2DS/STORM, 3D-анимация раскрытия раскладушки, Live OCR+TTS переводчик, нативный RAM Cheat Finder с кодами Gateway, база читов MMJ (123 игры), Zstd Auto-Timeline Rewind и 29 авто-профилей игр по Title ID.

## Контрольные суммы (SHA-256)
| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `STORM_3DS_1.0.0.apk` | 81 765 822 байт (77.98 MiB) | `e25d13e14dad9c494aee51d911f204a387881b8d32ada39445328770d2859e6b` |
| `STORM_3DS_1.0.0_LEGACY.apk` | 81 759 855 байт (77.97 MiB) | `0cd07a73c9dd98d1de272709a290f2b040b316139221368c913b427f53edb7b6` |
