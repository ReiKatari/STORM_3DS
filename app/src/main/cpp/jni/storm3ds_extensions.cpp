// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#include <algorithm>
#include <cstring>
#include <locale>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>
#include <jni.h>
#include "common/android_utils.h"
#include "common/arch.h"
#include "common/common_paths.h"
#include "common/file_util.h"
#include "common/logging/backend.h"
#include "common/file_derived.h"
#include "common/settings.h"
#include "common/string_util.h"
#include "common/tar_file.h"
#include "audio_core/input_details.h"
#include "core/core.h"
#include "core/hle/service/am/am.h"
#include "core/file_sys/cia_container.h"
#include "core/file_sys/seed_db.h"
#include "core/hw/aes/key.h"
#include "core/hw/unique_data.h"
#include "core/loader/loader.h"
#include "jni/android_common/android_common.h"
#include "jni/config.h"
#include "jni/emu_window/emu_window.h"
#include "video_core/gpu.h"
#include "video_core/renderer_base.h"
#include "video_core/storm_librashader.h"
#if defined(STORM_ENABLE_ADRENOTOOLS) && CITRA_ARCH(arm64)
#include <adrenotools/driver.h>
#endif

namespace StormBridge {
EmuWindow_Android* GetPrimaryWindow();
EmuWindow_Android* GetSecondaryWindow();
std::recursive_mutex& GetSurfaceMutex();
} // namespace StormBridge

extern "C" {

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setGyro3dParams(
    JNIEnv*, jobject, jfloat tilt_x, jfloat tilt_y, jfloat strength) {
    Settings::values.gyro_3d_tilt_x = std::clamp(static_cast<f32>(tilt_x), -1.0f, 1.0f);
    Settings::values.gyro_3d_tilt_y = std::clamp(static_cast<f32>(tilt_y), -1.0f, 1.0f);
    Settings::values.gyro_3d_strength.SetValue(std::clamp(static_cast<f32>(strength), 0.0f, 3.0f));
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setFactor3d(
    JNIEnv*, jobject, jint factor) {
    Settings::values.factor_3d.SetValue(std::clamp(static_cast<u32>(factor), 0u, 100u));
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setRender3dMode(
    JNIEnv*, jobject, jint mode) {
    const auto clamped = std::clamp<int>(mode, 0, 7);
    const auto which_display = clamped == 0 ? Settings::StereoWhichDisplay::None
                                            : Settings::StereoWhichDisplay::PrimaryOnly;
    Settings::values.render_3d.SetValue(static_cast<Settings::StereoRenderOption>(clamped));
    Settings::values.render_3d_which_display.SetValue(which_display);
    Config::AddDefaultSettingValue("render_3d", std::to_string(clamped));
    Config::AddDefaultSettingValue("render_3d_which_display",
                                   std::to_string(static_cast<int>(which_display)));
    auto& system = Core::System::GetInstance();
    if (system.IsPoweredOn()) {
        system.GPU().Renderer().Settings().shader_update_requested = true;
    }
    std::scoped_lock lock{StormBridge::GetSurfaceMutex()};
    if (auto* w = StormBridge::GetPrimaryWindow()) {
        w->RefreshFramebufferLayout();
    }
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setDisableRightEyeRender(
    JNIEnv*, jobject, jboolean enabled) {
    Settings::values.disable_right_eye_render.SetValue(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setLibrashaderPreset(
    JNIEnv* env, jobject, jboolean enabled, jstring j_preset_path) {
    const bool enable_val = (enabled == JNI_TRUE);
    const std::string preset_path = GetJString(env, j_preset_path);
    const bool changed = (Settings::values.librashader_enabled.GetValue() != enable_val) ||
                         (Settings::values.librashader_preset_path.GetValue() != preset_path);
    Settings::values.librashader_enabled.SetValue(enable_val);
    Settings::values.librashader_preset_path.SetValue(preset_path);
    Config::AddDefaultSettingValue("librashader_enabled", enable_val ? "1" : "0");
    Config::AddDefaultSettingValue("librashader_preset_path", preset_path);
    if (changed) {
        VideoCore::StormLibrashader::RequestReload();
    }
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_configureLibrashader(
    JNIEnv* env, jobject, jboolean enabled, jstring j_preset_path, jstring j_parameters,
    jboolean clear_history) {
    const bool enable_val = (enabled == JNI_TRUE);
    const std::string preset_path = GetJString(env, j_preset_path);
    const std::string parameters = GetJString(env, j_parameters);
    const bool changed = (Settings::values.librashader_enabled.GetValue() != enable_val) ||
                         (Settings::values.librashader_preset_path.GetValue() != preset_path);
    Settings::values.librashader_enabled.SetValue(enable_val);
    Settings::values.librashader_preset_path.SetValue(preset_path);
    Settings::values.librashader_parameters.SetValue(parameters);
    Config::AddDefaultSettingValue("librashader_enabled", enable_val ? "1" : "0");
    Config::AddDefaultSettingValue("librashader_preset_path", preset_path);
    Config::AddDefaultSettingValue("librashader_parameters", parameters);
    if (clear_history == JNI_TRUE) {
        VideoCore::StormLibrashader::RequestClearHistory();
    }
    if (changed) {
        VideoCore::StormLibrashader::RequestReload();
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_storm3ds_emulator_nativebridge_NativeLibrary_queryLibrashaderParams(JNIEnv* env, jobject,
                                                                             jstring j_preset_path) {
    const std::string preset_path = GetJString(env, j_preset_path);
    const auto params = VideoCore::StormLibrashader::QueryPresetParameters(preset_path);
    jclass str_class = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(params.size()), str_class, nullptr);
    if (!out) {
        return nullptr;
    }
    for (std::size_t i = 0; i < params.size(); ++i) {
        std::string desc = params[i].description;
        std::replace(desc.begin(), desc.end(), '\t', ' ');
        std::replace(desc.begin(), desc.end(), '\r', ' ');
        std::replace(desc.begin(), desc.end(), '\n', ' ');
        std::ostringstream oss;
        oss.imbue(std::locale::classic());
        oss << params[i].name << '\t' << desc << '\t' << params[i].initial << '\t'
            << params[i].minimum << '\t' << params[i].maximum << '\t' << params[i].step;
        jstring jstr = env->NewStringUTF(oss.str().c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(i), jstr);
        env->DeleteLocalRef(jstr);
    }
    return out;
}

JNIEXPORT jobjectArray JNICALL
Java_com_storm3ds_emulator_nativebridge_NativeLibrary_consumeLibrashaderDiagnostics(JNIEnv* env,
                                                                                    jobject) {
    const auto records = VideoCore::StormLibrashader::ConsumeDiagnostics();
    jclass str_class = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(records.size()), str_class, nullptr);
    if (!out) {
        return nullptr;
    }
    for (std::size_t i = 0; i < records.size(); ++i) {
        jstring jstr = env->NewStringUTF(records[i].c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(i), jstr);
        env->DeleteLocalRef(jstr);
    }
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_storm3ds_emulator_nativebridge_NativeLibrary_isLibrashaderAvailable(JNIEnv*, jobject) {
    return VideoCore::StormLibrashader::IsLibraryAvailable() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setSpeedhacks(
    JNIEnv*, jobject, jboolean fmv_hack, jboolean skip_slow_draw, jboolean skip_texture_copy,
    jboolean accurate_mul, jboolean audio_stretching, jboolean separable_shader) {
    Settings::SetFMVHack(fmv_hack == JNI_TRUE);
    Settings::values.skip_slow_draw.SetValue(skip_slow_draw == JNI_TRUE);
    Settings::values.skip_texture_copy.SetValue(skip_texture_copy == JNI_TRUE);
    Settings::values.shaders_accurate_mul.SetValue(accurate_mul == JNI_TRUE);
    Settings::values.enable_audio_stretching.SetValue(audio_stretching == JNI_TRUE);
    Settings::values.use_separable_shader.SetValue(separable_shader == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setSpirvOptimization(
    JNIEnv*, jobject, jint mode, jboolean validation, jboolean legalization) {
    Settings::values.optimize_spirv_output.SetValue(static_cast<Settings::OptimizeSpirv>(mode));
    Settings::values.spirv_output_validation.SetValue(validation == JNI_TRUE);
    Settings::values.spirv_output_legalization.SetValue(legalization == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setStormJitOptions(
    JNIEnv*, jobject, jint jit_mode, jint code_cache_mb) {
    Settings::values.storm_jit_mode = static_cast<u32>(std::clamp<int>(jit_mode, 0, 3));
    Settings::values.storm_jit_cache_mb =
        code_cache_mb <= 0 ? 0u : static_cast<u32>(std::clamp<int>(code_cache_mb, 32, 256));
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setCustomScreenLayout(
    JNIEnv*, jobject, jboolean is_portrait, jint top_x, jint top_y, jint top_w, jint top_h,
    jint bot_x, jint bot_y, jint bot_w, jint bot_h, jint second_layer_opacity) {
    const int opacity = std::clamp(static_cast<int>(second_layer_opacity), 0, 100);
    const int tx = std::max(0, static_cast<int>(top_x));
    const int ty = std::max(0, static_cast<int>(top_y));
    const int tw = std::max(0, static_cast<int>(top_w));
    const int th = std::max(0, static_cast<int>(top_h));
    const int bx = std::max(0, static_cast<int>(bot_x));
    const int by = std::max(0, static_cast<int>(bot_y));
    const int bw = std::max(0, static_cast<int>(bot_w));
    const int bh = std::max(0, static_cast<int>(bot_h));

    Settings::values.swap_screen.SetValue(false);
    Settings::values.storm_custom_layout_enabled.SetValue(true);
    Settings::values.custom_second_layer_opacity.SetValue(static_cast<u16>(opacity));
    Config::AddDefaultSettingValue("swap_screen", "0");
    Config::AddDefaultSettingValue("custom_second_layer_opacity", std::to_string(opacity));

    if (is_portrait) {
        Settings::values.portrait_layout_option.SetValue(
            Settings::PortraitLayoutOption::PortraitCustomLayout);
        Settings::values.custom_portrait_top_x.SetValue(static_cast<u16>(tx));
        Settings::values.custom_portrait_top_y.SetValue(static_cast<u16>(ty));
        Settings::values.custom_portrait_top_width.SetValue(static_cast<u16>(tw));
        Settings::values.custom_portrait_top_height.SetValue(static_cast<u16>(th));
        Settings::values.custom_portrait_bottom_x.SetValue(static_cast<u16>(bx));
        Settings::values.custom_portrait_bottom_y.SetValue(static_cast<u16>(by));
        Settings::values.custom_portrait_bottom_width.SetValue(static_cast<u16>(bw));
        Settings::values.custom_portrait_bottom_height.SetValue(static_cast<u16>(bh));
        Config::AddDefaultSettingValue(
            "portrait_layout_option",
            std::to_string(static_cast<int>(Settings::PortraitLayoutOption::PortraitCustomLayout)));
        Config::AddDefaultSettingValue("custom_portrait_top_x", std::to_string(tx));
        Config::AddDefaultSettingValue("custom_portrait_top_y", std::to_string(ty));
        Config::AddDefaultSettingValue("custom_portrait_top_width", std::to_string(tw));
        Config::AddDefaultSettingValue("custom_portrait_top_height", std::to_string(th));
        Config::AddDefaultSettingValue("custom_portrait_bottom_x", std::to_string(bx));
        Config::AddDefaultSettingValue("custom_portrait_bottom_y", std::to_string(by));
        Config::AddDefaultSettingValue("custom_portrait_bottom_width", std::to_string(bw));
        Config::AddDefaultSettingValue("custom_portrait_bottom_height", std::to_string(bh));
    } else {
        Settings::values.layout_option.SetValue(Settings::LayoutOption::CustomLayout);
        Settings::values.custom_top_x.SetValue(static_cast<u16>(tx));
        Settings::values.custom_top_y.SetValue(static_cast<u16>(ty));
        Settings::values.custom_top_width.SetValue(static_cast<u16>(tw));
        Settings::values.custom_top_height.SetValue(static_cast<u16>(th));
        Settings::values.custom_bottom_x.SetValue(static_cast<u16>(bx));
        Settings::values.custom_bottom_y.SetValue(static_cast<u16>(by));
        Settings::values.custom_bottom_width.SetValue(static_cast<u16>(bw));
        Settings::values.custom_bottom_height.SetValue(static_cast<u16>(bh));
        Config::AddDefaultSettingValue(
            "layout_option", std::to_string(static_cast<int>(Settings::LayoutOption::CustomLayout)));
        Config::AddDefaultSettingValue("custom_top_x", std::to_string(tx));
        Config::AddDefaultSettingValue("custom_top_y", std::to_string(ty));
        Config::AddDefaultSettingValue("custom_top_width", std::to_string(tw));
        Config::AddDefaultSettingValue("custom_top_height", std::to_string(th));
        Config::AddDefaultSettingValue("custom_bottom_x", std::to_string(bx));
        Config::AddDefaultSettingValue("custom_bottom_y", std::to_string(by));
        Config::AddDefaultSettingValue("custom_bottom_width", std::to_string(bw));
        Config::AddDefaultSettingValue("custom_bottom_height", std::to_string(bh));
    }
    std::scoped_lock lock{StormBridge::GetSurfaceMutex()};
    if (auto* w = StormBridge::GetPrimaryWindow()) {
        w->RefreshFramebufferLayout();
    }
}

JNIEXPORT jint JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_getAesKeyStatusMask(
    JNIEnv*, jobject) {
    HW::AES::InitKeys();
    int mask = 0;
    const std::string sysdir = FileUtil::GetUserPath(FileUtil::UserPath::SysDataDir);
    if (FileUtil::Exists(sysdir + BOOTROM9)) mask |= (1 << 0);
    if (FileUtil::Exists(sysdir + KEYS_FILE)) mask |= (1 << 1);
    if (FileUtil::Exists(sysdir + AES_KEYS)) mask |= (1 << 2);
    if (FileUtil::Exists(sysdir + "seeddb.bin")) mask |= (1 << 3);
    if (HW::AES::IsNormalKeyAvailable(HW::AES::KeySlotID::NCCHSecure1) ||
        HW::AES::IsKeyXAvailable(HW::AES::KeySlotID::NCCHSecure1)) mask |= (1 << 4);
    if (HW::UniqueData::IsFullConsoleLinked()) mask |= (1 << 5);
    return static_cast<jint>(mask);
}

JNIEXPORT jint JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_createBundleNative(
    JNIEnv* env, jobject, jstring j_out_path, jstring j_main_file, jobjectArray j_deps) {
    const std::string out_path = GetJString(env, j_out_path);
    const std::string main_file = GetJString(env, j_main_file);
    std::vector<std::string> deps;
    if (j_deps != nullptr) {
        const jsize len = env->GetArrayLength(j_deps);
        deps.reserve(len);
        for (jsize i = 0; i < len; ++i) {
            auto jstr = static_cast<jstring>(env->GetObjectArrayElement(j_deps, i));
            if (jstr) {
                deps.push_back(GetJString(env, jstr));
                env->DeleteLocalRef(jstr);
            }
        }
    }
    const auto res = Loader::CreateBundleFile(out_path, main_file, deps, nullptr);
    return static_cast<jint>(res);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setLogFilterAndRegex(
    JNIEnv* env, jobject, jstring j_filter, jstring j_regex) {
    const std::string filter_str = GetJString(env, j_filter);
    const std::string regex_str = GetJString(env, j_regex);
    Settings::values.log_filter.SetValue(filter_str);
    Settings::values.log_regex_filter.SetValue(regex_str);
    Common::Log::Filter filter;
    filter.ParseFilterString(filter_str);
    Common::Log::SetGlobalFilter(filter);
    Common::Log::SetRegexFilter(regex_str);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setMicrophoneEnabled(
    JNIEnv*, jobject, jboolean enabled) {
    Settings::values.input_type.SetValue(
        enabled == JNI_TRUE ? AudioCore::InputType::Auto : AudioCore::InputType::Null);
    auto& system = Core::System::GetInstance();
    if (system.IsPoweredOn()) {
        system.ApplySettings();
    }
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setAudioMuted(
    JNIEnv*, jobject, jboolean muted) {
    Settings::values.audio_muted = (muted == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setMicrophoneInputType(
    JNIEnv*, jobject, jint input_type) {
    AudioCore::InputType mapped = AudioCore::InputType::Null;
    if (input_type == 1) {
        mapped = AudioCore::InputType::Auto;
    } else if (input_type == 2) {
        mapped = AudioCore::InputType::Static;
    }
    Settings::values.input_type.SetValue(mapped);
    Config::AddDefaultSettingValue("input_type", std::to_string(static_cast<int>(mapped)));
    auto& system = Core::System::GetInstance();
    if (system.IsPoweredOn()) {
        system.ApplySettings();
    }
}

JNIEXPORT jint JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_installCiaSimple(
    JNIEnv* env, jobject, jstring j_path) {
    const std::string path = GetJString(env, j_path);
    const auto res = Service::AM::InstallCIA(path, [](std::size_t, std::size_t) {});
    return static_cast<jint>(res);
}

JNIEXPORT jlong JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_getCiaTitleId(
    JNIEnv* env, jobject, jstring j_path) {
    const std::string path = GetJString(env, j_path);
    if (!FileUtil::Exists(path)) {
        return 0;
    }
    std::unique_ptr<FileUtil::IOFileBase> in_file = std::make_unique<FileUtil::IOFile>(path, "rb");
    if (!in_file || !in_file->IsOpen()) {
        return 0;
    }
    if (auto tar = FileUtil::TarArchive::OpenTarArchive(in_file->OpenCopy())) {
        std::string picked;
        for (const auto& f : tar->GetFileList()) {
            const std::string lower_name = Common::ToLower(f.name);
            if (lower_name.ends_with(".cia") || lower_name.ends_with(".zcia")) {
                picked = f.name;
                if (lower_name.find("00040000") != std::string::npos) {
                    break;
                }
            }
        }
        if (picked.empty()) {
            return 0;
        }
        in_file = tar->OpenSubFile(picked);
        if (!in_file || !in_file->IsGood()) {
            return 0;
        }
    }
    if (FileUtil::Z3DSReadIOFile::GetUnderlyingFileMagic(in_file.get()).has_value()) {
        auto z3ds = std::make_unique<FileUtil::Z3DSReadIOFile>(std::move(in_file));
        if (auto ti = z3ds->Metadata().Get("titleinfo"); ti && ti->size() >= sizeof(u64)) {
            u64 tid = 0;
            std::memcpy(&tid, ti->data(), sizeof(u64));
            if (tid != 0) {
                return static_cast<jlong>(tid);
            }
        }
        in_file = std::move(z3ds);
    }
    FileSys::CIAContainer container;
    if (container.Load(in_file.get()) != Loader::ResultStatus::Success) {
        return 0;
    }
    return static_cast<jlong>(container.GetTitleMetadata().GetTitleID());
}

JNIEXPORT jstring JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_findInstalledTitlePath(
    JNIEnv* env, jobject, jlong j_title_id) {
    const u64 tid = static_cast<u64>(j_title_id);
    if (tid == 0) {
        return env->NewStringUTF("");
    }
    const Service::FS::MediaType primary = Service::AM::GetTitleMediaType(tid);
    const Service::FS::MediaType secondary = (primary == Service::FS::MediaType::SDMC)
                                                 ? Service::FS::MediaType::NAND
                                                 : Service::FS::MediaType::SDMC;
    for (const auto media : {primary, secondary}) {
        const std::string content_path = Service::AM::GetTitleContentPath(media, tid, 0, false);
        if (content_path.empty() || !FileUtil::Exists(content_path)) {
            continue;
        }
        auto loader = Loader::GetLoader(content_path);
        if (!loader) {
            continue;
        }
        bool executable = false;
        if (loader->IsExecutable(executable) == Loader::ResultStatus::Success && executable) {
            return env->NewStringUTF(content_path.c_str());
        }
    }
    return env->NewStringUTF("");
}

JNIEXPORT jboolean JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setAdrenoTurbo(
    JNIEnv*, jobject, jboolean enabled) {
#if defined(STORM_ENABLE_ADRENOTOOLS) && CITRA_ARCH(arm64)
    adrenotools_set_turbo(enabled == JNI_TRUE);
    return JNI_TRUE;
#else
    (void)enabled;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_areAllBciaTitlesInstalled(
    JNIEnv* env, jobject, jstring j_path) {
    const std::string path = GetJString(env, j_path);
    if (path.empty() || !FileUtil::Exists(path)) {
        return JNI_FALSE;
    }
    auto tar = FileUtil::TarArchive::OpenTarArchive(path);
    if (!tar) {
        return JNI_FALSE;
    }
    int cia_entries = 0;
    bool has_base_app = false;
    for (const auto& f : tar->GetFileList()) {
        const std::string lower_name = Common::ToLower(f.name);
        if (!lower_name.ends_with(".cia") && !lower_name.ends_with(".zcia")) {
            continue;
        }
        auto sub = tar->OpenSubFile(f.name);
        if (!sub || !sub->IsGood()) {
            return JNI_FALSE;
        }
        u64 tid = 0;
        if (FileUtil::Z3DSReadIOFile::GetUnderlyingFileMagic(sub.get()).has_value()) {
            auto z3ds = std::make_unique<FileUtil::Z3DSReadIOFile>(std::move(sub));
            if (auto ti = z3ds->Metadata().Get("titleinfo"); ti && ti->size() >= sizeof(u64)) {
                std::memcpy(&tid, ti->data(), sizeof(u64));
            }
            sub = std::move(z3ds);
        }
        if (tid == 0) {
            FileSys::CIAContainer container;
            if (container.Load(sub.get()) != Loader::ResultStatus::Success) {
                return JNI_FALSE;
            }
            tid = container.GetTitleMetadata().GetTitleID();
        }
        if (tid == 0) {
            return JNI_FALSE;
        }
        ++cia_entries;
        if ((tid >> 32) == 0x00040000ULL) {
            has_base_app = true;
        }
        const auto media = Service::AM::GetTitleMediaType(tid);
        const std::string content_path = Service::AM::GetTitleContentPath(media, tid, 0, false);
        if (content_path.empty() || !FileUtil::Exists(content_path)) {
            return JNI_FALSE;
        }
    }
    return (cia_entries > 0 && has_base_app) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
