// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#include <array>
#include <cstring>
#include <vector>
#include <jni.h>
#include "common/common_types.h"
#include "core/core.h"
#include "core/memory.h"

namespace {

constexpr u32 SEARCH_TYPE_SPECIFIED_VALUE = 0;
constexpr u32 SEARCH_TYPE_UNKNOWN_SEARCH = 1;

constexpr u32 VALUE_TYPE_FOUR_BYTES = 0;
constexpr u32 VALUE_TYPE_TWO_BYTES = 1;
constexpr u32 VALUE_TYPE_ONE_BYTE = 2;

bool EqualTo(u32 lhs, u32 rhs) { return lhs == rhs; }
bool NotEqualTo(u32 lhs, u32 rhs) { return lhs != rhs; }
bool BiggerThan(u32 lhs, u32 rhs) { return lhs > rhs; }
bool BiggerOrEqual(u32 lhs, u32 rhs) { return lhs >= rhs; }
bool SmallerThan(u32 lhs, u32 rhs) { return lhs < rhs; }
bool SmallerOrEqual(u32 lhs, u32 rhs) { return lhs <= rhs; }

using ScanHandler = bool (*)(u32, u32);
constexpr std::array<ScanHandler, 6> kScanHandlers = {
    &EqualTo, &NotEqualTo, &BiggerThan, &BiggerOrEqual, &SmallerThan, &SmallerOrEqual,
};

struct SearchSession {
    bool initialized = false;
    u32 start_addr = 0;
    u32 stop_addr = 0;
    u32 value_type = 0;
    u32 search_type = 0;
    u32 scan_type = 0;
    u32 value = 0;
    std::vector<u32> results;
    std::vector<u8*> pointers;

    SearchSession() : pointers(Memory::PAGE_TABLE_NUM_ENTRIES, nullptr) {}
    ~SearchSession() { reset(); }

    void reset() {
        initialized = false;
        start_addr = 0;
        stop_addr = 0;
        value_type = 0;
        search_type = 0;
        scan_type = 0;
        value = 0;
        results.clear();
        for (auto*& ptr : pointers) {
            if (ptr != nullptr) {
                delete[] ptr;
                ptr = nullptr;
            }
        }
    }
};

SearchSession g_search_session;

template <typename T>
void SearchInResults(const SearchSession& session, std::vector<u32>& out) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) return;
    auto pagetable = system.Memory().GetCurrentPageTable();
    if (!pagetable) return;
    const ScanHandler fn = kScanHandlers[session.scan_type % kScanHandlers.size()];

    for (std::size_t i = 0; i + 1 < session.results.size(); i += 2) {
        const u32 addr = session.results[i];
        if (addr < session.start_addr || addr > session.stop_addr) continue;
        const u32 page_index = addr >> Memory::CITRA_PAGE_BITS;
        const u32 page_offset = addr & Memory::CITRA_PAGE_MASK;
        if (page_index >= Memory::PAGE_TABLE_NUM_ENTRIES ||
            page_offset + sizeof(T) > Memory::CITRA_PAGE_SIZE) {
            continue;
        }
        const u8* p = static_cast<u8*>(pagetable->pointers[page_index]);
        if (p != nullptr) {
            T val{};
            std::memcpy(&val, p + page_offset, sizeof(T));
            const u32 new_value = static_cast<u32>(val);
            if (fn(new_value, session.value)) {
                out.push_back(addr);
                out.push_back(new_value);
            }
        }
    }
}

template <typename T>
void SearchMemory(SearchSession& session) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) return;
    auto pagetable = system.Memory().GetCurrentPageTable();
    if (!pagetable) return;
    const ScanHandler fn = kScanHandlers[session.scan_type % kScanHandlers.size()];

    const u32 startPage = session.start_addr >> Memory::CITRA_PAGE_BITS;
    u32 startOffset = session.start_addr & Memory::CITRA_PAGE_MASK;
    const u32 stopPage = std::min<u32>(session.stop_addr >> Memory::CITRA_PAGE_BITS,
                                      static_cast<u32>(Memory::PAGE_TABLE_NUM_ENTRIES - 1));
    const u32 stopOffset = session.stop_addr & Memory::CITRA_PAGE_MASK;

    constexpr std::size_t kMaxResults = 200000;
    for (u32 i = startPage; i <= stopPage && session.results.size() < kMaxResults * 2; ++i) {
        const u8* base_ptr = static_cast<u8*>(pagetable->pointers[i]);
        if (base_ptr != nullptr) {
            const u32 endOff = (i == stopPage)
                                   ? std::min<u32>(stopOffset, Memory::CITRA_PAGE_SIZE)
                                   : Memory::CITRA_PAGE_SIZE;
            for (u32 off = startOffset; off + sizeof(T) <= endOff; off += sizeof(T)) {
                T val{};
                std::memcpy(&val, base_ptr + off, sizeof(T));
                const u32 uval = static_cast<u32>(val);
                if (fn(uval, session.value)) {
                    session.results.push_back((i << Memory::CITRA_PAGE_BITS) + off);
                    session.results.push_back(uval);
                    if (session.results.size() >= kMaxResults * 2) break;
                }
            }
        }
        startOffset = 0;
    }
}

template <typename T>
void CompareInResults(const SearchSession& session, std::vector<u32>& out) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) return;
    auto pagetable = system.Memory().GetCurrentPageTable();
    if (!pagetable) return;
    const ScanHandler fn = kScanHandlers[session.scan_type % kScanHandlers.size()];

    for (std::size_t i = 0; i + 1 < session.results.size(); i += 2) {
        const u32 addr = session.results[i];
        if (addr < session.start_addr || addr > session.stop_addr) continue;
        const u32 old_value = session.results[i + 1];
        const u32 page_index = addr >> Memory::CITRA_PAGE_BITS;
        const u32 page_offset = addr & Memory::CITRA_PAGE_MASK;
        if (page_index >= Memory::PAGE_TABLE_NUM_ENTRIES ||
            page_offset + sizeof(T) > Memory::CITRA_PAGE_SIZE) {
            continue;
        }
        const u8* p = static_cast<u8*>(pagetable->pointers[page_index]);
        if (p != nullptr) {
            T val{};
            std::memcpy(&val, p + page_offset, sizeof(T));
            const u32 new_value = static_cast<u32>(val);
            if (fn(new_value, old_value)) {
                out.push_back(addr);
                out.push_back(new_value);
            }
        }
    }
}

template <typename T>
void CompareMemory(SearchSession& session) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) return;
    auto pagetable = system.Memory().GetCurrentPageTable();
    if (!pagetable) return;
    const ScanHandler fn = kScanHandlers[session.scan_type % kScanHandlers.size()];

    const u32 startPage = session.start_addr >> Memory::CITRA_PAGE_BITS;
    const u32 stopPage = std::min<u32>(session.stop_addr >> Memory::CITRA_PAGE_BITS,
                                      static_cast<u32>(Memory::PAGE_TABLE_NUM_ENTRIES - 1));
    constexpr std::size_t kMaxResults = 200000;

    for (u32 i = startPage; i <= stopPage; ++i) {
        const u8* p = static_cast<u8*>(pagetable->pointers[i]);
        u8* q = session.pointers[i];
        if (p != nullptr) {
            if (q == nullptr) {
                q = new u8[Memory::CITRA_PAGE_SIZE];
                std::memcpy(q, p, Memory::CITRA_PAGE_SIZE);
                session.pointers[i] = q;
            } else if (session.results.size() < kMaxResults * 2) {
                for (u32 j = 0; j + sizeof(T) <= Memory::CITRA_PAGE_SIZE; j += sizeof(T)) {
                    T cur{}, prev{};
                    std::memcpy(&cur, p + j, sizeof(T));
                    std::memcpy(&prev, q + j, sizeof(T));
                    if (fn(static_cast<u32>(cur), static_cast<u32>(prev))) {
                        session.results.push_back((i << Memory::CITRA_PAGE_BITS) + j);
                        session.results.push_back(static_cast<u32>(cur));
                        if (session.results.size() >= kMaxResults * 2) break;
                    }
                }
                std::memcpy(q, p, Memory::CITRA_PAGE_SIZE);
            }
        }
    }
}

jintArray WrapResults(JNIEnv* env, const std::vector<u32>& vals) {
    const jsize count = static_cast<jsize>(std::min<std::size_t>(vals.size(), 4000));
    jintArray arr = env->NewIntArray(count);
    if (arr && count > 0) {
        env->SetIntArrayRegion(arr, 0, count, reinterpret_cast<const jint*>(vals.data()));
    }
    return arr;
}

} // namespace

extern "C" {

JNIEXPORT jintArray JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_searchMemoryRegion(
    JNIEnv* env, jobject, jlong start_addr, jlong stop_addr, jint value_type, jint search_type,
    jint scan_type, jlong value) {
    const u32 s = static_cast<u32>(start_addr);
    const u32 e = static_cast<u32>(stop_addr);
    if (s > e) return WrapResults(env, {});

    if (g_search_session.search_type != static_cast<u32>(search_type) ||
        g_search_session.value_type != static_cast<u32>(value_type) ||
        g_search_session.start_addr > s || g_search_session.stop_addr < e) {
        g_search_session.reset();
    }

    g_search_session.start_addr = s;
    g_search_session.stop_addr = e;
    g_search_session.value_type = static_cast<u32>(value_type);
    g_search_session.search_type = static_cast<u32>(search_type);
    g_search_session.scan_type = static_cast<u32>(scan_type);
    g_search_session.value = static_cast<u32>(value);

    if (g_search_session.search_type == SEARCH_TYPE_SPECIFIED_VALUE) {
        if (!g_search_session.initialized) {
            if (g_search_session.value_type == VALUE_TYPE_FOUR_BYTES)
                SearchMemory<u32>(g_search_session);
            else if (g_search_session.value_type == VALUE_TYPE_TWO_BYTES)
                SearchMemory<u16>(g_search_session);
            else
                SearchMemory<u8>(g_search_session);
            g_search_session.initialized = !g_search_session.results.empty();
        } else if (!g_search_session.results.empty()) {
            std::vector<u32> next;
            if (g_search_session.value_type == VALUE_TYPE_FOUR_BYTES)
                SearchInResults<u32>(g_search_session, next);
            else if (g_search_session.value_type == VALUE_TYPE_TWO_BYTES)
                SearchInResults<u16>(g_search_session, next);
            else
                SearchInResults<u8>(g_search_session, next);
            g_search_session.results.swap(next);
        }
    } else if (g_search_session.search_type == SEARCH_TYPE_UNKNOWN_SEARCH) {
        if (!g_search_session.initialized) {
            if (g_search_session.value_type == VALUE_TYPE_FOUR_BYTES)
                CompareMemory<u32>(g_search_session);
            else if (g_search_session.value_type == VALUE_TYPE_TWO_BYTES)
                CompareMemory<u16>(g_search_session);
            else
                CompareMemory<u8>(g_search_session);
            g_search_session.initialized = true;
        } else {
            std::vector<u32> next;
            if (g_search_session.results.empty()) {
                if (g_search_session.value_type == VALUE_TYPE_FOUR_BYTES)
                    CompareMemory<u32>(g_search_session);
                else if (g_search_session.value_type == VALUE_TYPE_TWO_BYTES)
                    CompareMemory<u16>(g_search_session);
                else
                    CompareMemory<u8>(g_search_session);
            } else {
                if (g_search_session.value_type == VALUE_TYPE_FOUR_BYTES)
                    CompareInResults<u32>(g_search_session, next);
                else if (g_search_session.value_type == VALUE_TYPE_TWO_BYTES)
                    CompareInResults<u16>(g_search_session, next);
                else
                    CompareInResults<u8>(g_search_session, next);
                g_search_session.results.swap(next);
            }
        }
    }
    return WrapResults(env, g_search_session.results);
}

JNIEXPORT jintArray JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_getSearchResults(
    JNIEnv* env, jobject) {
    return WrapResults(env, g_search_session.results);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_resetSearchResults(
    JNIEnv*, jobject) {
    g_search_session.reset();
}

JNIEXPORT jlong JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_readMemoryValue(
    JNIEnv*, jobject, jlong vaddr, jint size_bytes) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) return 0;
    const VAddr addr = static_cast<VAddr>(vaddr);
    auto& mem = system.Memory();
    if (size_bytes == 1) return mem.Read8(addr);
    if (size_bytes == 2) return mem.Read16(addr);
    return mem.Read32(addr);
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_NativeLibrary_writeMemoryValue(
    JNIEnv*, jobject, jlong vaddr, jint size_bytes, jlong value) {
    auto& system = Core::System::GetInstance();
    if (!system.IsPoweredOn()) return;
    const VAddr addr = static_cast<VAddr>(vaddr);
    auto& mem = system.Memory();
    if (size_bytes == 1) {
        mem.Write8(addr, static_cast<u8>(value));
    } else if (size_bytes == 2) {
        mem.Write16(addr, static_cast<u16>(value));
    } else {
        mem.Write32(addr, static_cast<u32>(value));
    }
}

} // extern "C"
