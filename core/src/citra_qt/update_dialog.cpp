// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <QDesktopServices>
#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QList>
#include <QLocale>
#include <QMap>
#include <QPushButton>
#include <QRegularExpression>
#include <QTextBrowser>
#include <QUrl>
#include <QVBoxLayout>
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
    if (trimmed == QStringLiteral("ru") || trimmed.contains(QStringLiteral("русский")) || trimmed.contains(QStringLiteral("russian"))) {
        return QStringLiteral("ru");
    }
    if (trimmed == QStringLiteral("en") || trimmed.contains(QStringLiteral("english")) || trimmed.contains(QStringLiteral("английск"))) {
        return QStringLiteral("en");
    }
    if (trimmed == QStringLiteral("de") || trimmed.contains(QStringLiteral("deutsch")) || trimmed.contains(QStringLiteral("german"))) {
        return QStringLiteral("de");
    }
    if (trimmed == QStringLiteral("es") || trimmed.contains(QStringLiteral("español")) || trimmed.contains(QStringLiteral("espanol")) || trimmed.contains(QStringLiteral("spanish"))) {
        return QStringLiteral("es");
    }
    if (trimmed == QStringLiteral("fr") || trimmed.contains(QStringLiteral("français")) || trimmed.contains(QStringLiteral("francais")) || trimmed.contains(QStringLiteral("french"))) {
        return QStringLiteral("fr");
    }
    if (trimmed == QStringLiteral("id") || trimmed.contains(QStringLiteral("bahasa indonesia")) || trimmed.contains(QStringLiteral("indonesian"))) {
        return QStringLiteral("id");
    }
    if (trimmed == QStringLiteral("it") || trimmed.contains(QStringLiteral("italiano")) || trimmed.contains(QStringLiteral("italian"))) {
        return QStringLiteral("it");
    }
    if (trimmed == QStringLiteral("ja") || trimmed.contains(QStringLiteral("日本語")) || trimmed.contains(QStringLiteral("japanese"))) {
        return QStringLiteral("ja");
    }
    if (trimmed == QStringLiteral("pt-br") || trimmed.contains(QStringLiteral("português")) || trimmed.contains(QStringLiteral("portugues")) || trimmed.contains(QStringLiteral("portuguese")) || trimmed == QStringLiteral("pt")) {
        return trimmed.contains(QStringLiteral("br")) ? QStringLiteral("pt-br") : QStringLiteral("pt");
    }
    if (trimmed == QStringLiteral("zh-hans") || trimmed == QStringLiteral("zh-cn") || trimmed == QStringLiteral("zh") || trimmed.contains(QStringLiteral("简体中文")) || trimmed.contains(QStringLiteral("中文")) || trimmed.contains(QStringLiteral("chinese"))) {
        return (trimmed.contains(QStringLiteral("hans")) || trimmed.contains(QStringLiteral("cn")) || trimmed.contains(QStringLiteral("简体")))
                   ? QStringLiteral("zh-hans")
                   : QStringLiteral("zh");
    }
    return {};
}

QString SelectLocalizedReleaseNotes(const QString& raw, const QLocale& locale) {
    QString normalized = raw;
    normalized.replace(QStringLiteral("\r\n"), QStringLiteral("\n"));
    const QStringList lines = normalized.split(QLatin1Char('\n'));

    static const QRegularExpression kCommentTagRe(
        QStringLiteral("<!--\\s*lang\\s*:\\s*([a-zA-Z_-]+)\\s*-->"),
        QRegularExpression::CaseInsensitiveOption);
    static const QRegularExpression kCommentEndRe(
        QStringLiteral("<!--\\s*/lang\\s*-->"),
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
            if (trimmed.startsWith(QStringLiteral("## ")) || trimmed.startsWith(QStringLiteral("### "))) {
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

    const QString bcp = NormalizeLocaleCode(locale.bcp47Name());
    const QString base_lang = bcp.section(QLatin1Char('-'), 0, 0);
    QStringList candidates;
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

QFrame* BuildVersionPill(const QString& label_text, const QString& version_text, bool accent) {
    auto* frame = new QFrame();
    frame->setFrameShape(QFrame::StyledPanel);
    if (accent) {
        frame->setStyleSheet(QStringLiteral(
            "QFrame { border: 1px solid #00c8ff; border-radius: 8px; padding: 6px 10px; "
            "background-color: rgba(0, 200, 255, 36); }"));
    } else {
        frame->setStyleSheet(QStringLiteral(
            "QFrame { border: 1px solid rgba(130, 150, 190, 90); border-radius: 8px; "
            "padding: 6px 10px; }"));
    }

    auto* layout = new QVBoxLayout(frame);
    layout->setContentsMargins(6, 4, 6, 4);
    layout->setSpacing(2);

    auto* title_lbl = new QLabel(label_text);
    title_lbl->setAlignment(Qt::AlignCenter);
    title_lbl->setStyleSheet(QStringLiteral("border: none; background: transparent; font-size: 11px;"));

    auto* ver_lbl = new QLabel(version_text);
    ver_lbl->setAlignment(Qt::AlignCenter);
    ver_lbl->setStyleSheet(
        accent
            ? QStringLiteral("border: none; background: transparent; font-weight: bold; font-size: 14px; color: #00c8ff;")
            : QStringLiteral("border: none; background: transparent; font-weight: bold; font-size: 14px;"));

    layout->addWidget(title_lbl);
    layout->addWidget(ver_lbl);
    return frame;
}

} // namespace

UpdateDialog::UpdateDialog(const QString& current_version, const UpdateChecker::ReleaseInfo& info,
                           QWidget* parent)
    : QDialog(parent, Qt::WindowTitleHint | Qt::WindowCloseButtonHint | Qt::WindowSystemMenuHint) {
    setWindowTitle(tr("STORM 3DS Update Available"));
    setMinimumSize(540, 460);
    resize(580, 520);

    auto* root = new QVBoxLayout(this);
    root->setContentsMargins(16, 16, 16, 16);
    root->setSpacing(12);

    auto* header_label = new QLabel(QString::fromStdString(info.title));
    header_label->setStyleSheet(QStringLiteral("font-size: 16px; font-weight: bold;"));
    header_label->setWordWrap(true);
    root->addWidget(header_label);

    // Current -> New version transition row
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
    version_row->addWidget(BuildVersionPill(tr("Installed Version"), clean_cur, false), 1);

    auto* arrow_lbl = new QLabel(QStringLiteral("->"));
    arrow_lbl->setAlignment(Qt::AlignCenter);
    arrow_lbl->setStyleSheet(QStringLiteral("font-weight: bold; font-size: 15px; color: #00c8ff;"));
    version_row->addWidget(arrow_lbl);

    version_row->addWidget(BuildVersionPill(tr("New Version"), clean_new, true), 1);
    root->addLayout(version_row);

    // Metadata strip (Source, Archive Size, Date)
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
        meta_lbl->setStyleSheet(QStringLiteral("font-size: 11px; opacity: 0.85;"));
        meta_lbl->setWordWrap(true);
        root->addWidget(meta_lbl);
    }

    auto* notes_title = new QLabel(tr("Release Notes"));
    notes_title->setStyleSheet(QStringLiteral("font-weight: bold; font-size: 12px;"));
    root->addWidget(notes_title);

    auto* changelog_browser = new QTextBrowser(this);
    changelog_browser->setOpenExternalLinks(true);
    QString localized_notes = info.changelog_md.empty()
                                  ? QString()
                                  : SelectLocalizedReleaseNotes(QString::fromStdString(info.changelog_md), QLocale());
    const QString md_notes = localized_notes.trimmed().isEmpty()
                                 ? tr("STORM 3DS %1 is ready to download.").arg(clean_new)
                                 : localized_notes;
    changelog_browser->setMarkdown(md_notes);
    changelog_browser->setMinimumHeight(230);
    root->addWidget(changelog_browser, 1);

    // Clear full-word buttons
    auto* btn_row = new QHBoxLayout();
    btn_row->setSpacing(8);

    const QString primary_url = QString::fromStdString(
        info.download_url.empty() ? info.release_url : info.download_url);
    const QString page_url = QString::fromStdString(info.release_url);

    auto* download_btn = new QPushButton(tr("Download Update"), this);
    download_btn->setDefault(true);
    connect(download_btn, &QPushButton::clicked, this, [this, primary_url] {
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
