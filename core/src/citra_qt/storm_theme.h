// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#pragma once

#include <QColor>
#include <QPalette>
#include <QString>
#include <vector>

class QWidget;

namespace StormTheme {

struct ThemeDef {
    QString id;
    QString display_name;
    bool is_dark;
    QString bg;
    QString header;
    QString card;
    QString surface_alt;
    QString border;
    QString accent;
    QString accent_secondary;
    QString text_primary;
    QString text_secondary;
    QString text_disabled;
    QString selection_bg;
    QString selection_fg;
    QString accent_text;
};

const std::vector<ThemeDef>& GetThemes();
QString NormalizeThemeId(const QString& theme_id);
bool IsStormTheme(const QString& theme_id);
const ThemeDef& ResolveTheme(const QString& theme_id);
const ThemeDef& CurrentTheme();
bool IsCurrentThemeDark();
bool IsDarkThemeId(const QString& theme_id);

QPalette BuildPalette(const ThemeDef& theme);
QString BuildStylesheet(const ThemeDef& theme);
QColor AccentLinkColor();

#ifdef _WIN32
void ApplyWindowsWindowChrome(QWidget* widget, const ThemeDef& theme, bool fullscreen = false);
#endif

} // namespace StormTheme
