// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#pragma once

#include <memory>
#include <string>
#include <utility>
#include <vector>
#include <glad/glad.h>
#include "common/common_types.h"

#ifdef ENABLE_VULKAN
#include "video_core/renderer_vulkan/vk_platform.h"
#endif

namespace VideoCore {

struct LibrashaderParam {
    std::string name;
    std::string description;
    float initial = 0.0f;
    float minimum = 0.0f;
    float maximum = 1.0f;
    float step = 0.01f;
};

class StormLibrashader {
public:
    StormLibrashader();
    ~StormLibrashader();

#ifdef ENABLE_VULKAN
    bool ApplyVulkan(vk::Instance instance, vk::PhysicalDevice physical_device, vk::Device device,
                     vk::Queue queue, PFN_vkGetInstanceProcAddr get_proc_addr,
                     vk::CommandBuffer cmdbuf, vk::Image source_image, vk::Format source_format,
                     u32 source_width, u32 source_height, vk::Image output_image,
                     vk::Format output_format, u32 output_width, u32 output_height,
                     u32 frames_in_flight = 3);
    void ResetVulkan();
#endif

    bool ApplyOpenGL(GLuint source_tex, u32 source_width, u32 source_height, GLuint output_fbo,
                     u32 output_width, u32 output_height);
    void ResetOpenGL();

    static bool IsLibraryAvailable();
    static std::vector<LibrashaderParam> QueryPresetParameters(const std::string& preset_path);
    static std::vector<std::string> ConsumeDiagnostics();
    static std::string GetLastDiagnosticSummary();
    static void RequestClearHistory();
    static void RequestReload();
    static std::vector<std::pair<std::string, float>> ParseParameterOverrides(
        const std::string& raw);

private:
    struct Impl;
    std::unique_ptr<Impl> impl;
};

} // namespace VideoCore
