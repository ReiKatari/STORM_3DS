// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>
#include <jni.h>
#include "common/android_utils.h"
#include "common/common_paths.h"
#include "common/file_util.h"
#include "common/logging/backend.h"
#include "common/settings.h"
#include "audio_core/input_details.h"
#include "core/core.h"
#include "core/hle/service/am/am.h"
#include "core/file_sys/seed_db.h"
#include "core/hw/aes/key.h"
#include "core/hw/unique_data.h"
#include "core/loader/loader.h"
#include "jni/android_common/android_common.h"
#include "jni/emu_window/emu_window.h"
#include "video_core/gpu.h"
#include "video_core/renderer_base.h"

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
    Settings::values.render_3d.SetValue(static_cast<Settings::StereoRenderOption>(mode));
    auto& system = Core::System::GetInstance();
    if (system.IsPoweredOn()) {
        system.GPU().Renderer().NotifySurfaceChanged(false);
    }
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setDisableRightEyeRender(
    JNIEnv*, jobject, jboolean enabled) {
    Settings::values.disable_right_eye_render.SetValue(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setLibrashaderPreset(
    JNIEnv* env, jobject, jboolean enabled, jstring j_preset_path) {
    Settings::values.librashader_enabled.SetValue(enabled == JNI_TRUE);
    Settings::values.librashader_preset_path.SetValue(GetJString(env, j_preset_path));
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

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setCustomScreenLayout(
    JNIEnv*, jobject, jboolean is_portrait, jint top_x, jint top_y, jint top_w, jint top_h,
    jint bot_x, jint bot_y, jint bot_w, jint bot_h, jint second_layer_opacity) {
    Settings::values.storm_custom_layout_enabled.SetValue(true);
    Settings::values.custom_second_layer_opacity.SetValue(
        static_cast<u16>(std::clamp(static_cast<int>(second_layer_opacity), 0, 100)));
    if (is_portrait) {
        Settings::values.portrait_layout_option.SetValue(
            Settings::PortraitLayoutOption::PortraitCustomLayout);
        Settings::values.custom_portrait_top_x.SetValue(static_cast<u16>(std::max(0, (int)top_x)));
        Settings::values.custom_portrait_top_y.SetValue(static_cast<u16>(std::max(0, (int)top_y)));
        Settings::values.custom_portrait_top_width.SetValue(static_cast<u16>(std::max(0, (int)top_w)));
        Settings::values.custom_portrait_top_height.SetValue(static_cast<u16>(std::max(0, (int)top_h)));
        Settings::values.custom_portrait_bottom_x.SetValue(static_cast<u16>(std::max(0, (int)bot_x)));
        Settings::values.custom_portrait_bottom_y.SetValue(static_cast<u16>(std::max(0, (int)bot_y)));
        Settings::values.custom_portrait_bottom_width.SetValue(static_cast<u16>(std::max(0, (int)bot_w)));
        Settings::values.custom_portrait_bottom_height.SetValue(static_cast<u16>(std::max(0, (int)bot_h)));
    } else {
        Settings::values.layout_option.SetValue(Settings::LayoutOption::CustomLayout);
        Settings::values.custom_top_x.SetValue(static_cast<u16>(std::max(0, (int)top_x)));
        Settings::values.custom_top_y.SetValue(static_cast<u16>(std::max(0, (int)top_y)));
        Settings::values.custom_top_width.SetValue(static_cast<u16>(std::max(0, (int)top_w)));
        Settings::values.custom_top_height.SetValue(static_cast<u16>(std::max(0, (int)top_h)));
        Settings::values.custom_bottom_x.SetValue(static_cast<u16>(std::max(0, (int)bot_x)));
        Settings::values.custom_bottom_y.SetValue(static_cast<u16>(std::max(0, (int)bot_y)));
        Settings::values.custom_bottom_width.SetValue(static_cast<u16>(std::max(0, (int)bot_w)));
        Settings::values.custom_bottom_height.SetValue(static_cast<u16>(std::max(0, (int)bot_h)));
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

JNIEXPORT jint JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_installCiaSimple(
    JNIEnv* env, jobject, jstring j_path) {
    const std::string path = GetJString(env, j_path);
    const auto res = Service::AM::InstallCIA(path, [](std::size_t, std::size_t) {});
    return static_cast<jint>(res);
}

} // extern "C"
