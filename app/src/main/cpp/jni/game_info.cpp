// Copyright 2017-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

#include <cstring>
#include <map>
#include <memory>
#include <vector>

#include <array>
#include "common/file_derived.h"
#include "common/file_util.h"
#include "common/string_util.h"
#include "common/tar_file.h"
#include "core/file_sys/cia_container.h"
#include "core/hle/service/am/am.h"
#include "core/hle/service/fs/archive.h"
#include "core/loader/loader.h"
#include "core/loader/smdh.h"
#include "jni/android_common/android_common.h"
#include "jni/id_cache.h"

namespace {

static constexpr u64 UPDATE_TID_HIGH = 0x0004000e00000000;

struct GameInfoData {
    Loader::SMDH smdh;
    u64 title_id = 0;
    bool loaded = false;
    bool is_encrypted = false;
    std::string file_type = "";
    bool is_insertable = false;
};

GameInfoData* GetNewGameInfoData(const std::string& path) {
    std::unique_ptr<Loader::AppLoader> loader = Loader::GetLoader(path);
    u64 program_id = 0;
    bool is_encrypted = false;
    Loader::ResultStatus result{};
    if (loader) {
        result = loader->ReadProgramId(program_id);
        if (result == Loader::ResultStatus::ErrorNotImplemented) {
            // This can happen for 3DSX and ELF files.
            program_id = 0;
            result = Loader::ResultStatus::Success;
        }
    }

    if (!loader || result != Loader::ResultStatus::Success) {
        GameInfoData* gid = new GameInfoData();
        std::memset(&gid->smdh, 0, sizeof(Loader::SMDH));
        if (FileUtil::Exists(path)) {
            std::unique_ptr<FileUtil::IOFileBase> in_file =
                std::make_unique<FileUtil::IOFile>(path, "rb");
            if (in_file && in_file->IsOpen()) {
                bool is_bundle = false;
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
                    if (!picked.empty()) {
                        in_file = tar->OpenSubFile(picked);
                        is_bundle = true;
                    } else {
                        in_file.reset();
                    }
                }
                bool is_zcia = false;
                FileUtil::Z3DSReadIOFile* z3ds_ptr = nullptr;
                if (in_file && in_file->IsGood() &&
                    FileUtil::Z3DSReadIOFile::GetUnderlyingFileMagic(in_file.get()).has_value()) {
                    auto z3ds = std::make_unique<FileUtil::Z3DSReadIOFile>(std::move(in_file));
                    z3ds_ptr = z3ds.get();
                    in_file = std::move(z3ds);
                    is_zcia = true;
                }
                bool resolved_from_z3ds_meta = false;
                if (z3ds_ptr != nullptr && z3ds_ptr->IsGood()) {
                    const auto ti_meta = z3ds_ptr->Metadata().Get("titleinfo");
                    const auto smdh_meta = z3ds_ptr->Metadata().Get("smdh");
                    if (ti_meta && ti_meta->size() >= sizeof(u64) && smdh_meta &&
                        smdh_meta->size() >= sizeof(Loader::SMDH) &&
                        Loader::IsValidSMDH(std::span<const u8>{smdh_meta->data(), sizeof(Loader::SMDH)})) {
                        u64 meta_tid = 0;
                        std::memcpy(&meta_tid, ti_meta->data(), sizeof(u64));
                        if (meta_tid != 0) {
                            gid->title_id = meta_tid;
                            gid->is_encrypted = false;
                            gid->loaded = true;
                            gid->file_type = is_bundle ? "BCIA" : "ZCIA";
                            std::memcpy(&gid->smdh, smdh_meta->data(), sizeof(Loader::SMDH));
                            resolved_from_z3ds_meta = true;
                        }
                    }
                }
                FileSys::CIAContainer container;
                if (!resolved_from_z3ds_meta && in_file && in_file->IsGood() &&
                    container.Load(in_file.get()) == Loader::ResultStatus::Success) {
                    const auto& tmd = container.GetTitleMetadata();
                    gid->title_id = tmd.GetTitleID();
                    gid->is_encrypted = tmd.HasEncryptedContent(container.GetHeader());
                    gid->loaded = true;
                    gid->file_type = is_bundle ? "BCIA" : (is_zcia ? "ZCIA" : "CIA");
                    if (container.GetSMDH()) {
                        std::memcpy(&gid->smdh, container.GetSMDH().get(), sizeof(Loader::SMDH));
                    }
                }
            }
        }
        return gid;
    }

    std::vector<u8> smdh = [program_id, &loader, &is_encrypted]() -> std::vector<u8> {
        std::vector<u8> original_smdh;
        auto result = loader->ReadIcon(original_smdh, true);
        if (result != Loader::ResultStatus::Success) {
            is_encrypted = result == Loader::ResultStatus::ErrorEncrypted;
            return {};
        }

        return original_smdh;
    }();

    GameInfoData* gid = new GameInfoData();
    std::memset(&gid->smdh, 0, sizeof(Loader::SMDH));
    if (!smdh.empty()) {
        std::memcpy(&gid->smdh, smdh.data(), std::min(smdh.size(), sizeof(Loader::SMDH)));
    }
    gid->loaded = true;
    gid->is_encrypted = is_encrypted;
    gid->title_id = program_id;
    gid->file_type = Loader::GetFileTypeString(loader->GetFileType(), loader->IsFileCompressed(),
                                               loader->IsFileBundle());
    gid->is_insertable = loader->GetFileType() == Loader::FileType::CCI;

    return gid;
}

std::string PickSmdhField(const Loader::SMDH* smdh, bool publisher) {
    static constexpr std::array<Loader::SMDH::TitleLanguage, 12> kOrder = {
        Loader::SMDH::TitleLanguage::English,
        Loader::SMDH::TitleLanguage::Russian,
        Loader::SMDH::TitleLanguage::Japanese,
        Loader::SMDH::TitleLanguage::French,
        Loader::SMDH::TitleLanguage::German,
        Loader::SMDH::TitleLanguage::Spanish,
        Loader::SMDH::TitleLanguage::Italian,
        Loader::SMDH::TitleLanguage::Dutch,
        Loader::SMDH::TitleLanguage::Portuguese,
        Loader::SMDH::TitleLanguage::Korean,
        Loader::SMDH::TitleLanguage::SimplifiedChinese,
        Loader::SMDH::TitleLanguage::TraditionalChinese,
    };
    for (const auto lang : kOrder) {
        const auto& entry = smdh->titles[static_cast<std::size_t>(lang)];
        if (publisher) {
            std::u16string raw{reinterpret_cast<const char16_t*>(entry.publisher.data()),
                               entry.publisher.size()};
            if (auto nul = raw.find(u'\0'); nul != std::u16string::npos) raw.resize(nul);
            std::string utf8 = Common::UTF16ToUTF8(raw);
            if (!utf8.empty()) return utf8;
        } else {
            std::u16string raw{reinterpret_cast<const char16_t*>(entry.long_title.data()),
                               entry.long_title.size()};
            if (auto nul = raw.find(u'\0'); nul != std::u16string::npos) raw.resize(nul);
            std::string utf8 = Common::UTF16ToUTF8(raw);
            if (!utf8.empty()) return utf8;
        }
    }
    return "";
}

} // namespace

extern "C" {

static GameInfoData* GetPointer(JNIEnv* env, jobject obj) {
    return reinterpret_cast<GameInfoData*>(env->GetLongField(obj, IDCache::GetGameInfoPointer()));
}

JNIEXPORT jlong JNICALL Java_com_storm3ds_emulator_nativebridge_model_GameInfo_initialize(JNIEnv* env, jclass,
                                                                            jstring j_path) {
    GameInfoData* game_info_data = GetNewGameInfoData(GetJString(env, j_path));
    return reinterpret_cast<jlong>(game_info_data);
}

JNIEXPORT jboolean JNICALL Java_com_storm3ds_emulator_nativebridge_model_GameInfo_isValid(JNIEnv* env,
                                                                            jobject obj) {
    auto* ptr = GetPointer(env, obj);
    return ptr ? static_cast<jboolean>(ptr->loaded) : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_storm3ds_emulator_nativebridge_model_GameInfo_isEncrypted(JNIEnv* env,
                                                                                jobject obj) {
    auto* ptr = GetPointer(env, obj);
    return ptr ? static_cast<jboolean>(ptr->is_encrypted) : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_storm3ds_emulator_nativebridge_model_GameInfo_finalize(JNIEnv* env, jobject obj) {
    delete GetPointer(env, obj);
}

jstring Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getTitle(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    if (!ptr) {
        return ToJString(env, "");
    }
    Loader::SMDH* smdh = &ptr->smdh;
    if (!smdh->IsValid()) {
        return ToJString(env, "");
    }
    return ToJString(env, PickSmdhField(smdh, false));
}

jstring Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getCompany(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    if (!ptr) {
        return ToJString(env, "");
    }
    Loader::SMDH* smdh = &ptr->smdh;
    if (!smdh->IsValid()) {
        return ToJString(env, "");
    }
    return ToJString(env, PickSmdhField(smdh, true));
}

jlong Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getTitleID(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    return ptr ? static_cast<jlong>(ptr->title_id) : 0;
}

jstring Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getRegions(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    if (!ptr) {
        return ToJString(env, "Invalid region");
    }
    Loader::SMDH* smdh = &ptr->smdh;
    if (!smdh->IsValid()) {
        return ToJString(env, "");
    }

    using GameRegion = Loader::SMDH::GameRegion;
    static const std::map<GameRegion, const char*> regions_map = {
        {GameRegion::Japan, "Japan"},   {GameRegion::NorthAmerica, "North America"},
        {GameRegion::Europe, "Europe"}, {GameRegion::Australia, "Australia"},
        {GameRegion::China, "China"},   {GameRegion::Korea, "Korea"},
        {GameRegion::Taiwan, "Taiwan"}};
    std::vector<GameRegion> regions = smdh->GetRegions();

    if (regions.empty()) {
        return ToJString(env, "Invalid region");
    }

    const bool region_free =
        std::all_of(regions_map.begin(), regions_map.end(), [&regions](const auto& it) {
            return std::find(regions.begin(), regions.end(), it.first) != regions.end();
        });

    if (region_free) {
        return ToJString(env, "Region free");
    }

    const std::string separator = "|";
    std::string result;
    for (const auto region : regions) {
        const auto it = regions_map.find(region);
        const char* label = (it != regions_map.end()) ? it->second : "Invalid region";
        if (!result.empty()) {
            result += separator;
        }
        result += label;
    }

    return ToJString(env, result);
}

jintArray Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getIcon(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    if (!ptr) {
        return nullptr;
    }
    Loader::SMDH* smdh = &ptr->smdh;
    if (!smdh->IsValid()) {
        return nullptr;
    }

    // Always get a 48x48(large) icon and convert RGB565 -> ARGB_8888
    std::vector<u16> icon_data = smdh->GetIcon(true);
    if (icon_data.empty()) {
        return nullptr;
    }

    std::vector<jint> argb_pixels(icon_data.size());
    for (std::size_t i = 0; i < icon_data.size(); ++i) {
        const u16 px = icon_data[i];
        const u32 r5 = (px >> 11) & 0x1F;
        const u32 g6 = (px >> 5) & 0x3F;
        const u32 b5 = px & 0x1F;
        const u32 r = (r5 << 3) | (r5 >> 2);
        const u32 g = (g6 << 2) | (g6 >> 4);
        const u32 b = (b5 << 3) | (b5 >> 2);
        argb_pixels[i] = static_cast<jint>(0xFF000000u | (r << 16) | (g << 8) | b);
    }

    jintArray icon = env->NewIntArray(static_cast<jsize>(argb_pixels.size()));
    env->SetIntArrayRegion(icon, 0, env->GetArrayLength(icon), argb_pixels.data());

    return icon;
}

jboolean Java_com_storm3ds_emulator_nativebridge_model_GameInfo_isSystemTitle(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    if (!ptr) {
        return JNI_FALSE;
    }
    return ((ptr->title_id >> 32) & 0xFFFFFFFF) == 0x00040010;
}

jboolean Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getIsVisibleSystemTitle(JNIEnv* env,
                                                                          jobject obj) {
    auto* ptr = GetPointer(env, obj);
    if (!ptr) {
        return JNI_FALSE;
    }
    Loader::SMDH* smdh = &ptr->smdh;
    if (!smdh->IsValid()) {
        return JNI_FALSE;
    }

    return smdh->flags.visible;
}

jstring Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getFileType(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    return ToJString(env, ptr ? ptr->file_type : "");
}
jboolean Java_com_storm3ds_emulator_nativebridge_model_GameInfo_getIsInsertable(JNIEnv* env, jobject obj) {
    auto* ptr = GetPointer(env, obj);
    return ptr ? static_cast<jboolean>(ptr->is_insertable) : JNI_FALSE;
}
}
