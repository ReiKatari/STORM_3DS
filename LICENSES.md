# Open Source Licenses & Notices - STORM 3DS

STORM 3DS is distributed free of charge under the **GNU General Public License v3.0 or later (GPLv3+)** (`LICENSE`). No proprietary monetization, paywalls, or closed-source DRM components are shipped in the emulator.

Full source code is published at:
- https://github.com/ReiKatari/STORM_3DS
- https://stormgamesworld.ru/git/STORM_TEAM/STORM_3DS

---

## Core Emulation & Graphics Components

| Component | License | Upstream Project |
|---|---|---|
| Azahar / Citra 3DS Core & Frontends | GPLv2+ / GPLv3+ | https://github.com/azahar-emu/azahar |
| Citra MMJ Virtual Memory Finder & HLE Hacks | GPLv2+ | https://github.com/weihuoya/citra |
| Borked3DS SPIRV-Tools Optimization Pipeline | GPLv2+ | https://github.com/Borked3DS/Borked3DS |
| Dynarmic (ARM A32/A64 Dynamic Recompiler) | 0BSD | https://github.com/azahar-emu/dynarmic |
| Teakra (Nintendo 3DS DSP HLE/LLE) | MIT | https://github.com/azahar-emu/teakra |
| sirit (Runtime SPIR-V Emitter) | BSD-3-Clause | https://github.com/azahar-emu/sirit |
| librashader (RetroArch Slang Shader Runtime) | MPL-2.0 / GPLv3+ | https://github.com/SnowflakePowered/librashader |
| Khronos Vulkan Headers / glslang / SPIRV-Tools | Apache-2.0 | https://github.com/KhronosGroup/SPIRV-Tools |
| Vulkan Memory Allocator (VMA) | MIT | https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator |
| GLAD | MIT | https://github.com/Dav1dde/glad |

## Audio, Compression, Crypto & System Libraries

| Component | License | Upstream Project |
|---|---|---|
| Cubeb (Cross-Platform Audio) | ISC | https://github.com/mozilla/cubeb |
| SoundTouch (Audio Time-Stretching) | LGPLv2.1 | https://www.surina.net/soundtouch/ |
| FAAD2 (AAC Audio Decoder) | GPLv2+ | https://github.com/knik0/faad2 |
| Zstandard (zstd) | BSD-3-Clause | https://github.com/facebook/zstd |
| xxHash | BSD-2-Clause | https://github.com/Cyan4973/xxHash |
| Crypto++ | Boost Software License 1.0 | https://www.cryptopp.com/ |
| LibreSSL | ISC / OpenSSL | https://www.libressl.org/ |
| Boost | Boost Software License 1.0 | https://www.boost.org/ |
| libyuv | BSD-3-Clause | https://chromium.googlesource.com/libyuv/libyuv |
| ENet / cpp-httplib / fmt / nlohmann_json | MIT | Upstream open-source repositories |
| libadrenotools | MIT / BSD-2-Clause | https://github.com/bylaws/libadrenotools |

## Desktop & Android Frontend Frameworks

| Component | License | Upstream Project |
|---|---|---|
| Qt 6 (Widgets, Gui, Multimedia, Network, Svg) | LGPLv3 / GPLv3 | https://www.qt.io/ |
| SDL2 | zlib | https://www.libsdl.org/ |
| WatermelonDS Android UI Architecture | GPLv3 | https://github.com/SapphireRhodonite/WatermelonDS |
| melonDS Android rcheevos-api HTTP Client | GPLv3 | https://github.com/rafaelvcaetano/melonDS-android |
| AndroidX / Jetpack Compose / Room / Hilt | Apache-2.0 | https://developer.android.com/jetpack |
| OkHttp / Coil / Markwon | Apache-2.0 | Square / Coil / Noties open-source projects |
| Apache Commons Compress / XZ for Java | Apache-2.0 / Public Domain | https://commons.apache.org/proper/commons-compress/ |

---

## RetroAchievements Client Identity Disclosure

In accordance with RetroAchievements Hardcore Compliance Requirements (Sections C and G):
- Early versions up to `1.12.0` inherited a forked HTTP User-Agent (`RetroArch/1.19.1 (Android) rcheevos/11.7.0`).
- Starting with commits after `1.12.0` (`1.12.1+`), STORM 3DS identifies exclusively with its own client User-Agent (`STORM_3DS/<version> (Android <release>) azahar_core/<version>`), enforces all Hardcore restrictions in both Android Kotlin and native emulation settings layers, and is actively working toward official `rcheevos` (`rc_client` / `rc_hash`) integration upon official RetroAchievements Nintendo 3DS support.
