// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#pragma once

#include <atomic>
#include <memory>
#include <QDialog>
#include "citra_qt/update_checker.h"

class QLabel;
class QProgressBar;
class QPushButton;

class UpdateDialog : public QDialog {
    Q_OBJECT

public:
    explicit UpdateDialog(const QString& current_version, const UpdateChecker::ReleaseInfo& info,
                          QWidget* parent = nullptr);
    ~UpdateDialog() override;

private:
    void StartDownload(const QString& url, const QString& asset_name);

    QPushButton* download_btn = nullptr;
    QProgressBar* download_progress = nullptr;
    QLabel* download_status_label = nullptr;
    std::shared_ptr<std::atomic<bool>> cancel_flag;
};
