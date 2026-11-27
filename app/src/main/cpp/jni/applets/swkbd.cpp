// Copyright 2020-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <map>

#include <jni.h>

#include "core/core.h"
#include "jni/android_common/android_common.h"
#include "jni/applets/swkbd.h"
#include "jni/id_cache.h"

static jclass s_software_keyboard_class;
static jclass s_keyboard_config_class;
static jclass s_keyboard_data_class;
static jclass s_validation_error_class;
static jmethodID s_swkbd_execute;
static jmethodID s_swkbd_show_error;

namespace SoftwareKeyboard {

static jobject ToJavaKeyboardConfig(const Frontend::KeyboardConfig& config) {
    JNIEnv* env = IDCache::GetEnvForThread();
    jobject object = env->AllocObject(s_keyboard_config_class);
    env->SetIntField(object, env->GetFieldID(s_keyboard_config_class, "buttonConfig", "I"),
                     static_cast<jint>(config.button_config));
    env->SetIntField(object, env->GetFieldID(s_keyboard_config_class, "maxTextLength", "I"),
                     static_cast<jint>(config.max_text_length));
    env->SetBooleanField(object, env->GetFieldID(s_keyboard_config_class, "multilineMode", "Z"),
                         static_cast<jboolean>(config.multiline_mode));
    jstring jhint = ToJString(env, config.hint_text);
    env->SetObjectField(object,
                        env->GetFieldID(s_keyboard_config_class, "hintText", "Ljava/lang/String;"),
                        jhint);
    if (jhint) env->DeleteLocalRef(jhint);

    const jclass string_class = reinterpret_cast<jclass>(env->FindClass("java/lang/String"));
    jstring initial_btn = config.button_text.empty() ? env->NewStringUTF("") : ToJString(env, config.button_text[0]);
    const jobjectArray array =
        env->NewObjectArray(static_cast<jsize>(config.button_text.size()), string_class, initial_btn);
    if (initial_btn) env->DeleteLocalRef(initial_btn);
    if (string_class) env->DeleteLocalRef(string_class);
    for (std::size_t i = 1; i < config.button_text.size(); ++i) {
        jstring btn = ToJString(env, config.button_text[i]);
        env->SetObjectArrayElement(array, static_cast<jsize>(i), btn);
        if (btn) env->DeleteLocalRef(btn);
    }
    env->SetObjectField(
        object, env->GetFieldID(s_keyboard_config_class, "buttonText", "[Ljava/lang/String;"),
        array);
    if (array) env->DeleteLocalRef(array);

    return object;
}

static Frontend::KeyboardData ToFrontendKeyboardData(jobject object) {
    JNIEnv* env = IDCache::GetEnvForThread();
    const jstring string = reinterpret_cast<jstring>(env->GetObjectField(
        object, env->GetFieldID(s_keyboard_data_class, "text", "Ljava/lang/String;")));
    return Frontend::KeyboardData{
        GetJString(env, string),
        static_cast<u8>(
            env->GetIntField(object, env->GetFieldID(s_keyboard_data_class, "button", "I")))};
}

AndroidKeyboard::~AndroidKeyboard() = default;

void AndroidKeyboard::Execute(const Frontend::KeyboardConfig& config) {
    SoftwareKeyboard::Execute(config);

    JNIEnv* env = IDCache::GetEnvForThread();
    jobject jcfg = ToJavaKeyboardConfig(config);
    jobject jdata = env->CallStaticObjectMethod(s_software_keyboard_class, s_swkbd_execute, jcfg);
    if (jcfg) env->DeleteLocalRef(jcfg);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        Finalize("", 0);
        return;
    }
    const auto data = ToFrontendKeyboardData(jdata);
    if (jdata) env->DeleteLocalRef(jdata);
    Finalize(data.text, data.button);
}

void AndroidKeyboard::ShowError(const std::string& error) {
    JNIEnv* env = IDCache::GetEnvForThread();
    jstring jerr = ToJString(env, error);
    env->CallStaticVoidMethod(s_software_keyboard_class, s_swkbd_show_error, jerr);
    if (jerr) env->DeleteLocalRef(jerr);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

void InitJNI(JNIEnv* env) {
    s_software_keyboard_class = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("com/storm3ds/emulator/nativebridge/applets/SoftwareKeyboard")));
    s_keyboard_config_class = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/storm3ds/emulator/nativebridge/applets/SoftwareKeyboard$KeyboardConfig")));
    s_keyboard_data_class = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/storm3ds/emulator/nativebridge/applets/SoftwareKeyboard$KeyboardData")));
    s_validation_error_class = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/storm3ds/emulator/nativebridge/applets/SoftwareKeyboard$ValidationError")));

    s_swkbd_execute = env->GetStaticMethodID(
        s_software_keyboard_class, "execute",
        "(Lcom/storm3ds/emulator/nativebridge/applets/SoftwareKeyboard$KeyboardConfig;)Lcom/storm3ds/emulator/nativebridge/"
        "applets/SoftwareKeyboard$KeyboardData;");
    s_swkbd_show_error =
        env->GetStaticMethodID(s_software_keyboard_class, "showError", "(Ljava/lang/String;)V");
}

void CleanupJNI(JNIEnv* env) {
    env->DeleteGlobalRef(s_software_keyboard_class);
    env->DeleteGlobalRef(s_keyboard_config_class);
    env->DeleteGlobalRef(s_keyboard_data_class);
    env->DeleteGlobalRef(s_validation_error_class);
}

} // namespace SoftwareKeyboard

jobject ToJavaValidationError(Frontend::ValidationError error) {
    static const std::map<Frontend::ValidationError, const char*> ValidationErrorNameMap{{
        {Frontend::ValidationError::None, "None"},
        {Frontend::ValidationError::ButtonOutOfRange, "ButtonOutOfRange"},
        {Frontend::ValidationError::MaxDigitsExceeded, "MaxDigitsExceeded"},
        {Frontend::ValidationError::AtSignNotAllowed, "AtSignNotAllowed"},
        {Frontend::ValidationError::PercentNotAllowed, "PercentNotAllowed"},
        {Frontend::ValidationError::BackslashNotAllowed, "BackslashNotAllowed"},
        {Frontend::ValidationError::ProfanityNotAllowed, "ProfanityNotAllowed"},
        {Frontend::ValidationError::CallbackFailed, "CallbackFailed"},
        {Frontend::ValidationError::FixedLengthRequired, "FixedLengthRequired"},
        {Frontend::ValidationError::MaxLengthExceeded, "MaxLengthExceeded"},
        {Frontend::ValidationError::BlankInputNotAllowed, "BlankInputNotAllowed"},
        {Frontend::ValidationError::EmptyInputNotAllowed, "EmptyInputNotAllowed"},
    }};
    ASSERT(ValidationErrorNameMap.count(error));

    JNIEnv* env = IDCache::GetEnvForThread();
    return env->GetStaticObjectField(
        s_validation_error_class,
        env->GetStaticFieldID(s_validation_error_class, ValidationErrorNameMap.at(error),
                              "Lcom/storm3ds/emulator/nativebridge/applets/SoftwareKeyboard$ValidationError;"));
}

jobject Java_com_storm3ds_emulator_nativebridge_applets_SoftwareKeyboard_ValidateFilters(JNIEnv* env,
                                                                           jclass clazz,
                                                                           jstring text) {

    const auto ret =
        Core::System::GetInstance().GetSoftwareKeyboard()->ValidateFilters(GetJString(env, text));
    return ToJavaValidationError(ret);
}

jobject Java_com_storm3ds_emulator_nativebridge_applets_SoftwareKeyboard_ValidateInput(JNIEnv* env, jclass clazz,
                                                                         jstring text) {

    const auto ret =
        Core::System::GetInstance().GetSoftwareKeyboard()->ValidateInput(GetJString(env, text));
    return ToJavaValidationError(ret);
}
