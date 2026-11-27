// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <algorithm>
#include <QApplication>
#include <QColor>
#include <QGuiApplication>
#include <QPalette>
#include <QStyleHints>
#include <QWidget>
#ifdef _WIN32
#include <dwmapi.h>
#include <windows.h>
#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif
#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
#ifndef DWMWA_BORDER_COLOR
#define DWMWA_BORDER_COLOR 34
#endif
#ifndef DWMWA_CAPTION_COLOR
#define DWMWA_CAPTION_COLOR 35
#endif
#ifndef DWMWA_TEXT_COLOR
#define DWMWA_TEXT_COLOR 36
#endif
#ifndef DWMWCP_DEFAULT
#define DWMWCP_DEFAULT 0
#endif
#ifndef DWMWCP_DONOTROUND
#define DWMWCP_DONOTROUND 1
#endif
#endif
#include "citra_qt/storm_theme.h"
#include "citra_qt/uisettings.h"

namespace StormTheme {

namespace {

QColor AdjustHex(const QString& hex, int delta) {
    QColor c(hex);
    if (!c.isValid()) {
        return c;
    }
    return QColor(std::clamp(c.red() + delta, 0, 255), std::clamp(c.green() + delta, 0, 255),
                  std::clamp(c.blue() + delta, 0, 255));
}

QString HoverSurface(const ThemeDef& theme) {
    return AdjustHex(theme.card, theme.is_dark ? 14 : -10).name();
}

QString PressedSurface(const ThemeDef& theme) {
    return AdjustHex(theme.card, theme.is_dark ? 24 : -18).name();
}

QString DisabledSurface(const ThemeDef& theme) {
    return AdjustHex(theme.bg, theme.is_dark ? 6 : -6).name();
}

QString InputBg(const ThemeDef& theme) {
    return theme.is_dark ? AdjustHex(theme.bg, 7).name() : QStringLiteral("#ffffff");
}

#ifdef _WIN32
COLORREF ToColorRef(const QString& hex) {
    const QColor c(hex);
    return RGB(c.red(), c.green(), c.blue());
}
#endif

} // namespace

const std::vector<ThemeDef>& GetThemes() {
    static const std::vector<ThemeDef> themes = {
        {QStringLiteral("storm_flagship"), QStringLiteral("STORM DARK"), true,
         QStringLiteral("#111318"), QStringLiteral("#161A22"), QStringLiteral("#1D222E"),
         QStringLiteral("#171C27"), QStringLiteral("#2C3446"), QStringLiteral("#00D2FF"),
         QStringLiteral("#38BDF8"), QStringLiteral("#F1F5F9"), QStringLiteral("#94A3B8"),
         QStringLiteral("#64748B"), QStringLiteral("#1E3A5F"), QStringLiteral("#FFFFFF"),
         QStringLiteral("#07131B")},
        {QStringLiteral("storm_day"), QStringLiteral("STORM DAY"), false,
         QStringLiteral("#F8FAFC"), QStringLiteral("#EDF2F7"), QStringLiteral("#FFFFFF"),
         QStringLiteral("#F1F5F9"), QStringLiteral("#CBD5E1"), QStringLiteral("#0284C7"),
         QStringLiteral("#0EA5E9"), QStringLiteral("#0F172A"), QStringLiteral("#475569"),
         QStringLiteral("#94A3B8"), QStringLiteral("#CCE4FF"), QStringLiteral("#0B2447"),
         QStringLiteral("#FFFFFF")},
        {QStringLiteral("storm_midnight"), QStringLiteral("STORM MIDNIGHT"), true,
         QStringLiteral("#0D0B18"), QStringLiteral("#130F24"), QStringLiteral("#1A1630"),
         QStringLiteral("#141028"), QStringLiteral("#2E2850"), QStringLiteral("#8B5CF6"),
         QStringLiteral("#A78BFA"), QStringLiteral("#F8FAFC"), QStringLiteral("#94A3B8"),
         QStringLiteral("#6B638E"), QStringLiteral("#36266E"), QStringLiteral("#FFFFFF"),
         QStringLiteral("#FFFFFF")},
        {QStringLiteral("storm_night"), QStringLiteral("STORM NIGHT"), true,
         QStringLiteral("#000000"), QStringLiteral("#08080C"), QStringLiteral("#0F1015"),
         QStringLiteral("#0B0C10"), QStringLiteral("#1E2028"), QStringLiteral("#00F0FF"),
         QStringLiteral("#38BDF8"), QStringLiteral("#FFFFFF"), QStringLiteral("#94A3B8"),
         QStringLiteral("#5C6475"), QStringLiteral("#143B4D"), QStringLiteral("#FFFFFF"),
         QStringLiteral("#041218")},
        {QStringLiteral("storm_fantasy"), QStringLiteral("STORM FANTASY"), true,
         QStringLiteral("#0E1324"), QStringLiteral("#141A33"), QStringLiteral("#1B2342"),
         QStringLiteral("#151C36"), QStringLiteral("#2A3563"), QStringLiteral("#D4AF37"),
         QStringLiteral("#F59E0B"), QStringLiteral("#FFFBEB"), QStringLiteral("#CBD5E1"),
         QStringLiteral("#7886A8"), QStringLiteral("#3B311B"), QStringLiteral("#FFFBEB"),
         QStringLiteral("#0E1324")},
        {QStringLiteral("storm_cyberpunk"), QStringLiteral("STORM CYBERPUNK"), true,
         QStringLiteral("#0B0A10"), QStringLiteral("#13111C"), QStringLiteral("#1B1829"),
         QStringLiteral("#151221"), QStringLiteral("#2B2542"), QStringLiteral("#FCEE09"),
         QStringLiteral("#FF007F"), QStringLiteral("#FFFFFF"), QStringLiteral("#A3A0B5"),
         QStringLiteral("#64607A"), QStringLiteral("#3E2B14"), QStringLiteral("#FCEE09"),
         QStringLiteral("#0B0A10")},
        {QStringLiteral("storm_warhammer"), QStringLiteral("STORM WARHAMMER 40K"), true,
         QStringLiteral("#0C0A08"), QStringLiteral("#15120E"), QStringLiteral("#1E1A14"),
         QStringLiteral("#171410"), QStringLiteral("#322B22"), QStringLiteral("#CA8A04"),
         QStringLiteral("#D4AF37"), QStringLiteral("#FDF6E2"), QStringLiteral("#A8A29E"),
         QStringLiteral("#6E675F"), QStringLiteral("#3D2E10"), QStringLiteral("#FDF6E2"),
         QStringLiteral("#0C0A08")},
        {QStringLiteral("storm_elemental"), QStringLiteral("STORM ELEMENTAL"), true,
         QStringLiteral("#091318"), QStringLiteral("#0E1C24"), QStringLiteral("#142732"),
         QStringLiteral("#101F29"), QStringLiteral("#203D4E"), QStringLiteral("#10B981"),
         QStringLiteral("#34D399"), QStringLiteral("#ECFDF5"), QStringLiteral("#94A3B8"),
         QStringLiteral("#5C7A82"), QStringLiteral("#134238"), QStringLiteral("#ECFDF5"),
         QStringLiteral("#051912")},
        {QStringLiteral("storm_aurora"), QStringLiteral("STORM AURORA"), true,
         QStringLiteral("#090714"), QStringLiteral("#100E24"), QStringLiteral("#181533"),
         QStringLiteral("#131029"), QStringLiteral("#282254"), QStringLiteral("#A855F7"),
         QStringLiteral("#EC4899"), QStringLiteral("#FAF5FF"), QStringLiteral("#A8A29E"),
         QStringLiteral("#6B5E8C"), QStringLiteral("#3D1E6D"), QStringLiteral("#FFFFFF"),
         QStringLiteral("#FFFFFF")},
    };
    return themes;
}

QString NormalizeThemeId(const QString& theme_id) {
    if (theme_id == QStringLiteral("storm_light")) {
        return QStringLiteral("storm_day");
    }
    return theme_id;
}

bool IsStormTheme(const QString& theme_id) {
    const QString norm = NormalizeThemeId(theme_id);
    for (const auto& t : GetThemes()) {
        if (t.id == norm) {
            return true;
        }
    }
    return false;
}

const ThemeDef& ResolveTheme(const QString& theme_id) {
    const QString norm = NormalizeThemeId(theme_id);
    const auto& themes = GetThemes();
    for (const auto& t : themes) {
        if (t.id == norm) {
            return t;
        }
    }
    // Check whether a non-STORM system/light theme was selected
    if (theme_id == QStringLiteral("default") || theme_id == QStringLiteral("colorful")) {
        const bool os_dark = (QGuiApplication::styleHints()->colorScheme() == Qt::ColorScheme::Dark);
        if (!os_dark) {
            return themes[1]; // STORM DAY palette as light fallback for DWM & helpers
        }
    }
    return themes[0]; // STORM DARK
}

bool IsDarkThemeId(const QString& theme_id) {
    const QString norm = NormalizeThemeId(theme_id);
    for (const auto& t : GetThemes()) {
        if (t.id == norm) {
            return t.is_dark;
        }
    }
    if (theme_id.contains(QStringLiteral("dark"), Qt::CaseInsensitive) ||
        theme_id.contains(QStringLiteral("midnight"), Qt::CaseInsensitive)) {
        return true;
    }
    return QGuiApplication::styleHints()->colorScheme() == Qt::ColorScheme::Dark;
}

const ThemeDef& CurrentTheme() {
    return ResolveTheme(UISettings::values.theme);
}

bool IsCurrentThemeDark() {
    return IsDarkThemeId(UISettings::values.theme);
}

QColor AccentLinkColor() {
    const auto& theme = CurrentTheme();
    if (!theme.is_dark) {
        return QColor(QStringLiteral("#0369A1"));
    }
    return QColor(theme.accent);
}

QPalette BuildPalette(const ThemeDef& theme) {
    QPalette pal;
    const QColor bg(theme.bg);
    const QColor card(theme.card);
    const QColor header(theme.header);
    const QColor input(InputBg(theme));
    const QColor alt(theme.surface_alt);
    const QColor fg(theme.text_primary);
    const QColor fg_sec(theme.text_secondary);
    const QColor fg_dis(theme.text_disabled);
    const QColor border(theme.border);
    const QColor accent(theme.accent);
    const QColor sel_bg(theme.selection_bg);
    const QColor sel_fg(theme.selection_fg);

    pal.setColor(QPalette::Window, bg);
    pal.setColor(QPalette::WindowText, fg);
    pal.setColor(QPalette::Base, input);
    pal.setColor(QPalette::AlternateBase, alt);
    pal.setColor(QPalette::ToolTipBase, card);
    pal.setColor(QPalette::ToolTipText, fg);
    pal.setColor(QPalette::Text, fg);
    pal.setColor(QPalette::Button, card);
    pal.setColor(QPalette::ButtonText, fg);
    pal.setColor(QPalette::BrightText, sel_fg);
    pal.setColor(QPalette::Link, accent);
    pal.setColor(QPalette::LinkVisited, QColor(theme.accent_secondary));
    pal.setColor(QPalette::Highlight, accent);
    pal.setColor(QPalette::HighlightedText, QColor(theme.accent_text));
    pal.setColor(QPalette::PlaceholderText, fg_sec);
    pal.setColor(QPalette::Mid, border);
    pal.setColor(QPalette::Dark, AdjustHex(theme.border, -20));
    pal.setColor(QPalette::Light, header);
    pal.setColor(QPalette::Midlight, alt);

    pal.setColor(QPalette::Disabled, QPalette::WindowText, fg_dis);
    pal.setColor(QPalette::Disabled, QPalette::Text, fg_dis);
    pal.setColor(QPalette::Disabled, QPalette::ButtonText, fg_dis);
    pal.setColor(QPalette::Disabled, QPalette::Highlight, border);
    pal.setColor(QPalette::Disabled, QPalette::HighlightedText, fg_dis);
    pal.setColor(QPalette::Disabled, QPalette::Button, DisabledSurface(theme));
    pal.setColor(QPalette::Disabled, QPalette::Base, DisabledSurface(theme));
    pal.setColor(QPalette::Inactive, QPalette::Highlight, sel_bg);
    pal.setColor(QPalette::Inactive, QPalette::HighlightedText, sel_fg);

    return pal;
}

QString BuildStylesheet(const ThemeDef& theme) {
    const QString input_bg = InputBg(theme);
    const QString hover_bg = HoverSurface(theme);
    const QString pressed_bg = PressedSurface(theme);
    const QString disabled_bg = DisabledSurface(theme);
    const QString arrow_suffix = theme.is_dark ? QStringLiteral("light") : QStringLiteral("dark");
    const QString check_img =
        (theme.accent == QStringLiteral("#FCEE09")) ? QStringLiteral("check_dark.svg")
                                                    : QStringLiteral("check_white.svg");
    const QString radio_img =
        (theme.accent == QStringLiteral("#FCEE09")) ? QStringLiteral("radio_dot_dark.svg")
                                                    : QStringLiteral("radio_dot_white.svg");

    QString qss = QStringLiteral(R"(
/* Canonical STORM Theme: %1 */
QWidget {
    color: %2;
    background-color: %3;
    selection-background-color: %4;
    selection-color: %5;
    outline: 0;
}

QMainWindow, QDialog, QStackedWidget {
    background-color: %3;
}

QLabel {
    background-color: transparent;
}

QLabel:disabled {
    color: %6;
}

QMenuBar {
    background-color: %7;
    border-bottom: 1px solid %8;
    padding: 2px 6px;
}

QMenuBar::item {
    background: transparent;
    padding: 5px 11px;
    border-radius: 5px;
    color: %2;
}

QMenuBar::item:selected, QMenuBar::item:pressed {
    background-color: %9;
    color: %10;
}

QMenu {
    background-color: %11;
    border: 1px solid %8;
    border-radius: 6px;
    padding: 4px;
}

QMenu::item {
    padding: 6px 26px 6px 14px;
    border-radius: 4px;
    color: %2;
}

QMenu::item:selected {
    background-color: %4;
    color: %5;
}

QMenu::item:disabled {
    color: %6;
}

QMenu::separator {
    height: 1px;
    background: %8;
    margin: 4px 8px;
}

QMenu::indicator {
    width: 14px;
    height: 14px;
    margin-left: 6px;
    border: 1px solid %8;
    border-radius: 3px;
    background-color: %12;
}

QMenu::indicator:checked {
    background-color: %10;
    border-color: %10;
    image: url(:/storm_assets/%13);
}

QMenu::right-arrow {
    width: 10px;
    height: 10px;
    right: 8px;
    image: url(:/storm_assets/arrow_right_%14.svg);
}

QToolBar {
    background-color: %7;
    border: none;
    border-bottom: 1px solid %8;
    spacing: 4px;
    padding: 4px 8px;
}

QToolBar::separator {
    background-color: %8;
    width: 1px;
    margin: 4px 4px;
}

QToolButton {
    background-color: transparent;
    border: 1px solid transparent;
    border-radius: 6px;
    padding: 4px 8px;
    color: %2;
    min-height: 22px;
}

QToolButton:hover {
    background-color: %9;
    border-color: %10;
    color: %2;
}

QToolButton:pressed, QToolButton:checked {
    background-color: %4;
    border-color: %10;
    color: %5;
}

QToolButton:disabled {
    color: %6;
}

QToolButton#FilterCloseButton {
    background-color: %11;
    border: 1px solid %8;
    border-radius: 5px;
    color: %2;
    font-weight: bold;
    padding: 0px;
    min-width: 24px;
    min-height: 24px;
}

QToolButton#FilterCloseButton:hover {
    background-color: #DC2626;
    border-color: #EF4444;
    color: #FFFFFF;
}

QStatusBar {
    background-color: %7;
    border-top: 1px solid %8;
    color: %15;
    padding: 1px 4px;
}

QStatusBar::item {
    border: none;
}

QPushButton {
    background-color: %11;
    border: 1px solid %8;
    border-radius: 6px;
    padding: 5px 14px;
    color: %2;
    min-height: 22px;
}

QPushButton:hover {
    background-color: %9;
    border-color: %10;
}

QPushButton:pressed {
    background-color: %16;
    border-color: %10;
}

QPushButton:disabled {
    background-color: %17;
    border-color: %8;
    color: %6;
}

QPushButton#GraphicsAPIStatusBarButton,
QPushButton#TogglableStatusBarButton {
    color: %15;
    border: 1px solid transparent;
    border-radius: 4px;
    background-color: transparent;
    padding: 2px 7px;
    min-width: 0px;
    min-height: 18px;
    font-weight: 600;
}

QPushButton#GraphicsAPIStatusBarButton:hover,
QPushButton#TogglableStatusBarButton:hover {
    background-color: %9;
    border: 1px solid %10;
}

QPushButton#TogglableStatusBarButton:checked {
    color: %10;
    font-weight: bold;
}

QFrame#VolumePopup {
    background-color: %11;
    border: 1px solid %10;
    border-radius: 8px;
}

QLineEdit, QPlainTextEdit, QTextEdit, QSpinBox, QDoubleSpinBox, QComboBox {
    background-color: %12;
    border: 1px solid %8;
    border-radius: 6px;
    padding: 4px 8px;
    color: %2;
    min-height: 22px;
}

QSpinBox, QDoubleSpinBox, QComboBox {
    padding-right: 24px;
}

QLineEdit:hover, QSpinBox:hover, QDoubleSpinBox:hover, QComboBox:hover {
    border-color: %10;
}

QLineEdit:focus, QPlainTextEdit:focus, QTextEdit:focus,
QSpinBox:focus, QDoubleSpinBox:focus, QComboBox:focus {
    border-color: %10;
}

QLineEdit:disabled, QPlainTextEdit:disabled, QTextEdit:disabled,
QSpinBox:disabled, QDoubleSpinBox:disabled, QComboBox:disabled {
    background-color: %17;
    border-color: %8;
    color: %6;
}

QComboBox::drop-down {
    subcontrol-origin: padding;
    subcontrol-position: top right;
    width: 24px;
    border-left: 1px solid %8;
    border-top-right-radius: 6px;
    border-bottom-right-radius: 6px;
    background-color: transparent;
}

QComboBox::down-arrow {
    width: 12px;
    height: 12px;
    image: url(:/storm_assets/arrow_down_%14.svg);
}

QSpinBox::up-button, QDoubleSpinBox::up-button {
    subcontrol-origin: border;
    subcontrol-position: top right;
    width: 20px;
    border-left: 1px solid %8;
    border-bottom: 1px solid %8;
    border-top-right-radius: 6px;
    background-color: %11;
}

QSpinBox::down-button, QDoubleSpinBox::down-button {
    subcontrol-origin: border;
    subcontrol-position: bottom right;
    width: 20px;
    border-left: 1px solid %8;
    border-bottom-right-radius: 6px;
    background-color: %11;
}

QSpinBox::up-button:hover, QDoubleSpinBox::up-button:hover,
QSpinBox::down-button:hover, QDoubleSpinBox::down-button:hover {
    background-color: %9;
}

QSpinBox::up-arrow, QDoubleSpinBox::up-arrow {
    width: 10px;
    height: 10px;
    image: url(:/storm_assets/arrow_up_%14.svg);
}

QSpinBox::down-arrow, QDoubleSpinBox::down-arrow {
    width: 10px;
    height: 10px;
    image: url(:/storm_assets/arrow_down_%14.svg);
}

QComboBox QAbstractItemView {
    background-color: %11;
    color: %2;
    border: 1px solid %8;
    border-radius: 6px;
    padding: 3px;
    selection-background-color: %4;
    selection-color: %5;
    outline: 0;
}

QTreeView, QListView, QListWidget, QTableView {
    background-color: %12;
    alternate-background-color: %18;
    border: 1px solid %8;
    border-radius: 6px;
    color: %2;
    gridline-color: %8;
}

QTreeView::item, QListWidget::item, QListView::item, QTableView::item {
    padding: 5px 6px;
    border-radius: 4px;
    color: %2;
}

QTreeView::item:hover, QListWidget::item:hover, QListView::item:hover {
    background-color: %9;
}

QTreeView::item:selected, QListWidget::item:selected, QListView::item:selected, QTableView::item:selected {
    background-color: %4;
    color: %5;
}

QTreeView::branch:has-children:!has-siblings:closed,
QTreeView::branch:closed:has-children:has-siblings {
    image: url(:/storm_assets/branch_closed_%14.svg);
}

QTreeView::branch:open:has-children:!has-siblings,
QTreeView::branch:open:has-children:has-siblings {
    image: url(:/storm_assets/branch_open_%14.svg);
}

QHeaderView::section {
    background-color: %7;
    color: %2;
    border: none;
    border-right: 1px solid %8;
    border-bottom: 1px solid %8;
    padding: 6px 8px;
    font-weight: 600;
}

QTabWidget::pane {
    border: 1px solid %8;
    border-radius: 6px;
    background-color: %11;
    top: -1px;
    padding: 4px;
}

QTabBar::tab {
    background-color: %7;
    color: %15;
    border: 1px solid %8;
    border-bottom: 1px solid %8;
    border-top-left-radius: 6px;
    border-top-right-radius: 6px;
    padding: 6px 12px;
    margin-right: 2px;
}

QTabBar::tab:selected {
    background-color: %11;
    color: %10;
    border-color: %10;
    border-bottom-color: %11;
    margin-bottom: -1px;
    font-weight: 600;
}

QTabBar::tab:hover:!selected {
    background-color: %9;
    color: %2;
}

QTabBar::tab:disabled {
    color: %6;
}

QGroupBox {
    border: 1px solid %8;
    border-radius: 8px;
    margin-top: 18px;
    padding: 14px 12px 12px 12px;
    background-color: %11;
}

QGroupBox::title {
    subcontrol-origin: margin;
    subcontrol-position: top left;
    left: 12px;
    top: 2px;
    padding: 1px 6px;
    background-color: %11;
    border-radius: 4px;
    color: %10;
    font-weight: 600;
}

QGroupBox::indicator {
    width: 15px;
    height: 15px;
    border: 1px solid %8;
    border-radius: 4px;
    background-color: %12;
}

QGroupBox::indicator:checked {
    background-color: %10;
    border-color: %10;
    image: url(:/storm_assets/%13);
}

QCheckBox, QRadioButton {
    spacing: 8px;
    color: %2;
    background: transparent;
}

QCheckBox:disabled, QRadioButton:disabled {
    color: %6;
}

QCheckBox::indicator, QRadioButton::indicator {
    width: 16px;
    height: 16px;
    border: 1px solid %8;
    background-color: %12;
}

QCheckBox::indicator {
    border-radius: 4px;
}

QRadioButton::indicator {
    border-radius: 8px;
}

QCheckBox::indicator:hover, QRadioButton::indicator:hover {
    border-color: %10;
}

QCheckBox::indicator:checked {
    background-color: %10;
    border-color: %10;
    image: url(:/storm_assets/%13);
}

QRadioButton::indicator:checked {
    background-color: %10;
    border-color: %10;
    image: url(:/storm_assets/%19);
}

QCheckBox::indicator:indeterminate {
    background-color: %4;
    border-color: %10;
    image: url(:/storm_assets/indeterminate_white.svg);
}

QCheckBox::indicator:disabled, QRadioButton::indicator:disabled {
    background-color: %17;
    border-color: %8;
}

QSlider {
    min-height: 24px;
    background: transparent;
}

QSlider::groove:horizontal {
    height: 6px;
    background: %8;
    border-radius: 3px;
}

QSlider::sub-page:horizontal {
    background: %10;
    border-radius: 3px;
}

QSlider::handle:horizontal {
    background: %12;
    border: 2px solid %10;
    width: 14px;
    height: 14px;
    margin: -5px 0;
    border-radius: 8px;
}

QSlider::handle:horizontal:disabled {
    background: %17;
    border-color: %6;
}

QProgressBar {
    background-color: %12;
    border: 1px solid %8;
    border-radius: 6px;
    text-align: center;
    color: %2;
    min-height: 18px;
}

QProgressBar::chunk {
    background-color: %10;
    border-radius: 5px;
}

QToolTip {
    background-color: %11;
    color: %2;
    border: 1px solid %10;
    border-radius: 4px;
    padding: 5px 8px;
}

QDockWidget {
    color: %2;
    background-color: %3;
    titlebar-close-icon: none;
}

QDockWidget::title {
    background-color: %7;
    color: %2;
    padding: 5px 8px;
    border-bottom: 1px solid %8;
    font-weight: 600;
}

QScrollArea, QScrollArea > QWidget > QWidget {
    background: transparent;
    border: none;
}

QScrollBar:vertical {
    background: %7;
    width: 12px;
    margin: 0px;
}

QScrollBar::handle:vertical {
    background: %8;
    min-height: 28px;
    border-radius: 5px;
    margin: 2px;
}

QScrollBar::handle:vertical:hover {
    background: %10;
}

QScrollBar:horizontal {
    background: %7;
    height: 12px;
    margin: 0px;
}

QScrollBar::handle:horizontal {
    background: %8;
    min-width: 28px;
    border-radius: 5px;
    margin: 2px;
}

QScrollBar::handle:horizontal:hover {
    background: %10;
}

QScrollBar::add-line, QScrollBar::sub-line {
    border: none;
    width: 0px;
    height: 0px;
}

QScrollBar::add-page, QScrollBar::sub-page {
    background: none;
}

QTextBrowser, QPlainTextEdit {
    background-color: %12;
    border: 1px solid %8;
    border-radius: 6px;
    padding: 8px;
    color: %2;
    selection-background-color: %4;
    selection-color: %5;
}

QMessageBox, QDialogButtonBox {
    background-color: %3;
    color: %2;
}

QSplitter::handle {
    background-color: %8;
    width: 2px;
    height: 2px;
}

QSplitter::handle:hover {
    background-color: %10;
}
)")
                      .arg(theme.display_name, theme.text_primary, theme.bg, theme.selection_bg,
                           theme.selection_fg, theme.text_disabled, theme.header, theme.border,
                           hover_bg)
                      .arg(theme.accent, theme.card, input_bg, check_img, arrow_suffix,
                           theme.text_secondary, pressed_bg, disabled_bg, theme.surface_alt,
                           radio_img);

    return qss;
}

#ifdef _WIN32
void ApplyWindowsWindowChrome(QWidget* widget, const ThemeDef& theme, bool fullscreen) {
    if (!widget || !widget->isWindow() || !widget->windowHandle()) {
        return;
    }
    HWND hwnd = reinterpret_cast<HWND>(widget->winId());
    if (!hwnd) {
        return;
    }

    const BOOL dark_val = theme.is_dark ? TRUE : FALSE;
    if (FAILED(DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark_val,
                                     sizeof(dark_val)))) {
        DwmSetWindowAttribute(hwnd, 19, &dark_val, sizeof(dark_val));
    }

    const COLORREF caption_color = ToColorRef(theme.header);
    const COLORREF text_color = ToColorRef(theme.text_primary);
    const COLORREF border_color = ToColorRef(theme.border);
    DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, &caption_color, sizeof(caption_color));
    DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, &text_color, sizeof(text_color));
    DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, &border_color, sizeof(border_color));

    const DWORD corner_pref = fullscreen ? DWMWCP_DONOTROUND : DWMWCP_DEFAULT;
    DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &corner_pref,
                          sizeof(corner_pref));

    SetWindowPos(hwnd, nullptr, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    RedrawWindow(hwnd, nullptr, nullptr, RDW_FRAME | RDW_INVALIDATE);
}
#endif

} // namespace StormTheme
