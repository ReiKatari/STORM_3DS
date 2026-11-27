// Copyright 2017-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <QIcon>
#include <QPushButton>
#include "aboutdialog.h"
#include "citra_qt/storm_theme.h"
#include "common/scm_rev.h"
#include "ui_aboutdialog.h"

AboutDialog::AboutDialog(QWidget* parent)
    : QDialog(parent, Qt::WindowTitleHint | Qt::WindowCloseButtonHint | Qt::WindowSystemMenuHint),
      ui(std::make_unique<Ui::AboutDialog>()) {
    ui->setupUi(this);
    ui->labelBuildInfo->setText(ui->labelBuildInfo->text().arg(
        QString::fromUtf8(Common::g_build_fullname), QString::fromUtf8(Common::g_scm_branch),
        QString::fromUtf8(Common::g_scm_desc), QString::fromUtf8(Common::g_build_date).left(10),
        QString::fromUtf8(Common::g_build_variant)));
    const QString link_hex = StormTheme::AccentLinkColor().name();
    QString links = ui->labelLinks->text();
    links.replace(QStringLiteral("#00c8ff"), link_hex, Qt::CaseInsensitive);
    ui->labelLinks->setText(links);
#ifdef ENABLE_QT_UPDATE_CHECKER
    auto* check_btn = ui->buttonBox->addButton(tr("Check for Updates..."), QDialogButtonBox::ActionRole);
    connect(check_btn, &QPushButton::clicked, this, [this] {
        emit CheckUpdatesRequested();
        accept();
    });
#endif
}

AboutDialog::~AboutDialog() = default;
