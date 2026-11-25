// Copyright 2026 STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#pragma once

#include <QDialog>
#include "citra_qt/update_checker.h"

class UpdateDialog : public QDialog {
    Q_OBJECT

public:
    explicit UpdateDialog(const QString& current_version, const UpdateChecker::ReleaseInfo& info,
                          QWidget* parent = nullptr);
    ~UpdateDialog() override = default;
};
