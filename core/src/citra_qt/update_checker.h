// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project / STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace UpdateChecker {

struct ReleaseInfo {
    std::string version;
    std::string title;
    std::string changelog_md;
    std::string published_at;
    std::string asset_name;
    std::uint64_t asset_size = 0;
    std::string download_url;
    std::string release_url;
    std::string source_name;
};

std::optional<ReleaseInfo> GetLatestRelease(bool include_prereleases);

} // namespace UpdateChecker
