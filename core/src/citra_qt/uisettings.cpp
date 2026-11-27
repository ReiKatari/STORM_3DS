// Copyright 2016-2026 Citra Emulator Project / Azahar Emulator Project / STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include "uisettings.h"

namespace UISettings {

const Themes themes{{
    {"STORM DARK", "storm_flagship"},
    {"STORM DAY", "storm_day"},
    {"STORM MIDNIGHT", "storm_midnight"},
    {"STORM NIGHT", "storm_night"},
    {"STORM FANTASY", "storm_fantasy"},
    {"STORM CYBERPUNK", "storm_cyberpunk"},
    {"STORM WARHAMMER 40K", "storm_warhammer"},
    {"STORM ELEMENTAL", "storm_elemental"},
    {"STORM AURORA", "storm_aurora"},
    {"System", "default"},
    {"System With Colorful Icons", "colorful"},
    {"Dark", "qdarkstyle"},
    {"Dark Colorful", "colorful_dark"},
    {"Midnight Blue", "qdarkstyle_midnight_blue"},
    {"Midnight Blue Colorful", "colorful_midnight_blue"},
}};

Values values = {};
} // namespace UISettings
