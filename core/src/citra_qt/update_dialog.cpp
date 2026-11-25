// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <QDesktopServices>
#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QTextBrowser>
#include <QUrl>
#include <QVBoxLayout>
#include "citra_qt/update_dialog.h"

namespace {

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
    version_row->addWidget(BuildVersionPill(tr("Installed Version"), QStringLiteral("v") + clean_cur, false), 1);

    auto* arrow_lbl = new QLabel(QStringLiteral("->"));
    arrow_lbl->setAlignment(Qt::AlignCenter);
    arrow_lbl->setStyleSheet(QStringLiteral("font-weight: bold; font-size: 15px; color: #00c8ff;"));
    version_row->addWidget(arrow_lbl);

    version_row->addWidget(BuildVersionPill(tr("New Version"), QStringLiteral("v") + clean_new, true), 1);
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
    const QString md_notes = info.changelog_md.empty()
                                 ? tr("STORM 3DS %1 is ready to download.").arg(clean_new)
                                 : QString::fromStdString(info.changelog_md);
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
