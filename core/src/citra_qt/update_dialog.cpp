// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <functional>
#include <memory>
#ifdef _WIN32
#include <windows.h>
#include <shellapi.h>
#include <winhttp.h>
#endif
#include <QDesktopServices>
#include <QDir>
#include <QFileInfo>
#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QLocale>
#include <QMap>
#include <QMessageBox>
#include <QMetaObject>
#include <QPointer>
#include <QProgressBar>
#include <QPushButton>
#include <QRegularExpression>
#include <QTextBrowser>
#include <QUrl>
#include <QVBoxLayout>
#include <QtConcurrent/QtConcurrentRun>
#include "citra_qt/storm_theme.h"
#include "citra_qt/uisettings.h"
#include "citra_qt/update_dialog.h"

namespace {

QString NormalizeLocaleCode(const QString& raw) {
    QString clean = raw.trimmed().toLower().replace(QLatin1Char('_'), QLatin1Char('-'));
    if (clean == QStringLiteral("in") || clean.startsWith(QStringLiteral("in-"))) {
        clean = QStringLiteral("id") + clean.mid(2);
    } else if (clean == QStringLiteral("iw") || clean.startsWith(QStringLiteral("iw-"))) {
        clean = QStringLiteral("he") + clean.mid(2);
    }
    return clean;
}

QString MatchReleaseLanguageHeading(const QString& raw_heading) {
    QString trimmed = raw_heading.trimmed();
    while (trimmed.startsWith(QLatin1Char('#'))) {
        trimmed.remove(0, 1);
    }
    trimmed = trimmed.trimmed();
    if (trimmed.startsWith(QLatin1Char('[')) && trimmed.endsWith(QLatin1Char(']'))) {
        trimmed = trimmed.mid(1, trimmed.size() - 2).trimmed();
    }
    trimmed = trimmed.toLower().replace(QLatin1Char('_'), QLatin1Char('-'));
    if (trimmed == QStringLiteral("ru") || trimmed.contains(QStringLiteral("русский")) ||
        trimmed.contains(QStringLiteral("russian"))) {
        return QStringLiteral("ru");
    }
    if (trimmed == QStringLiteral("en") || trimmed.contains(QStringLiteral("english")) ||
        trimmed.contains(QStringLiteral("английск"))) {
        return QStringLiteral("en");
    }
    if (trimmed == QStringLiteral("de") || trimmed.contains(QStringLiteral("deutsch")) ||
        trimmed.contains(QStringLiteral("german"))) {
        return QStringLiteral("de");
    }
    if (trimmed == QStringLiteral("es") || trimmed.contains(QStringLiteral("español")) ||
        trimmed.contains(QStringLiteral("espanol")) ||
        trimmed.contains(QStringLiteral("spanish"))) {
        return QStringLiteral("es");
    }
    if (trimmed == QStringLiteral("fr") || trimmed.contains(QStringLiteral("français")) ||
        trimmed.contains(QStringLiteral("francais")) ||
        trimmed.contains(QStringLiteral("french"))) {
        return QStringLiteral("fr");
    }
    if (trimmed == QStringLiteral("id") ||
        trimmed.contains(QStringLiteral("bahasa indonesia")) ||
        trimmed.contains(QStringLiteral("indonesian"))) {
        return QStringLiteral("id");
    }
    if (trimmed == QStringLiteral("it") || trimmed.contains(QStringLiteral("italiano")) ||
        trimmed.contains(QStringLiteral("italian"))) {
        return QStringLiteral("it");
    }
    if (trimmed == QStringLiteral("ja") || trimmed.contains(QStringLiteral("日本語")) ||
        trimmed.contains(QStringLiteral("japanese"))) {
        return QStringLiteral("ja");
    }
    if (trimmed == QStringLiteral("ko") || trimmed.contains(QStringLiteral("한국어")) ||
        trimmed.contains(QStringLiteral("korean"))) {
        return QStringLiteral("ko");
    }
    if (trimmed == QStringLiteral("hi") || trimmed.contains(QStringLiteral("हिन्दी")) ||
        trimmed.contains(QStringLiteral("hindi"))) {
        return QStringLiteral("hi");
    }
    if (trimmed == QStringLiteral("pt-br") || trimmed.contains(QStringLiteral("português")) ||
        trimmed.contains(QStringLiteral("portugues")) ||
        trimmed.contains(QStringLiteral("portuguese")) || trimmed == QStringLiteral("pt")) {
        return trimmed.contains(QStringLiteral("br")) ? QStringLiteral("pt-br")
                                                     : QStringLiteral("pt");
    }
    if (trimmed == QStringLiteral("zh-hans") || trimmed == QStringLiteral("zh-cn") ||
        trimmed == QStringLiteral("zh") || trimmed.contains(QStringLiteral("简体中文")) ||
        trimmed.contains(QStringLiteral("中文")) || trimmed.contains(QStringLiteral("chinese"))) {
        return (trimmed.contains(QStringLiteral("hans")) ||
                trimmed.contains(QStringLiteral("cn")) || trimmed.contains(QStringLiteral("简体")))
                   ? QStringLiteral("zh-hans")
                   : QStringLiteral("zh");
    }
    return {};
}

QString SelectLocalizedReleaseNotes(const QString& raw, const QString& ui_language,
                                    const QLocale& locale) {
    QString normalized = raw;
    normalized.replace(QStringLiteral("\r\n"), QStringLiteral("\n"));
    const QStringList lines = normalized.split(QLatin1Char('\n'));

    static const QRegularExpression kCommentTagRe(
        QStringLiteral("<!--\\s*lang\\s*:\\s*([a-zA-Z_-]+)\\s*-->"),
        QRegularExpression::CaseInsensitiveOption);
    static const QRegularExpression kCommentEndRe(QStringLiteral("<!--\\s*/lang\\s*-->"),
                                                  QRegularExpression::CaseInsensitiveOption);
    static const QRegularExpression kHtmlCommentLineRe(QStringLiteral("^\\s*<!--.*-->\\s*$"));

    QMap<QString, QStringList> marker_sections;
    QStringList marker_order;
    QString active_marker;
    for (const QString& line : lines) {
        const auto m = kCommentTagRe.match(line);
        if (m.hasMatch()) {
            active_marker = NormalizeLocaleCode(m.captured(1));
            if (!marker_sections.contains(active_marker)) {
                marker_order.append(active_marker);
            }
            QString stripped = line;
            stripped.remove(kCommentTagRe);
            stripped.remove(kCommentEndRe);
            stripped = stripped.trimmed();
            if (!stripped.isEmpty()) {
                marker_sections[active_marker].append(stripped);
            }
            if (kCommentEndRe.match(line).hasMatch()) {
                active_marker.clear();
            }
            continue;
        }
        if (kCommentEndRe.match(line).hasMatch()) {
            active_marker.clear();
            continue;
        }
        if (!active_marker.isEmpty() && !kHtmlCommentLineRe.match(line).hasMatch()) {
            marker_sections[active_marker].append(line);
        }
    }

    QMap<QString, QStringList> heading_sections;
    QStringList heading_order;
    if (marker_sections.size() < 2) {
        QString active_heading;
        for (const QString& line : lines) {
            const QString trimmed = line.trimmed();
            if (trimmed.startsWith(QStringLiteral("## ")) ||
                trimmed.startsWith(QStringLiteral("### "))) {
                const QString detected = MatchReleaseLanguageHeading(trimmed);
                if (!detected.isEmpty()) {
                    active_heading = detected;
                    if (!heading_sections.contains(detected)) {
                        heading_order.append(detected);
                    }
                    continue;
                }
            }
            if (!active_heading.isEmpty() && !kHtmlCommentLineRe.match(line).hasMatch()) {
                heading_sections[active_heading].append(line);
            }
        }
    }

    const bool use_markers = marker_sections.size() >= 2;
    const bool use_headings = !use_markers && heading_sections.size() >= 2;
    if (!use_markers && !use_headings) {
        QStringList clean;
        for (const QString& line : lines) {
            if (!kHtmlCommentLineRe.match(line).hasMatch()) {
                clean.append(line);
            }
        }
        return clean.join(QLatin1Char('\n')).trimmed();
    }

    const auto& chosen_map = use_markers ? marker_sections : heading_sections;
    const auto& chosen_order = use_markers ? marker_order : heading_order;

    QStringList candidates;
    const QString pref_bcp = NormalizeLocaleCode(ui_language);
    if (!pref_bcp.isEmpty()) {
        const QString pref_base = pref_bcp.section(QLatin1Char('-'), 0, 0);
        candidates.append(pref_bcp);
        if (pref_base == QStringLiteral("zh")) {
            candidates.append(QStringLiteral("zh-hans"));
        }
        if (!pref_base.isEmpty()) {
            candidates.append(pref_base);
        }
    }

    const QString bcp = NormalizeLocaleCode(locale.bcp47Name());
    const QString base_lang = bcp.section(QLatin1Char('-'), 0, 0);
    if (!bcp.isEmpty()) {
        candidates.append(bcp);
    }
    if (base_lang == QStringLiteral("zh")) {
        candidates.append(QStringLiteral("zh-hans"));
    }
    if (!base_lang.isEmpty()) {
        candidates.append(base_lang);
    }
    candidates.append(QStringLiteral("en"));
    candidates.append(QStringLiteral("ru"));

    for (const QString& key : candidates) {
        const auto it = chosen_map.constFind(key);
        if (it != chosen_map.constEnd() && !it.value().join(QString()).trimmed().isEmpty()) {
            return it.value().join(QLatin1Char('\n')).trimmed();
        }
    }
    for (const QString& key : chosen_order) {
        const auto it = chosen_map.constFind(key);
        if (it != chosen_map.constEnd() && !it.value().join(QString()).trimmed().isEmpty()) {
            return it.value().join(QLatin1Char('\n')).trimmed();
        }
    }
    return normalized.trimmed();
}

QString FormatArchiveSize(std::uint64_t bytes) {
    if (bytes == 0) {
        return {};
    }
    const double mb = static_cast<double>(bytes) / (1024.0 * 1024.0);
    return QString::number(mb, 'f', 1) + QStringLiteral(" MB");
}

QFrame* BuildVersionPill(const QString& label_text, const QString& version_text, bool accent,
                         const StormTheme::ThemeDef& theme) {
    auto* frame = new QFrame();
    frame->setFrameShape(QFrame::StyledPanel);
    const QColor acc(theme.is_dark ? theme.accent : StormTheme::AccentLinkColor().name());
    if (accent) {
        frame->setStyleSheet(
            QStringLiteral("QFrame { border: 1px solid %1; border-radius: 8px; padding: 6px 10px; "
                           "background-color: %2; }")
                .arg(acc.name(), theme.is_dark ? QStringLiteral("rgba(0, 200, 255, 28)")
                                               : QStringLiteral("#E0F2FE")));
    } else {
        frame->setStyleSheet(
            QStringLiteral("QFrame { border: 1px solid %1; border-radius: 8px; padding: 6px 10px; "
                           "background-color: %2; }")
                .arg(theme.border, theme.card));
    }

    auto* layout = new QVBoxLayout(frame);
    layout->setContentsMargins(6, 4, 6, 4);
    layout->setSpacing(2);

    auto* title_lbl = new QLabel(label_text);
    title_lbl->setAlignment(Qt::AlignCenter);
    title_lbl->setStyleSheet(
        QStringLiteral("border: none; background: transparent; font-size: 11px; color: %1;")
            .arg(theme.text_secondary));

    auto* ver_lbl = new QLabel(version_text);
    ver_lbl->setAlignment(Qt::AlignCenter);
    ver_lbl->setStyleSheet(
        accent ? QStringLiteral(
                     "border: none; background: transparent; font-weight: bold; font-size: 14px; "
                     "color: %1;")
                     .arg(acc.name())
               : QStringLiteral(
                     "border: none; background: transparent; font-weight: bold; font-size: 14px; "
                     "color: %1;")
                     .arg(theme.text_primary));

    layout->addWidget(title_lbl);
    layout->addWidget(ver_lbl);
    return frame;
}

#ifdef _WIN32
bool DownloadFileWinHttp(const std::wstring& url, const std::wstring& dest_path,
                         const std::shared_ptr<std::atomic<bool>>& cancel_flag,
                         const std::function<void(std::uint64_t, std::uint64_t)>& on_progress) {
    URL_COMPONENTS uc{};
    uc.dwStructSize = sizeof(uc);
    wchar_t host_buf[512]{};
    wchar_t path_buf[2048]{};
    uc.lpszHostName = host_buf;
    uc.dwHostNameLength = static_cast<DWORD>(std::size(host_buf));
    uc.lpszUrlPath = path_buf;
    uc.dwUrlPathLength = static_cast<DWORD>(std::size(path_buf));
    if (!WinHttpCrackUrl(url.c_str(), static_cast<DWORD>(url.size()), 0, &uc)) {
        return false;
    }

    HINTERNET hSession =
        WinHttpOpen(L"STORM-3DS-Updater/1.12.1", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                    WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) {
        return false;
    }
    WinHttpSetTimeouts(hSession, 15000, 15000, 30000, 30000);
    DWORD redirect_policy = WINHTTP_OPTION_REDIRECT_POLICY_ALWAYS;
    WinHttpSetOption(hSession, WINHTTP_OPTION_REDIRECT_POLICY, &redirect_policy,
                     sizeof(redirect_policy));

    HINTERNET hConnect = WinHttpConnect(hSession, host_buf, uc.nPort, 0);
    if (!hConnect) {
        WinHttpCloseHandle(hSession);
        return false;
    }

    const DWORD flags = (uc.nScheme == INTERNET_SCHEME_HTTPS) ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"GET", path_buf, nullptr, WINHTTP_NO_REFERER,
                                            WINHTTP_DEFAULT_ACCEPT_TYPES, flags);
    if (!hRequest) {
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return false;
    }

    const wchar_t* headers = L"User-Agent: STORM-3DS-Updater\r\n";
    BOOL ok = WinHttpSendRequest(hRequest, headers, static_cast<DWORD>(-1L), WINHTTP_NO_REQUEST_DATA,
                                 0, 0, 0) &&
              WinHttpReceiveResponse(hRequest, nullptr);
    DWORD status = 0;
    DWORD status_sz = sizeof(status);
    if (ok) {
        WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                            WINHTTP_HEADER_NAME_BY_INDEX, &status, &status_sz,
                            WINHTTP_NO_HEADER_INDEX);
    }
    if (!ok || status < 200 || status >= 400) {
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return false;
    }

    std::uint64_t total_bytes = 0;
    wchar_t len_buf[64]{};
    DWORD len_buf_sz = sizeof(len_buf);
    if (WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_CONTENT_LENGTH, WINHTTP_HEADER_NAME_BY_INDEX,
                            len_buf, &len_buf_sz, WINHTTP_NO_HEADER_INDEX)) {
        total_bytes = _wcstoui64(len_buf, nullptr, 10);
    }

    std::ofstream out(std::filesystem::path(dest_path), std::ios::binary | std::ios::trunc);
    if (!out.is_open()) {
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return false;
    }

    std::uint64_t downloaded = 0;
    std::vector<char> buffer(65536);
    while (!cancel_flag->load()) {
        DWORD available = 0;
        if (!WinHttpQueryDataAvailable(hRequest, &available)) {
            ok = FALSE;
            break;
        }
        if (available == 0) {
            break;
        }
        DWORD to_read = std::min<DWORD>(available, static_cast<DWORD>(buffer.size()));
        DWORD read = 0;
        if (!WinHttpReadData(hRequest, buffer.data(), to_read, &read) || read == 0) {
            break;
        }
        out.write(buffer.data(), static_cast<std::streamsize>(read));
        if (!out.good()) {
            ok = FALSE;
            break;
        }
        downloaded += read;
        if (on_progress) {
            on_progress(downloaded, total_bytes);
        }
    }
    out.close();

    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);

    if (cancel_flag->load() || !ok || downloaded == 0) {
        std::error_code ec;
        std::filesystem::remove(std::filesystem::path(dest_path), ec);
        return false;
    }
    return true;
}
#endif

} // namespace

UpdateDialog::UpdateDialog(const QString& current_version, const UpdateChecker::ReleaseInfo& info,
                           QWidget* parent)
    : QDialog(parent, Qt::WindowTitleHint | Qt::WindowMinMaxButtonsHint |
                          Qt::WindowCloseButtonHint | Qt::WindowSystemMenuHint),
      cancel_flag(std::make_shared<std::atomic<bool>>(false)) {
    setWindowTitle(tr("STORM 3DS Update Available"));
    setMinimumSize(540, 480);
    resize(600, 540);

    const auto& theme = StormTheme::CurrentTheme();
    const QColor acc(theme.is_dark ? theme.accent : StormTheme::AccentLinkColor().name());

    auto* root = new QVBoxLayout(this);
    root->setContentsMargins(16, 16, 16, 16);
    root->setSpacing(12);

    auto* header_label = new QLabel(QString::fromStdString(info.title));
    header_label->setStyleSheet(QStringLiteral("font-size: 16px; font-weight: bold;"));
    header_label->setWordWrap(true);
    root->addWidget(header_label);

    QString clean_cur = current_version.trimmed();
    if (clean_cur.startsWith(QLatin1Char('v'), Qt::CaseInsensitive)) {
        clean_cur.remove(0, 1);
    }
    QString clean_new = QString::fromStdString(info.version).trimmed();
    if (clean_new.startsWith(QLatin1Char('v'), Qt::CaseInsensitive)) {
        clean_new.remove(0, 1);
    }

    auto* version_row = new QHBoxLayout();
    version_row->setSpacing(10);
    version_row->addWidget(BuildVersionPill(tr("Installed Version"), clean_cur, false, theme), 1);

    auto* arrow_lbl = new QLabel(QStringLiteral("->"));
    arrow_lbl->setAlignment(Qt::AlignCenter);
    arrow_lbl->setStyleSheet(
        QStringLiteral("font-weight: bold; font-size: 15px; color: %1;").arg(acc.name()));
    version_row->addWidget(arrow_lbl);

    version_row->addWidget(BuildVersionPill(tr("New Version"), clean_new, true, theme), 1);
    root->addLayout(version_row);

    QStringList meta_parts;
    if (!info.source_name.empty()) {
        meta_parts << tr("Mirror: %1").arg(QString::fromStdString(info.source_name));
    }
    const QString size_str = FormatArchiveSize(info.asset_size);
    if (!size_str.isEmpty()) {
        meta_parts << tr("Size: %1").arg(size_str);
    }
    if (!info.published_at.empty()) {
        meta_parts << tr("Date: %1").arg(QString::fromStdString(info.published_at).left(10));
    }
    if (!meta_parts.isEmpty()) {
        auto* meta_lbl = new QLabel(meta_parts.join(QStringLiteral("   |   ")));
        meta_lbl->setStyleSheet(
            QStringLiteral("font-size: 11px; color: %1;").arg(theme.text_secondary));
        meta_lbl->setWordWrap(true);
        root->addWidget(meta_lbl);
    }

    auto* notes_title = new QLabel(tr("Release Notes"));
    notes_title->setStyleSheet(QStringLiteral("font-weight: bold; font-size: 12px;"));
    root->addWidget(notes_title);

    auto* changelog_browser = new QTextBrowser(this);
    changelog_browser->setOpenExternalLinks(true);
    const QString localized_notes =
        info.changelog_md.empty()
            ? QString()
            : SelectLocalizedReleaseNotes(QString::fromStdString(info.changelog_md),
                                          UISettings::values.language, QLocale());
    const QString md_notes = localized_notes.trimmed().isEmpty()
                                 ? tr("STORM 3DS %1 is ready to download.").arg(clean_new)
                                 : localized_notes;
    changelog_browser->setMarkdown(md_notes);
    changelog_browser->setMinimumHeight(210);
    root->addWidget(changelog_browser, 1);

    download_progress = new QProgressBar(this);
    download_progress->setRange(0, 100);
    download_progress->setValue(0);
    download_progress->setVisible(false);
    root->addWidget(download_progress);

    download_status_label = new QLabel(this);
    download_status_label->setStyleSheet(
        QStringLiteral("font-size: 11px; color: %1;").arg(theme.text_secondary));
    download_status_label->setVisible(false);
    root->addWidget(download_status_label);

    auto* btn_row = new QHBoxLayout();
    btn_row->setSpacing(8);

    const QString primary_url =
        QString::fromStdString(info.download_url.empty() ? info.release_url : info.download_url);
    const QString page_url = QString::fromStdString(info.release_url);
    const QString asset_name = QString::fromStdString(info.asset_name);

    download_btn = new QPushButton(tr("Download Update"), this);
    download_btn->setDefault(true);
    connect(download_btn, &QPushButton::clicked, this,
            [this, primary_url, has_direct = !info.download_url.empty(), asset_name] {
#ifdef _WIN32
                if (has_direct && !asset_name.isEmpty()) {
                    StartDownload(primary_url, asset_name);
                    return;
                }
#endif
                if (!primary_url.isEmpty()) {
                    QDesktopServices::openUrl(QUrl(primary_url));
                }
                accept();
            });

    auto* page_btn = new QPushButton(tr("Open Release Page"), this);
    connect(page_btn, &QPushButton::clicked, this, [page_url] {
        if (!page_url.isEmpty()) {
            QDesktopServices::openUrl(QUrl(page_url));
        }
    });

    auto* later_btn = new QPushButton(tr("Remind Later"), this);
    connect(later_btn, &QPushButton::clicked, this, &QDialog::reject);

    btn_row->addWidget(later_btn);
    if (!page_url.isEmpty() && page_url != primary_url) {
        btn_row->addWidget(page_btn);
    }
    btn_row->addStretch(1);
    btn_row->addWidget(download_btn);
    root->addLayout(btn_row);
}

UpdateDialog::~UpdateDialog() {
    if (cancel_flag) {
        cancel_flag->store(true);
    }
}

void UpdateDialog::StartDownload(const QString& url, const QString& asset_name) {
#ifndef _WIN32
    QDesktopServices::openUrl(QUrl(url));
    accept();
    return;
#else
    download_btn->setEnabled(false);
    download_progress->setRange(0, 100);
    download_progress->setValue(0);
    download_progress->setVisible(true);
    download_status_label->setText(tr("Downloading %1...").arg(asset_name));
    download_status_label->setVisible(true);

    const QString clean_name = QFileInfo(asset_name).fileName();
    const QString dest_qpath = QDir::tempPath() + QLatin1Char('/') + clean_name;
    const std::wstring wurl = url.toStdWString();
    const std::wstring wdest = QDir::toNativeSeparators(dest_qpath).toStdWString();
    const auto flag = cancel_flag;
    QPointer<UpdateDialog> guard(this);

    (void)QtConcurrent::run([guard, wurl, wdest, url, clean_name, flag]() {
        const bool ok = DownloadFileWinHttp(
            wurl, wdest, flag, [guard](std::uint64_t done, std::uint64_t total) {
                QMetaObject::invokeMethod(
                    guard,
                    [guard, done, total]() {
                        if (!guard) {
                            return;
                        }
                        if (total > 0) {
                            guard->download_progress->setRange(0, 100);
                            guard->download_progress->setValue(
                                static_cast<int>((done * 100) / total));
                            guard->download_status_label->setText(
                                QStringLiteral("%1 / %2 MB")
                                    .arg(static_cast<double>(done) / (1024.0 * 1024.0), 0, 'f', 1)
                                    .arg(static_cast<double>(total) / (1024.0 * 1024.0), 0, 'f',
                                         1));
                        } else {
                            guard->download_progress->setRange(0, 0);
                        }
                    },
                    Qt::QueuedConnection);
            });

        QMetaObject::invokeMethod(
            guard,
            [guard, ok, wdest, url, clean_name, flag]() {
                if (!guard || flag->load()) {
                    return;
                }
                if (!ok) {
                    guard->download_progress->setVisible(false);
                    guard->download_status_label->setText(
                        tr("Direct download failed, opening browser..."));
                    guard->download_btn->setEnabled(true);
                    QDesktopServices::openUrl(QUrl(url));
                    return;
                }
                guard->download_progress->setValue(100);
                if (clean_name.endsWith(QStringLiteral(".exe"), Qt::CaseInsensitive)) {
                    ShellExecuteW(nullptr, L"open", wdest.c_str(), nullptr, nullptr,
                                  SW_SHOWNORMAL);
                } else {
                    const std::wstring args = L"/select,\"" + wdest + L"\"";
                    ShellExecuteW(nullptr, L"open", L"explorer.exe", args.c_str(), nullptr,
                                  SW_SHOWNORMAL);
                }
                guard->accept();
            },
            Qt::QueuedConnection);
    });
#endif
}
