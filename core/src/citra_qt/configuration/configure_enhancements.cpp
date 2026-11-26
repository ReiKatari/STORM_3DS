// Copyright 2019-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

#include <algorithm>
#include <cmath>
#include <locale>
#include <sstream>
#include <unordered_map>
#include <QColorDialog>
#include <QCoreApplication>
#include <QDialog>
#include <QDialogButtonBox>
#include <QDir>
#include <QDoubleSpinBox>
#include <QFileDialog>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QListView>
#include <QPushButton>
#include <QScrollArea>
#include <QSlider>
#include <QVBoxLayout>
#include "citra_qt/configuration/configuration_shared.h"
#include "citra_qt/configuration/configure_enhancements.h"
#include "common/settings.h"
#include "ui_configure_enhancements.h"
#include "video_core/storm_librashader.h"
#ifdef ENABLE_OPENGL
#include "video_core/renderer_opengl/post_processing_opengl.h"
#endif

ConfigureEnhancements::ConfigureEnhancements(QWidget* parent)
    : QWidget(parent), ui(std::make_unique<Ui::ConfigureEnhancements>()) {
    ui->setupUi(this);
    PopulateRender3dComboBox();

    SetupPerGameUI();
    SetConfiguration();

    const auto graphics_api = Settings::GetWorkingGraphicsAPI();
    const bool hardware_graphics = graphics_api != Settings::GraphicsAPI::Software;
    ui->resolution_factor_combobox->setEnabled(hardware_graphics);
    ui->antialiasing_combobox->setEnabled(hardware_graphics);

    // TODO(wunk): MSAA requires GPU capabilities to be understood at config-time before the backend
    // has actually been created. Disable all UI MSAA options until this feature is available.
    // Hide all AntiAliasing options that involve MSAA.
    QListView* combobox_view = qobject_cast<QListView*>(ui->antialiasing_combobox->view());
    for (int item_index = 0; item_index < ui->antialiasing_combobox->count(); ++item_index) {
        if (Settings::GetAntiAliasingSampleCount(
                static_cast<Settings::AntiAliasingMethod>(item_index)) > 1) {
            combobox_view->setRowHidden(item_index, true);
            ui->antialiasing_combobox->setItemData(item_index, 0, Qt::UserRole - 1);
        }
    }

    connect(ui->render_3d_combobox, qOverload<int>(&QComboBox::currentIndexChanged), this,
            [this](int currentIndex) {
                const auto option = static_cast<Settings::StereoRenderOption>(
                    ui->render_3d_combobox->itemData(currentIndex).toInt());
                if (option == Settings::StereoRenderOption::GyroParallax &&
                    ui->factor_3d->value() == 0) {
                    ui->factor_3d->setValue(50);
                }
                updateShaders(option);
            });

    ui->toggle_preload_textures->setEnabled(ui->toggle_custom_textures->isChecked());
    ui->toggle_async_custom_loading->setEnabled(ui->toggle_custom_textures->isChecked());
    connect(ui->toggle_custom_textures, &QCheckBox::toggled, this, [this] {
        ui->toggle_preload_textures->setEnabled(ui->toggle_custom_textures->isChecked());
        ui->toggle_async_custom_loading->setEnabled(ui->toggle_custom_textures->isChecked());
        if (!ui->toggle_preload_textures->isEnabled())
            ui->toggle_preload_textures->setChecked(false);
    });

    auto syncRetroArchUiEnabled = [this] {
        const bool enabled = ui->retroarch_shader_enable->isChecked();
        const bool has_path = !ui->retroarch_shader_path->text().trimmed().isEmpty();
        ui->retroarch_shader_path->setEnabled(enabled);
        ui->retroarch_shader_browse->setEnabled(enabled);
        ui->retroarch_shader_clear->setEnabled(enabled && has_path);
        ui->retroarch_shader_params_button->setEnabled(enabled && has_path);
        ui->retroarch_shader_clear_history->setEnabled(enabled && has_path);
        UpdateRetroArchStatusLabel();
    };
    connect(ui->retroarch_shader_enable, &QCheckBox::toggled, this, syncRetroArchUiEnabled);
    connect(ui->retroarch_shader_path, &QLineEdit::textChanged, this, syncRetroArchUiEnabled);
    connect(ui->retroarch_shader_browse, &QPushButton::clicked, this, [this] {
        QString start_dir = QCoreApplication::applicationDirPath() + QStringLiteral("/shaders");
        if (!QDir(start_dir).exists()) {
            start_dir = QCoreApplication::applicationDirPath();
        }
        const QString current = ui->retroarch_shader_path->text().trimmed();
        if (!current.isEmpty()) {
            start_dir = QFileInfo(current).absolutePath();
        }
        const QString picked = QFileDialog::getOpenFileName(
            this, tr("Select RetroArch Shader Preset"), start_dir,
            tr("RetroArch Shader Presets (*.slangp);;All Files (*)"));
        if (!picked.isEmpty()) {
            ui->retroarch_shader_path->setText(QDir::toNativeSeparators(picked));
            ui->retroarch_shader_enable->setChecked(true);
        }
    });
    connect(ui->retroarch_shader_clear, &QPushButton::clicked, this, [this] {
        ui->retroarch_shader_path->clear();
        pending_librashader_parameters.clear();
    });
    connect(ui->retroarch_shader_params_button, &QPushButton::clicked, this,
            &ConfigureEnhancements::OpenRetroArchParametersDialog);
    connect(ui->retroarch_shader_clear_history, &QPushButton::clicked, this, [this] {
        VideoCore::StormLibrashader::RequestClearHistory();
        UpdateRetroArchStatusLabel();
    });
    syncRetroArchUiEnabled();
}

ConfigureEnhancements::~ConfigureEnhancements() = default;

void ConfigureEnhancements::SetConfiguration() {

    if (!Settings::IsConfiguringGlobal()) {
        ConfigurationShared::SetPerGameSetting(ui->resolution_factor_combobox,
                                               &Settings::values.resolution_factor);
        ConfigurationShared::SetPerGameSetting(ui->antialiasing_combobox,
                                               &Settings::values.antialiasing);
        ConfigurationShared::SetPerGameSetting(ui->texture_filter_combobox,
                                               &Settings::values.texture_filter);
        ConfigurationShared::SetHighlight(ui->widget_texture_filter,
                                          !Settings::values.texture_filter.UsingGlobal());
    } else {
        ui->resolution_factor_combobox->setCurrentIndex(
            Settings::values.resolution_factor.GetValue());
        ui->antialiasing_combobox->setCurrentIndex(
            static_cast<int>(Settings::values.antialiasing.GetValue()));
        ui->texture_filter_combobox->setCurrentIndex(
            static_cast<int>(Settings::values.texture_filter.GetValue()));
    }

    const int render_3d_value = static_cast<int>(Settings::values.render_3d.GetValue());
    const int render_3d_idx = ui->render_3d_combobox->findData(render_3d_value);
    ui->render_3d_combobox->setCurrentIndex(std::max(0, render_3d_idx));
    ui->swap_eyes_3d->setChecked(Settings::values.swap_eyes_3d.GetValue());
    ui->factor_3d->setValue(Settings::values.factor_3d.GetValue());
    ui->mono_rendering_eye->setCurrentIndex(
        static_cast<int>(Settings::values.mono_render_option.GetValue()));
    updateShaders(Settings::values.render_3d.GetValue());
    ui->toggle_linear_filter->setChecked(Settings::values.filter_mode.GetValue());
    ui->use_integer_scaling->setChecked(Settings::values.use_integer_scaling.GetValue());
    ui->toggle_dump_textures->setChecked(Settings::values.dump_textures.GetValue());
    ui->toggle_custom_textures->setChecked(Settings::values.custom_textures.GetValue());
    ui->toggle_preload_textures->setChecked(Settings::values.preload_textures.GetValue());
    ui->toggle_async_custom_loading->setChecked(Settings::values.async_custom_loading.GetValue());
    ui->disable_right_eye_render->setChecked(Settings::values.disable_right_eye_render.GetValue());
    ui->retroarch_shader_enable->setChecked(Settings::values.librashader_enabled.GetValue());
    ui->retroarch_shader_path->setText(
        QString::fromStdString(Settings::values.librashader_preset_path.GetValue()));
    pending_librashader_parameters = Settings::values.librashader_parameters.GetValue();
    UpdateRetroArchStatusLabel();
}

void ConfigureEnhancements::updateShaders(Settings::StereoRenderOption stereo_option) {
    ui->shader_combobox->clear();
    ui->shader_combobox->setEnabled(true);

    if (stereo_option == Settings::StereoRenderOption::Interlaced ||
        stereo_option == Settings::StereoRenderOption::ReverseInterlaced) {
        ui->shader_combobox->addItem(QStringLiteral("Horizontal (builtin)"));
        ui->shader_combobox->setCurrentIndex(0);
        ui->shader_combobox->setEnabled(false);
        return;
    }

    std::string current_shader;
    if (stereo_option == Settings::StereoRenderOption::Anaglyph) {
        ui->shader_combobox->addItem(QStringLiteral("Dubois (builtin)"));
        current_shader = Settings::values.anaglyph_shader_name.GetValue();
    } else {
        ui->shader_combobox->addItem(QStringLiteral("None (builtin)"));
        current_shader = Settings::values.pp_shader_name.GetValue();
    }

    ui->shader_combobox->setCurrentIndex(0);

#ifdef ENABLE_OPENGL
    for (const auto& shader : OpenGL::GetPostProcessingShaderList(
             stereo_option == Settings::StereoRenderOption::Anaglyph)) {
        ui->shader_combobox->addItem(QString::fromStdString(shader));
        if (current_shader == shader)
            ui->shader_combobox->setCurrentIndex(ui->shader_combobox->count() - 1);
    }
#endif
}

void ConfigureEnhancements::PopulateRender3dComboBox() {
    const QVariant prev_data = ui->render_3d_combobox->currentData();
    ui->render_3d_combobox->blockSignals(true);
    ui->render_3d_combobox->clear();
    ui->render_3d_combobox->addItem(tr("Off"),
                                    static_cast<int>(Settings::StereoRenderOption::Off));
    ui->render_3d_combobox->addItem(
        tr("3D Depth Parallax (Single Screen)"),
        static_cast<int>(Settings::StereoRenderOption::GyroParallax));
    ui->render_3d_combobox->addItem(tr("Anaglyph"),
                                    static_cast<int>(Settings::StereoRenderOption::Anaglyph));
    ui->render_3d_combobox->addItem(tr("Side by Side"),
                                    static_cast<int>(Settings::StereoRenderOption::SideBySide));
    ui->render_3d_combobox->addItem(
        tr("Side by Side Full Width"),
        static_cast<int>(Settings::StereoRenderOption::SideBySideFull));
    ui->render_3d_combobox->addItem(tr("Interlaced"),
                                    static_cast<int>(Settings::StereoRenderOption::Interlaced));
    ui->render_3d_combobox->addItem(
        tr("Reverse Interlaced"),
        static_cast<int>(Settings::StereoRenderOption::ReverseInterlaced));
    ui->render_3d_combobox->addItem(tr("Cardboard VR"),
                                    static_cast<int>(Settings::StereoRenderOption::CardboardVR));
    if (prev_data.isValid()) {
        const int idx = ui->render_3d_combobox->findData(prev_data);
        if (idx >= 0) {
            ui->render_3d_combobox->setCurrentIndex(idx);
        }
    }
    ui->render_3d_combobox->blockSignals(false);
}

void ConfigureEnhancements::RetranslateUI() {
    ui->retranslateUi(this);
    PopulateRender3dComboBox();
}

void ConfigureEnhancements::ApplyConfiguration() {
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.resolution_factor,
                                             ui->resolution_factor_combobox);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.antialiasing,
                                             ui->antialiasing_combobox);
    Settings::values.render_3d = static_cast<Settings::StereoRenderOption>(
        ui->render_3d_combobox->currentData().toInt());
    Settings::values.swap_eyes_3d = ui->swap_eyes_3d->isChecked();
    Settings::values.factor_3d = ui->factor_3d->value();
    Settings::values.mono_render_option =
        static_cast<Settings::MonoRenderOption>(ui->mono_rendering_eye->currentIndex());
    if (Settings::values.render_3d.GetValue() == Settings::StereoRenderOption::Anaglyph) {
        Settings::values.anaglyph_shader_name =
            ui->shader_combobox->itemText(ui->shader_combobox->currentIndex()).toStdString();
    } else if (Settings::values.render_3d.GetValue() == Settings::StereoRenderOption::Off) {
        Settings::values.pp_shader_name =
            ui->shader_combobox->itemText(ui->shader_combobox->currentIndex()).toStdString();
    }
    Settings::values.disable_right_eye_render = ui->disable_right_eye_render->isChecked();

    ConfigurationShared::ApplyPerGameSetting(&Settings::values.filter_mode,
                                             ui->toggle_linear_filter, linear_filter);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.use_integer_scaling,
                                             ui->use_integer_scaling, use_integer_scaling);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.texture_filter,
                                             ui->texture_filter_combobox);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.dump_textures,
                                             ui->toggle_dump_textures, dump_textures);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.custom_textures,
                                             ui->toggle_custom_textures, custom_textures);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.preload_textures,
                                             ui->toggle_preload_textures, preload_textures);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.async_custom_loading,
                                             ui->toggle_async_custom_loading, async_custom_loading);
    ConfigurationShared::ApplyPerGameSetting(&Settings::values.disable_right_eye_render,
                                             ui->disable_right_eye_render,
                                             disable_right_eye_render);
    if (Settings::IsConfiguringGlobal()) {
        const bool next_enabled = ui->retroarch_shader_enable->isChecked();
        const std::string next_path =
            QDir::toNativeSeparators(ui->retroarch_shader_path->text().trimmed()).toStdString();
        const bool changed = (Settings::values.librashader_enabled.GetValue() != next_enabled) ||
                             (Settings::values.librashader_preset_path.GetValue() != next_path);
        Settings::values.librashader_enabled.SetValue(next_enabled);
        Settings::values.librashader_preset_path.SetValue(next_path);
        Settings::values.librashader_parameters.SetValue(pending_librashader_parameters);
        if (changed) {
            VideoCore::StormLibrashader::RequestReload();
        }
    }
}

void ConfigureEnhancements::UpdateRetroArchStatusLabel() {
    if (!ui->retroarch_shader_enable->isChecked()) {
        ui->retroarch_shader_status->setText(tr("RetroArch shaders disabled."));
        return;
    }
    if (!VideoCore::StormLibrashader::IsLibraryAvailable()) {
        ui->retroarch_shader_status->setText(
            tr("librashader.dll not found next to executable."));
        return;
    }
    const std::string summary = VideoCore::StormLibrashader::GetLastDiagnosticSummary();
    if (!summary.empty()) {
        ui->retroarch_shader_status->setText(QString::fromStdString(summary));
    } else if (ui->retroarch_shader_path->text().trimmed().isEmpty()) {
        ui->retroarch_shader_status->setText(
            tr("Librashader ready. Select a .slangp preset."));
    } else {
        ui->retroarch_shader_status->setText(tr("Librashader ready."));
    }
}

void ConfigureEnhancements::OpenRetroArchParametersDialog() {
    const std::string path =
        QDir::toNativeSeparators(ui->retroarch_shader_path->text().trimmed()).toStdString();
    if (path.empty()) {
        return;
    }
    const auto params = VideoCore::StormLibrashader::QueryPresetParameters(path);
    std::unordered_map<std::string, float> overrides;
    for (const auto& [k, v] :
         VideoCore::StormLibrashader::ParseParameterOverrides(pending_librashader_parameters)) {
        overrides[k] = v;
    }

    QDialog dialog(this);
    dialog.setWindowTitle(tr("RetroArch Shader Parameters"));
    dialog.resize(480, 420);
    auto* main_layout = new QVBoxLayout(&dialog);

    if (params.empty()) {
        main_layout->addWidget(
            new QLabel(tr("No adjustable parameters reported for this preset."), &dialog));
        auto* buttons = new QDialogButtonBox(QDialogButtonBox::Close, &dialog);
        connect(buttons, &QDialogButtonBox::rejected, &dialog, &QDialog::reject);
        main_layout->addWidget(buttons);
        dialog.exec();
        return;
    }

    auto* scroll = new QScrollArea(&dialog);
    scroll->setWidgetResizable(true);
    auto* container = new QWidget(scroll);
    auto* grid = new QGridLayout(container);

    struct RowBinding {
        std::string name;
        float initial;
        float min_val;
        float step_val;
        QSlider* slider;
        QDoubleSpinBox* spin;
    };
    std::vector<RowBinding> bindings;
    bindings.reserve(params.size());

    auto serialize = [&]() -> std::string {
        std::ostringstream oss;
        oss.imbue(std::locale::classic());
        bool first = true;
        for (const auto& b : bindings) {
            const float val = static_cast<float>(b.spin->value());
            if (std::abs(val - b.initial) > 1e-4f) {
                if (!first) {
                    oss << '\n';
                }
                oss << b.name << '=' << val;
                first = false;
            }
        }
        return oss.str();
    };

    for (std::size_t i = 0; i < params.size(); ++i) {
        const auto& p = params[i];
        const int row = static_cast<int>(i);
        const float span = std::max(p.maximum - p.minimum, p.step);
        const int steps = std::clamp(static_cast<int>(std::llround(span / p.step)), 1, 2000);
        const float initial_clamped = std::clamp(
            overrides.count(p.name) ? overrides[p.name] : p.initial, p.minimum, p.maximum);

        grid->addWidget(new QLabel(QString::fromStdString(p.description), container), row, 0);

        auto* slider = new QSlider(Qt::Horizontal, container);
        slider->setRange(0, steps);
        slider->setValue(
            std::clamp(static_cast<int>(std::llround(((initial_clamped - p.minimum) / span) * steps)),
                       0, steps));
        grid->addWidget(slider, row, 1);

        auto* spin = new QDoubleSpinBox(container);
        spin->setRange(p.minimum, p.maximum);
        spin->setSingleStep(p.step);
        spin->setDecimals(p.step >= 1.0f ? 0 : (p.step >= 0.1f ? 1 : 3));
        spin->setValue(initial_clamped);
        grid->addWidget(spin, row, 2);

        bindings.push_back(RowBinding{p.name, p.initial, p.minimum, p.step, slider, spin});

        connect(slider, &QSlider::valueChanged, &dialog,
                [spin, p, span, steps, &serialize](int pos) {
                    const double val = std::clamp(
                        static_cast<double>(p.minimum) + (static_cast<double>(pos) / steps) * span,
                        static_cast<double>(p.minimum), static_cast<double>(p.maximum));
                    if (std::abs(spin->value() - val) > 1e-4) {
                        spin->blockSignals(true);
                        spin->setValue(val);
                        spin->blockSignals(false);
                    }
                    Settings::values.librashader_parameters.SetValue(serialize());
                });
        connect(spin, qOverload<double>(&QDoubleSpinBox::valueChanged), &dialog,
                [slider, p, span, steps, &serialize](double val) {
                    const int pos = std::clamp(
                        static_cast<int>(std::llround(((val - p.minimum) / span) * steps)), 0,
                        steps);
                    if (slider->value() != pos) {
                        slider->blockSignals(true);
                        slider->setValue(pos);
                        slider->blockSignals(false);
                    }
                    Settings::values.librashader_parameters.SetValue(serialize());
                });
    }

    scroll->setWidget(container);
    main_layout->addWidget(scroll);

    auto* buttons = new QDialogButtonBox(
        QDialogButtonBox::Ok | QDialogButtonBox::Cancel | QDialogButtonBox::RestoreDefaults,
        &dialog);
    connect(buttons->button(QDialogButtonBox::RestoreDefaults), &QPushButton::clicked, &dialog,
            [&]() {
                for (const auto& b : bindings) {
                    b.spin->setValue(b.initial);
                }
                Settings::values.librashader_parameters.SetValue(serialize());
            });
    connect(buttons, &QDialogButtonBox::accepted, &dialog, [&]() {
        pending_librashader_parameters = serialize();
        Settings::values.librashader_parameters.SetValue(pending_librashader_parameters);
        dialog.accept();
    });
    connect(buttons, &QDialogButtonBox::rejected, &dialog, [&]() {
        Settings::values.librashader_parameters.SetValue(pending_librashader_parameters);
        dialog.reject();
    });
    main_layout->addWidget(buttons);
    dialog.exec();
}

void ConfigureEnhancements::SetupPerGameUI() {
    // Block the global settings if a game is currently running that overrides them
    if (Settings::IsConfiguringGlobal()) {
        ui->widget_resolution->setEnabled(Settings::values.resolution_factor.UsingGlobal());
        ui->widget_antialiasing->setEnabled(Settings::values.antialiasing.UsingGlobal());
        ui->widget_texture_filter->setEnabled(Settings::values.texture_filter.UsingGlobal());
        ui->toggle_linear_filter->setEnabled(Settings::values.filter_mode.UsingGlobal());
        ui->use_integer_scaling->setEnabled(Settings::values.use_integer_scaling.UsingGlobal());
        ui->toggle_dump_textures->setEnabled(Settings::values.dump_textures.UsingGlobal());
        ui->toggle_custom_textures->setEnabled(Settings::values.custom_textures.UsingGlobal());
        ui->toggle_preload_textures->setEnabled(Settings::values.preload_textures.UsingGlobal());
        ui->toggle_async_custom_loading->setEnabled(
            Settings::values.async_custom_loading.UsingGlobal());
        ui->disable_right_eye_render->setEnabled(
            Settings::values.disable_right_eye_render.UsingGlobal());
        return;
    }

    ui->render_3d_combobox->setEnabled(false);
    ui->factor_3d->setEnabled(false);
    ui->mono_rendering_eye->setEnabled(false);

    ui->widget_shader->setVisible(false);
    ui->widget_retroarch_shader->setVisible(false);

    ConfigurationShared::SetColoredTristate(ui->toggle_linear_filter, Settings::values.filter_mode,
                                            linear_filter);
    ConfigurationShared::SetColoredTristate(
        ui->use_integer_scaling, Settings::values.use_integer_scaling, use_integer_scaling);
    ConfigurationShared::SetColoredTristate(ui->toggle_dump_textures,
                                            Settings::values.dump_textures, dump_textures);
    ConfigurationShared::SetColoredTristate(ui->toggle_custom_textures,
                                            Settings::values.custom_textures, custom_textures);
    ConfigurationShared::SetColoredTristate(ui->toggle_preload_textures,
                                            Settings::values.preload_textures, preload_textures);
    ConfigurationShared::SetColoredTristate(ui->toggle_async_custom_loading,
                                            Settings::values.async_custom_loading,
                                            async_custom_loading);
    ConfigurationShared::SetColoredTristate(ui->disable_right_eye_render,
                                            Settings::values.disable_right_eye_render,
                                            disable_right_eye_render);

    ConfigurationShared::SetColoredComboBox(
        ui->resolution_factor_combobox, ui->widget_resolution,
        static_cast<int>(Settings::values.resolution_factor.GetValue(true)));

    ConfigurationShared::SetColoredComboBox(
        ui->antialiasing_combobox, ui->widget_antialiasing,
        static_cast<int>(Settings::values.antialiasing.GetValue(true)));

    ConfigurationShared::SetColoredComboBox(
        ui->texture_filter_combobox, ui->widget_texture_filter,
        static_cast<int>(Settings::values.texture_filter.GetValue(true)));
}
