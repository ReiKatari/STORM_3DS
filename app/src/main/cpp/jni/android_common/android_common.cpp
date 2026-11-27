// Copyright 2022-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include "jni/android_common/android_common.h"

#include <string>
#include <string_view>

#include <jni.h>

#include "common/string_util.h"

std::string GetJString(JNIEnv* env, jstring jstr) {
    if (!jstr) {
        return {};
    }

    const jchar* jchars = env->GetStringChars(jstr, nullptr);
    const jsize length = env->GetStringLength(jstr);
    const std::u16string_view string_view(reinterpret_cast<const char16_t*>(jchars), length);
    const std::string converted_string = Common::UTF16ToUTF8(string_view);
    env->ReleaseStringChars(jstr, jchars);

    return converted_string;
}

jstring ToJString(JNIEnv* env, std::string_view str) {
    const std::u16string converted_string = Common::UTF8ToUTF16(str);
    return env->NewString(reinterpret_cast<const jchar*>(converted_string.data()),
                          static_cast<jint>(converted_string.size()));
}

jobjectArray ToJStringArray(JNIEnv* env, const std::vector<std::string>& strs) {
    jclass str_class = env->FindClass("java/lang/String");
    jstring empty_str = env->NewStringUTF("");
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(strs.size()), str_class, empty_str);
    if (empty_str) env->DeleteLocalRef(empty_str);
    if (str_class) env->DeleteLocalRef(str_class);
    if (!array) {
        return nullptr;
    }
    for (std::size_t i = 0; i < strs.size(); ++i) {
        jstring elem = ToJString(env, strs[i]);
        env->SetObjectArrayElement(array, static_cast<jsize>(i), elem);
        if (elem) env->DeleteLocalRef(elem);
    }
    return array;
}
