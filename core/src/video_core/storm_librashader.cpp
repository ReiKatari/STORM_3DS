// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#include "video_core/storm_librashader.h"
#include "common/logging/log.h"
#include "common/settings.h"

#include <algorithm>
#include <dlfcn.h>
#include <mutex>
#include <EGL/egl.h>

#if defined(ENABLE_VULKAN)
#define LIBRA_RUNTIME_VULKAN 1
#endif
#define LIBRA_RUNTIME_OPENGL 1
#include "librashader.h"

namespace VideoCore {

namespace {

struct LibraApi {
    void* handle = nullptr;
    bool attempted = false;

    PFN_libra_error_free error_free = nullptr;
    PFN_libra_error_write error_write = nullptr;
    PFN_libra_error_free_string error_free_string = nullptr;
    PFN_libra_preset_ctx_create preset_ctx_create = nullptr;
    PFN_libra_preset_ctx_free preset_ctx_free = nullptr;
    PFN_libra_preset_ctx_set_runtime preset_ctx_set_runtime = nullptr;
    PFN_libra_preset_ctx_set_core_name preset_ctx_set_core_name = nullptr;
    PFN_libra_preset_ctx_set_core_aspect_orientation preset_ctx_set_core_aspect_orientation = nullptr;
    PFN_libra_preset_ctx_set_view_aspect_orientation preset_ctx_set_view_aspect_orientation = nullptr;
    PFN_libra_preset_ctx_set_allow_rotation preset_ctx_set_allow_rotation = nullptr;
    PFN_libra_preset_create_with_options preset_create_with_options = nullptr;
    PFN_libra_preset_free preset_free = nullptr;

    PFN_libra_gl_filter_chain_create gl_chain_create = nullptr;
    PFN_libra_gl_filter_chain_frame gl_chain_frame = nullptr;
    PFN_libra_gl_filter_chain_free gl_chain_free = nullptr;

#ifdef ENABLE_VULKAN
    PFN_libra_vk_filter_chain_create vk_chain_create = nullptr;
    PFN_libra_vk_filter_chain_frame vk_chain_frame = nullptr;
    PFN_libra_vk_filter_chain_free vk_chain_free = nullptr;
#endif

    bool EnsureLoaded() {
        if (handle != nullptr) {
            return true;
        }
        if (attempted) {
            return false;
        }
        attempted = true;
        handle = dlopen("liblibrashader.so", RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOG_WARNING(Render, "librashader not loaded (optional): {}", dlerror());
            return false;
        }
        auto load = [&](const char* sym) -> void* { return dlsym(handle, sym); };
        error_free = reinterpret_cast<PFN_libra_error_free>(load("libra_error_free"));
        error_write = reinterpret_cast<PFN_libra_error_write>(load("libra_error_write"));
        error_free_string = reinterpret_cast<PFN_libra_error_free_string>(load("libra_error_free_string"));
        preset_ctx_create = reinterpret_cast<PFN_libra_preset_ctx_create>(load("libra_preset_ctx_create"));
        preset_ctx_free = reinterpret_cast<PFN_libra_preset_ctx_free>(load("libra_preset_ctx_free"));
        preset_ctx_set_runtime = reinterpret_cast<PFN_libra_preset_ctx_set_runtime>(load("libra_preset_ctx_set_runtime"));
        preset_ctx_set_core_name = reinterpret_cast<PFN_libra_preset_ctx_set_core_name>(load("libra_preset_ctx_set_core_name"));
        preset_ctx_set_core_aspect_orientation = reinterpret_cast<PFN_libra_preset_ctx_set_core_aspect_orientation>(load("libra_preset_ctx_set_core_aspect_orientation"));
        preset_ctx_set_view_aspect_orientation = reinterpret_cast<PFN_libra_preset_ctx_set_view_aspect_orientation>(load("libra_preset_ctx_set_view_aspect_orientation"));
        preset_ctx_set_allow_rotation = reinterpret_cast<PFN_libra_preset_ctx_set_allow_rotation>(load("libra_preset_ctx_set_allow_rotation"));
        preset_create_with_options = reinterpret_cast<PFN_libra_preset_create_with_options>(load("libra_preset_create_with_options"));
        preset_free = reinterpret_cast<PFN_libra_preset_free>(load("libra_preset_free"));
        gl_chain_create = reinterpret_cast<PFN_libra_gl_filter_chain_create>(load("libra_gl_filter_chain_create"));
        gl_chain_frame = reinterpret_cast<PFN_libra_gl_filter_chain_frame>(load("libra_gl_filter_chain_frame"));
        gl_chain_free = reinterpret_cast<PFN_libra_gl_filter_chain_free>(load("libra_gl_filter_chain_free"));
#ifdef ENABLE_VULKAN
        vk_chain_create = reinterpret_cast<PFN_libra_vk_filter_chain_create>(load("libra_vk_filter_chain_create"));
        vk_chain_frame = reinterpret_cast<PFN_libra_vk_filter_chain_frame>(load("libra_vk_filter_chain_frame"));
        vk_chain_free = reinterpret_cast<PFN_libra_vk_filter_chain_free>(load("libra_vk_filter_chain_free"));
#endif
        return preset_ctx_create && preset_create_with_options && preset_free;
    }

    void LogErr(const char* ctx, libra_error_t err) {
        if (!err) return;
        char* msg = nullptr;
        if (error_write && error_write(err, &msg) == 0 && msg) {
            LOG_ERROR(Render, "librashader {} failed: {}", ctx, msg);
            if (error_free_string) error_free_string(&msg);
        } else {
            LOG_ERROR(Render, "librashader {} failed", ctx);
        }
        if (error_free) error_free(&err);
    }
};

LibraApi& GetLibra() {
    static LibraApi api;
    return api;
}

} // namespace

struct StormLibrashader::Impl {
    std::mutex mutex;
    std::string active_preset;
    std::string failed_preset;
    u64 frame_count = 0;

#ifdef ENABLE_VULKAN
    libra_vk_filter_chain_t vk_chain = nullptr;
    vk::Device vk_device{};
#endif

    libra_gl_filter_chain_t gl_chain = nullptr;
    GLuint gl_out_tex = 0;
    GLuint gl_out_fbo = 0;
    u32 gl_out_w = 0;
    u32 gl_out_h = 0;
};

StormLibrashader::StormLibrashader() : impl(std::make_unique<Impl>()) {}

StormLibrashader::~StormLibrashader() {
#ifdef ENABLE_VULKAN
    ResetVulkan();
#endif
    ResetOpenGL();
}

#ifdef ENABLE_VULKAN
void StormLibrashader::ResetVulkan() {
    std::scoped_lock lock{impl->mutex};
    if (impl->vk_chain) {
        auto& api = GetLibra();
        if (api.vk_chain_free) {
            api.LogErr("vk_chain_free", api.vk_chain_free(&impl->vk_chain));
        }
        impl->vk_chain = nullptr;
    }
    impl->active_preset.clear();
    impl->frame_count = 0;
}

bool StormLibrashader::ApplyVulkan(vk::Instance instance, vk::PhysicalDevice physical_device,
                                   vk::Device device, vk::Queue queue,
                                   PFN_vkGetInstanceProcAddr get_proc_addr,
                                   vk::CommandBuffer cmdbuf, vk::Image source_image,
                                   vk::Format source_format, u32 source_width, u32 source_height,
                                   vk::Image output_image, vk::Format output_format,
                                   u32 output_width, u32 output_height) {
    if (!Settings::values.librashader_enabled.GetValue()) {
        if (impl->vk_chain) ResetVulkan();
        return false;
    }
    const std::string preset_path = Settings::values.librashader_preset_path.GetValue();
    if (preset_path.empty() || source_width == 0 || source_height == 0 || output_width == 0 ||
        output_height == 0) {
        return false;
    }

    auto& api = GetLibra();
    if (!api.EnsureLoaded() || !api.vk_chain_create || !api.vk_chain_frame) {
        return false;
    }

    std::scoped_lock lock{impl->mutex};
    if (!impl->failed_preset.empty() && impl->failed_preset == preset_path) {
        return false;
    }

    if (impl->vk_chain && (impl->active_preset != preset_path || impl->vk_device != device)) {
        if (api.vk_chain_free) {
            api.LogErr("vk_chain_free", api.vk_chain_free(&impl->vk_chain));
        }
        impl->vk_chain = nullptr;
        impl->active_preset.clear();
        impl->frame_count = 0;
    }

    if (!impl->vk_chain) {
        libra_preset_ctx_t ctx = nullptr;
        if (libra_error_t err = api.preset_ctx_create(&ctx)) {
            api.LogErr("preset_ctx_create", err);
            impl->failed_preset = preset_path;
            return false;
        }
        if (api.preset_ctx_set_runtime)
            api.preset_ctx_set_runtime(&ctx, LIBRA_PRESET_CTX_RUNTIME_VULKAN);
        if (api.preset_ctx_set_core_name)
            api.preset_ctx_set_core_name(&ctx, "STORM 3DS");
        if (api.preset_ctx_set_core_aspect_orientation)
            api.preset_ctx_set_core_aspect_orientation(&ctx, LIBRA_PRESET_CTX_ORIENTATION_HORIZONTAL);
        if (api.preset_ctx_set_view_aspect_orientation)
            api.preset_ctx_set_view_aspect_orientation(&ctx, LIBRA_PRESET_CTX_ORIENTATION_HORIZONTAL);
        if (api.preset_ctx_set_allow_rotation)
            api.preset_ctx_set_allow_rotation(&ctx, false);

        libra_preset_opt_t p_opt{};
        p_opt.version = LIBRASHADER_CURRENT_ABI;
        p_opt.original_aspect_uniforms = true;
        p_opt.frametime_uniforms = true;

        libra_shader_preset_t preset = nullptr;
        if (libra_error_t err = api.preset_create_with_options(preset_path.c_str(), &ctx, &p_opt, &preset)) {
            api.LogErr("preset_create_with_options", err);
            if (ctx && api.preset_ctx_free) api.preset_ctx_free(&ctx);
            impl->failed_preset = preset_path;
            return false;
        }

        libra_device_vk_t dev{};
        dev.physical_device = static_cast<VkPhysicalDevice>(physical_device);
        dev.instance = static_cast<VkInstance>(instance);
        dev.device = static_cast<VkDevice>(device);
        dev.queue = static_cast<VkQueue>(queue);
        dev.entry = get_proc_addr;

        filter_chain_vk_opt_t c_opt{};
        c_opt.version = LIBRASHADER_CURRENT_ABI;
        c_opt.frames_in_flight = 3;
        c_opt.force_no_mipmaps = false;
        c_opt.use_dynamic_rendering = false;
        c_opt.disable_cache = false;

        if (libra_error_t err = api.vk_chain_create(&preset, dev, &c_opt, &impl->vk_chain)) {
            api.LogErr("vk_filter_chain_create", err);
            if (preset) api.preset_free(&preset);
            impl->failed_preset = preset_path;
            return false;
        }
        impl->active_preset = preset_path;
        impl->vk_device = device;
        impl->failed_preset.clear();
        impl->frame_count = 0;
    }

    impl->frame_count++;
    libra_image_vk_t src{
        .handle = static_cast<VkImage>(source_image),
        .format = static_cast<VkFormat>(source_format),
        .width = source_width,
        .height = source_height,
    };
    libra_image_vk_t dst{
        .handle = static_cast<VkImage>(output_image),
        .format = static_cast<VkFormat>(output_format),
        .width = output_width,
        .height = output_height,
    };
    libra_viewport_t vp{
        .x = 0.0f,
        .y = 0.0f,
        .width = output_width,
        .height = output_height,
    };
    frame_vk_opt_t f_opt{};
    f_opt.version = LIBRASHADER_CURRENT_ABI;
    f_opt.clear_history = impl->frame_count <= 1;
    f_opt.frame_direction = 1;
    f_opt.rotation = 0;
    f_opt.total_subframes = 1;
    f_opt.current_subframe = 1;
    f_opt.aspect_ratio = static_cast<float>(output_width) / std::max(1.0f, static_cast<float>(output_height));
    f_opt.frames_per_second = 60.0f;
    f_opt.frametime_delta = 16;

    if (libra_error_t err = api.vk_chain_frame(&impl->vk_chain, static_cast<VkCommandBuffer>(cmdbuf),
                                               static_cast<size_t>(impl->frame_count), src, dst, &vp,
                                               nullptr, &f_opt)) {
        api.LogErr("vk_filter_chain_frame", err);
        impl->failed_preset = preset_path;
        return false;
    }
    return true;
}
#endif

void StormLibrashader::ResetOpenGL() {
    std::scoped_lock lock{impl->mutex};
    if (impl->gl_chain) {
        auto& api = GetLibra();
        if (api.gl_chain_free) {
            api.LogErr("gl_chain_free", api.gl_chain_free(&impl->gl_chain));
        }
        impl->gl_chain = nullptr;
    }
    if (impl->gl_out_fbo) {
        glDeleteFramebuffers(1, &impl->gl_out_fbo);
        impl->gl_out_fbo = 0;
    }
    if (impl->gl_out_tex) {
        glDeleteTextures(1, &impl->gl_out_tex);
        impl->gl_out_tex = 0;
    }
    impl->gl_out_w = 0;
    impl->gl_out_h = 0;
    impl->active_preset.clear();
    impl->frame_count = 0;
}

bool StormLibrashader::ApplyOpenGL(GLuint source_tex, u32 source_width, u32 source_height,
                                   GLuint output_fbo, u32 output_width, u32 output_height) {
    if (!Settings::values.librashader_enabled.GetValue()) {
        if (impl->gl_chain) ResetOpenGL();
        return false;
    }
    const std::string preset_path = Settings::values.librashader_preset_path.GetValue();
    if (preset_path.empty() || source_tex == 0 || source_width == 0 || source_height == 0 ||
        output_width == 0 || output_height == 0) {
        return false;
    }

    auto& api = GetLibra();
    if (!api.EnsureLoaded() || !api.gl_chain_create || !api.gl_chain_frame) {
        return false;
    }

    std::scoped_lock lock{impl->mutex};
    if (!impl->failed_preset.empty() && impl->failed_preset == preset_path) {
        return false;
    }

    if (impl->gl_chain && impl->active_preset != preset_path) {
        if (api.gl_chain_free) {
            api.LogErr("gl_chain_free", api.gl_chain_free(&impl->gl_chain));
        }
        impl->gl_chain = nullptr;
        impl->active_preset.clear();
        impl->frame_count = 0;
    }

    if (!impl->gl_chain) {
        libra_preset_ctx_t ctx = nullptr;
        if (libra_error_t err = api.preset_ctx_create(&ctx)) {
            api.LogErr("preset_ctx_create", err);
            impl->failed_preset = preset_path;
            return false;
        }
        if (api.preset_ctx_set_runtime)
            api.preset_ctx_set_runtime(&ctx, LIBRA_PRESET_CTX_RUNTIME_GL_CORE);
        if (api.preset_ctx_set_core_name)
            api.preset_ctx_set_core_name(&ctx, "STORM 3DS");

        libra_preset_opt_t p_opt{};
        p_opt.version = LIBRASHADER_CURRENT_ABI;
        p_opt.original_aspect_uniforms = true;
        p_opt.frametime_uniforms = true;

        libra_shader_preset_t preset = nullptr;
        if (libra_error_t err = api.preset_create_with_options(preset_path.c_str(), &ctx, &p_opt, &preset)) {
            api.LogErr("preset_create_with_options", err);
            if (ctx && api.preset_ctx_free) api.preset_ctx_free(&ctx);
            impl->failed_preset = preset_path;
            return false;
        }

        libra_gl_loader_t loader = reinterpret_cast<libra_gl_loader_t>(eglGetProcAddress);
        filter_chain_gl_opt_t c_opt{};
        c_opt.version = LIBRASHADER_CURRENT_ABI;
        c_opt.glsl_version = 0;
        c_opt.use_dsa = false;
        c_opt.force_no_mipmaps = false;
        c_opt.disable_cache = false;

        if (libra_error_t err = api.gl_chain_create(&preset, loader, &c_opt, &impl->gl_chain)) {
            api.LogErr("gl_filter_chain_create", err);
            if (preset) api.preset_free(&preset);
            impl->failed_preset = preset_path;
            return false;
        }
        impl->active_preset = preset_path;
        impl->failed_preset.clear();
        impl->frame_count = 0;
    }

    if (impl->gl_out_tex == 0 || impl->gl_out_w != output_width || impl->gl_out_h != output_height) {
        if (!impl->gl_out_fbo) glGenFramebuffers(1, &impl->gl_out_fbo);
        if (!impl->gl_out_tex) glGenTextures(1, &impl->gl_out_tex);
        glBindTexture(GL_TEXTURE_2D, impl->gl_out_tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, output_width, output_height, 0, GL_RGBA,
                     GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindFramebuffer(GL_FRAMEBUFFER, impl->gl_out_fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, impl->gl_out_tex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        impl->gl_out_w = output_width;
        impl->gl_out_h = output_height;
    }

    impl->frame_count++;
    libra_image_gl_t src{
        .handle = source_tex,
        .format = GL_RGBA8,
        .width = source_width,
        .height = source_height,
    };
    libra_image_gl_t dst{
        .handle = impl->gl_out_tex,
        .format = GL_RGBA8,
        .width = output_width,
        .height = output_height,
    };
    libra_viewport_t vp{
        .x = 0.0f,
        .y = 0.0f,
        .width = output_width,
        .height = output_height,
    };
    frame_gl_opt_t f_opt{};
    f_opt.version = LIBRASHADER_CURRENT_ABI;
    f_opt.clear_history = impl->frame_count <= 1;
    f_opt.frame_direction = 1;
    f_opt.rotation = 0;
    f_opt.total_subframes = 1;
    f_opt.current_subframe = 1;
    f_opt.aspect_ratio = static_cast<float>(output_width) / std::max(1.0f, static_cast<float>(output_height));
    f_opt.frames_per_second = 60.0f;
    f_opt.frametime_delta = 16;

    if (libra_error_t err = api.gl_chain_frame(&impl->gl_chain, static_cast<size_t>(impl->frame_count),
                                               src, dst, &vp, nullptr, &f_opt)) {
        api.LogErr("gl_filter_chain_frame", err);
        impl->failed_preset = preset_path;
        return false;
    }

    glBindFramebuffer(GL_READ_FRAMEBUFFER, impl->gl_out_fbo);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, output_fbo);
    glBlitFramebuffer(0, 0, output_width, output_height, 0, 0, output_width, output_height,
                      GL_COLOR_BUFFER_BIT, GL_LINEAR);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    return true;
}

} // namespace VideoCore
