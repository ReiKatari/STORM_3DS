// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#include <chrono>
#include <deque>
#include <functional>
#include <mutex>
#include <vector>
#include <jni.h>
#include "common/common_types.h"
#include "core/core.h"

namespace StormBridge {
bool ExecuteOnEmuThread(std::function<bool(Core::System&)> fn);
} // namespace StormBridge

namespace {

struct RewindSnapshot {
    std::vector<u8> cst_zstd_data;
    jlong timestamp_ms = 0;
};

std::mutex g_rewind_mutex;
std::deque<RewindSnapshot> g_rewind_ring;
std::size_t g_rewind_total_bytes = 0;
std::size_t g_rewind_budget_bytes = 128ULL * 1024ULL * 1024ULL; // 128 MB default cap

void TrimToBudgetLocked() {
    while (g_rewind_total_bytes > g_rewind_budget_bytes && g_rewind_ring.size() > 1) {
        g_rewind_total_bytes -= g_rewind_ring.front().cst_zstd_data.size();
        g_rewind_ring.pop_front();
    }
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_setRewindBudgetMb(
    JNIEnv*, jobject, jint budget_mb) {
    std::scoped_lock lock{g_rewind_mutex};
    const std::size_t clamped = static_cast<std::size_t>(std::max(16, static_cast<int>(budget_mb)));
    g_rewind_budget_bytes = clamped * 1024ULL * 1024ULL;
    TrimToBudgetLocked();
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_clearRewindBuffer(
    JNIEnv*, jobject) {
    std::scoped_lock lock{g_rewind_mutex};
    g_rewind_ring.clear();
    g_rewind_total_bytes = 0;
}

JNIEXPORT jboolean JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_captureRewindSnapshot(
    JNIEnv*, jobject) {
    std::vector<u8> buf;
    const bool captured = StormBridge::ExecuteOnEmuThread([&buf](Core::System& sys) {
        if (!sys.IsPoweredOn()) {
            return false;
        }
        try {
            buf = sys.SaveStateBuffer();
            return !buf.empty();
        } catch (...) {
            buf.clear();
            return false;
        }
    });
    if (!captured || buf.empty()) {
        return JNI_FALSE;
    }
    const auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::system_clock::now().time_since_epoch())
                            .count();
    std::scoped_lock lock{g_rewind_mutex};
    const std::size_t sz = buf.size();
    if (sz > g_rewind_budget_bytes) {
        return JNI_FALSE;
    }
    g_rewind_total_bytes += sz;
    g_rewind_ring.push_back(RewindSnapshot{std::move(buf), static_cast<jlong>(now_ms)});
    TrimToBudgetLocked();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_rewindStepsBack(
    JNIEnv*, jobject, jint steps_back) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) {
        return JNI_FALSE;
    }
    std::vector<u8> target_buf;
    {
        std::scoped_lock lock{g_rewind_mutex};
        if (g_rewind_ring.empty()) {
            return JNI_FALSE;
        }
        const std::size_t back =
            std::min<std::size_t>(std::max(1, static_cast<int>(steps_back)), g_rewind_ring.size());
        const std::size_t target_idx = g_rewind_ring.size() - back;
        target_buf = g_rewind_ring[target_idx].cst_zstd_data;
        // Pop future snapshots beyond the rewound point
        while (g_rewind_ring.size() > target_idx + 1) {
            g_rewind_total_bytes -= g_rewind_ring.back().cst_zstd_data.size();
            g_rewind_ring.pop_back();
        }
    }
    return StormBridge::ExecuteOnEmuThread([buf = std::move(target_buf)](Core::System& sys) mutable {
        if (!sys.IsPoweredOn()) {
            return false;
        }
        try {
            return sys.LoadStateBuffer(std::move(buf));
        } catch (...) {
            return false;
        }
    }) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_getRewindTimelineTimestamps(
    JNIEnv* env, jobject) {
    std::scoped_lock lock{g_rewind_mutex};
    const jsize n = static_cast<jsize>(g_rewind_ring.size());
    jlongArray arr = env->NewLongArray(n);
    if (arr && n > 0) {
        std::vector<jlong> ts;
        ts.reserve(n);
        for (const auto& entry : g_rewind_ring) {
            ts.push_back(entry.timestamp_ms);
        }
        env->SetLongArrayRegion(arr, 0, n, ts.data());
    }
    return arr;
}

JNIEXPORT jlong JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_getRewindMemoryBytes(
    JNIEnv*, jobject) {
    std::scoped_lock lock{g_rewind_mutex};
    return static_cast<jlong>(g_rewind_total_bytes);
}

} // extern "C"
