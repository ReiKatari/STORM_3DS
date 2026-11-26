// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#include "video_core/storm_librashader.h"
#include "common/logging/log.h"
#include "common/settings.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <deque>
#include <locale>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif
#ifdef __ANDROID__
#include <EGL/egl.h>
#endif

#if defined(ENABLE_VULKAN)
#define LIBRA_RUNTIME_VULKAN 1
#endif
#define LIBRA_RUNTIME_OPENGL 1
#include "librashader.h"

namespace VideoCore {

namespace {

std::atomic<u64> g_reload_generation{0};
std::atomic<bool> g_clear_history_requested{false};

struct DiagnosticsStore {
    static constexpr std::size_t MAX_ENTRIES = 64;
    std::mutex mutex;
    std::deque<std::string> queue;
    std::string last_signature;
    std::string last_summary;

    void Record(const std::string& backend, bool succeeded, const std::string& preset_path,
                u32 src_w, u32 src_h, u32 dst_w, u32 dst_h, const std::string& reason) {
        std::string sanitized_reason = reason;
        std::replace(sanitized_reason.begin(), sanitized_reason.end(), '\t', ' ');
        std::replace(sanitized_reason.begin(), sanitized_reason.end(), '\r', ' ');
        std::replace(sanitized_reason.begin(), sanitized_reason.end(), '\n', ' ');

        const std::string signature =
            backend + "|" + (succeeded ? "1" : "0") + "|" + preset_path + "|" + sanitized_reason;

        std::scoped_lock lock{mutex};
        last_summary = backend + ": " + (succeeded ? "OK" : ("FAIL - " + sanitized_reason));
        if (signature == last_signature) {
            return;
        }
        last_signature = signature;

        std::ostringstream oss;
        oss.imbue(std::locale::classic());
        oss << backend << '\t' << (succeeded ? "OK" : "FAIL") << '\t' << preset_path << '\t'
            << src_w << 'x' << src_h << '\t' << dst_w << 'x' << dst_h;
        if (!sanitized_reason.empty()) {
            oss << '\t' << sanitized_reason;
        }
        if (queue.size() >= MAX_ENTRIES) {
            queue.pop_front();
        }
        queue.push_back(oss.str());
    }

    std::vector<std::string> Consume() {
        std::scoped_lock lock{mutex};
        std::vector<std::string> out(queue.begin(), queue.end());
        queue.clear();
        return out;
    }

    std::string LastSummary() {
        std::scoped_lock lock{mutex};
        return last_summary;
    }
};

DiagnosticsStore& GetDiagnostics() {
    static DiagnosticsStore store;
    return store;
}

struct LibraApi {
    std::mutex init_mutex;
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
    PFN_libra_preset_get_runtime_params preset_get_runtime_params = nullptr;
    PFN_libra_preset_free_runtime_params preset_free_runtime_params = nullptr;

    PFN_libra_gl_filter_chain_create gl_chain_create = nullptr;
    PFN_libra_gl_filter_chain_frame gl_chain_frame = nullptr;
    PFN_libra_gl_filter_chain_set_param gl_chain_set_param = nullptr;
    PFN_libra_gl_filter_chain_free gl_chain_free = nullptr;

#ifdef ENABLE_VULKAN
    PFN_libra_vk_filter_chain_create vk_chain_create = nullptr;
    PFN_libra_vk_filter_chain_frame vk_chain_frame = nullptr;
    PFN_libra_vk_filter_chain_set_param vk_chain_set_param = nullptr;
    PFN_libra_vk_filter_chain_free vk_chain_free = nullptr;
#endif

    bool EnsureLoaded() {
        std::scoped_lock lock{init_mutex};
        if (handle != nullptr) {
            return preset_ctx_create && preset_create_with_options && preset_free;
        }
        if (attempted) {
            return false;
        }
        attempted = true;
#ifdef _WIN32
        HMODULE mod = LoadLibraryA("librashader.dll");
        if (!mod) {
            wchar_t exe_path[MAX_PATH]{};
            if (GetModuleFileNameW(nullptr, exe_path, MAX_PATH) > 0) {
                std::wstring dir(exe_path);
                const auto slash = dir.find_last_of(L"\\/");
                if (slash != std::wstring::npos) {
                    dir.resize(slash + 1);
                    dir += L"librashader.dll";
                    mod = LoadLibraryW(dir.c_str());
                }
            }
        }
        handle = static_cast<void*>(mod);
        if (!handle) {
            LOG_WARNING(Render, "librashader.dll not loaded (optional): error {}", GetLastError());
            return false;
        }
        auto load = [&](const char* sym) -> void* {
            return reinterpret_cast<void*>(GetProcAddress(static_cast<HMODULE>(handle), sym));
        };
#else
        handle = dlopen("liblibrashader.so", RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOG_WARNING(Render, "librashader not loaded (optional): {}", dlerror());
            return false;
        }
        auto load = [&](const char* sym) -> void* { return dlsym(handle, sym); };
#endif
        error_free = reinterpret_cast<PFN_libra_error_free>(load("libra_error_free"));
        error_write = reinterpret_cast<PFN_libra_error_write>(load("libra_error_write"));
        error_free_string =
            reinterpret_cast<PFN_libra_error_free_string>(load("libra_error_free_string"));
        preset_ctx_create =
            reinterpret_cast<PFN_libra_preset_ctx_create>(load("libra_preset_ctx_create"));
        preset_ctx_free = reinterpret_cast<PFN_libra_preset_ctx_free>(load("libra_preset_ctx_free"));
        preset_ctx_set_runtime =
            reinterpret_cast<PFN_libra_preset_ctx_set_runtime>(load("libra_preset_ctx_set_runtime"));
        preset_ctx_set_core_name = reinterpret_cast<PFN_libra_preset_ctx_set_core_name>(
            load("libra_preset_ctx_set_core_name"));
        preset_ctx_set_core_aspect_orientation =
            reinterpret_cast<PFN_libra_preset_ctx_set_core_aspect_orientation>(
                load("libra_preset_ctx_set_core_aspect_orientation"));
        preset_ctx_set_view_aspect_orientation =
            reinterpret_cast<PFN_libra_preset_ctx_set_view_aspect_orientation>(
                load("libra_preset_ctx_set_view_aspect_orientation"));
        preset_ctx_set_allow_rotation = reinterpret_cast<PFN_libra_preset_ctx_set_allow_rotation>(
            load("libra_preset_ctx_set_allow_rotation"));
        preset_create_with_options = reinterpret_cast<PFN_libra_preset_create_with_options>(
            load("libra_preset_create_with_options"));
        preset_free = reinterpret_cast<PFN_libra_preset_free>(load("libra_preset_free"));
        preset_get_runtime_params = reinterpret_cast<PFN_libra_preset_get_runtime_params>(
            load("libra_preset_get_runtime_params"));
        preset_free_runtime_params = reinterpret_cast<PFN_libra_preset_free_runtime_params>(
            load("libra_preset_free_runtime_params"));

        gl_chain_create =
            reinterpret_cast<PFN_libra_gl_filter_chain_create>(load("libra_gl_filter_chain_create"));
        gl_chain_frame =
            reinterpret_cast<PFN_libra_gl_filter_chain_frame>(load("libra_gl_filter_chain_frame"));
        gl_chain_set_param = reinterpret_cast<PFN_libra_gl_filter_chain_set_param>(
            load("libra_gl_filter_chain_set_param"));
        gl_chain_free =
            reinterpret_cast<PFN_libra_gl_filter_chain_free>(load("libra_gl_filter_chain_free"));
#ifdef ENABLE_VULKAN
        vk_chain_create =
            reinterpret_cast<PFN_libra_vk_filter_chain_create>(load("libra_vk_filter_chain_create"));
        vk_chain_frame =
            reinterpret_cast<PFN_libra_vk_filter_chain_frame>(load("libra_vk_filter_chain_frame"));
        vk_chain_set_param = reinterpret_cast<PFN_libra_vk_filter_chain_set_param>(
            load("libra_vk_filter_chain_set_param"));
        vk_chain_free =
            reinterpret_cast<PFN_libra_vk_filter_chain_free>(load("libra_vk_filter_chain_free"));
#endif
        return preset_ctx_create && preset_create_with_options && preset_free;
    }

    std::string DescribeAndFreeErr(const char* ctx, libra_error_t err) {
        std::string description;
        if (!err) {
            return description;
        }
        char* msg = nullptr;
        if (error_write && error_write(err, &msg) == 0 && msg) {
            description = msg;
            LOG_ERROR(Render, "librashader {} failed: {}", ctx, msg);
            if (error_free_string) {
                error_free_string(&msg);
            }
        } else {
            LOG_ERROR(Render, "librashader {} failed", ctx);
        }
        if (error_free) {
            error_free(&err);
        }
        if (description.empty()) {
            description = std::string(ctx) + " failed";
        }
        return description;
    }

    void LogErr(const char* ctx, libra_error_t err) {
        (void)DescribeAndFreeErr(ctx, err);
    }
};

LibraApi& GetLibra() {
    static LibraApi api;
    return api;
}

struct PresetCleanupGuard {
    LibraApi& api;
    libra_preset_ctx_t& ctx;
    libra_shader_preset_t& preset;

    ~PresetCleanupGuard() {
        if (preset != nullptr && api.preset_free) {
            (void)api.preset_free(&preset);
        }
        if (ctx != nullptr && api.preset_ctx_free) {
            (void)api.preset_ctx_free(&ctx);
        }
    }
};

std::string TrimWhitespace(std::string s) {
    const auto is_space = [](unsigned char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    };
    while (!s.empty() && is_space(static_cast<unsigned char>(s.front()))) {
        s.erase(s.begin());
    }
    while (!s.empty() && is_space(static_cast<unsigned char>(s.back()))) {
        s.pop_back();
    }
    return s;
}

void ExtractPresetDefaults(LibraApi& api, const libra_shader_preset_t& preset,
                           std::unordered_map<std::string, float>& out_defaults) {
    out_defaults.clear();
    if (!api.preset_get_runtime_params || !api.preset_free_runtime_params) {
        return;
    }
    libra_preset_param_list_t list{};
    if (libra_error_t err = api.preset_get_runtime_params(&preset, &list)) {
        api.LogErr("preset_get_runtime_params", err);
        return;
    }
    if (list.parameters && list.length > 0) {
        for (uint64_t i = 0; i < list.length; ++i) {
            const auto& p = list.parameters[i];
            if (p.name && p.name[0] != '\0') {
                out_defaults[p.name] = p.initial;
            }
        }
    }
    if (libra_error_t err = api.preset_free_runtime_params(list)) {
        api.LogErr("preset_free_runtime_params", err);
    }
}

void ConfigurePresetContext(LibraApi& api, libra_preset_ctx_t* ctx, LIBRA_PRESET_CTX_RUNTIME runtime,
                            u32 output_width, u32 output_height) {
    if (api.preset_ctx_set_runtime) {
        api.preset_ctx_set_runtime(ctx, runtime);
    }
    if (api.preset_ctx_set_core_name) {
        api.preset_ctx_set_core_name(ctx, "STORM 3DS");
    }
    const auto orient = (output_height > output_width) ? LIBRA_PRESET_CTX_ORIENTATION_VERTICAL
                                                       : LIBRA_PRESET_CTX_ORIENTATION_HORIZONTAL;
    if (api.preset_ctx_set_core_aspect_orientation) {
        api.preset_ctx_set_core_aspect_orientation(ctx, orient);
    }
    if (api.preset_ctx_set_view_aspect_orientation) {
        api.preset_ctx_set_view_aspect_orientation(ctx, orient);
    }
    if (api.preset_ctx_set_allow_rotation) {
        api.preset_ctx_set_allow_rotation(ctx, false);
    }
}

#ifdef __ANDROID__
const void* AndroidGlProcLoader(const char* name) {
    static void* gles_v3 = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
    static void* gles_v2 = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);

    if (!name) {
        return nullptr;
    }
    if (gles_v3) {
        if (void* sym = dlsym(gles_v3, name)) {
            return sym;
        }
    }
    if (gles_v2) {
        if (void* sym = dlsym(gles_v2, name)) {
            return sym;
        }
    }
    if (void* sym = dlsym(RTLD_DEFAULT, name)) {
        return sym;
    }
    return reinterpret_cast<const void*>(eglGetProcAddress(name));
}
#elif defined(_WIN32)
const void* WinGlProcLoader(const char* name) {
    if (!name) {
        return nullptr;
    }
    void* proc = nullptr;
    HMODULE gl_mod = GetModuleHandleA("opengl32.dll");
    if (!gl_mod) {
        gl_mod = LoadLibraryA("opengl32.dll");
    }
    if (gl_mod) {
        using PFN_wglGetProcAddress = PROC(WINAPI*)(LPCSTR);
        auto wgl_get_proc =
            reinterpret_cast<PFN_wglGetProcAddress>(GetProcAddress(gl_mod, "wglGetProcAddress"));
        if (wgl_get_proc) {
            proc = reinterpret_cast<void*>(wgl_get_proc(name));
        }
        if (proc == nullptr || proc == reinterpret_cast<void*>(0x1) ||
            proc == reinterpret_cast<void*>(0x2) || proc == reinterpret_cast<void*>(0x3) ||
            proc == reinterpret_cast<void*>(-1)) {
            proc = reinterpret_cast<void*>(GetProcAddress(gl_mod, name));
        }
    }
    return proc;
}
#endif

struct GlStateGuard {
    GLint draw_fbo = 0;
    GLint read_fbo = 0;
    GLint program = 0;
    GLint vao = 0;
    GLint active_texture = GL_TEXTURE0;
    GLint tex2d_active = 0;
    GLint tex2d_unit0 = 0;
    GLint viewport[4]{};
    GLint scissor_box[4]{};
    GLboolean scissor_test = GL_FALSE;
    GLboolean blend = GL_FALSE;
    GLboolean depth_test = GL_FALSE;
    GLboolean cull_face = GL_FALSE;
    GLboolean stencil_test = GL_FALSE;
    GLboolean color_mask[4]{GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE};
    GLint pack_align = 4;
    GLint unpack_align = 4;

    GlStateGuard() {
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw_fbo);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read_fbo);
        glGetIntegerv(GL_CURRENT_PROGRAM, &program);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao);
        glGetIntegerv(GL_ACTIVE_TEXTURE, &active_texture);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex2d_active);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex2d_unit0);
        glActiveTexture(active_texture);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor_box);
        scissor_test = glIsEnabled(GL_SCISSOR_TEST);
        blend = glIsEnabled(GL_BLEND);
        depth_test = glIsEnabled(GL_DEPTH_TEST);
        cull_face = glIsEnabled(GL_CULL_FACE);
        stencil_test = glIsEnabled(GL_STENCIL_TEST);
        glGetBooleanv(GL_COLOR_WRITEMASK, color_mask);
        glGetIntegerv(GL_PACK_ALIGNMENT, &pack_align);
        glGetIntegerv(GL_UNPACK_ALIGNMENT, &unpack_align);
    }

    ~GlStateGuard() {
        auto set_cap = [](GLenum cap, GLboolean enabled) {
            if (enabled) {
                glEnable(cap);
            } else {
                glDisable(cap);
            }
        };
        glUseProgram(program);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex2d_unit0);
        if (active_texture != GL_TEXTURE0) {
            glActiveTexture(active_texture);
            glBindTexture(GL_TEXTURE_2D, tex2d_active);
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read_fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw_fbo);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissor_box[0], scissor_box[1], scissor_box[2], scissor_box[3]);
        set_cap(GL_SCISSOR_TEST, scissor_test);
        set_cap(GL_BLEND, blend);
        set_cap(GL_DEPTH_TEST, depth_test);
        set_cap(GL_CULL_FACE, cull_face);
        set_cap(GL_STENCIL_TEST, stencil_test);
        glColorMask(color_mask[0], color_mask[1], color_mask[2], color_mask[3]);
        glPixelStorei(GL_PACK_ALIGNMENT, pack_align);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpack_align);
    }
};

} // namespace

struct StormLibrashader::Impl {
    std::mutex mutex;
    std::string active_preset;
    std::string failed_preset;
    std::string applied_params_raw;
    std::unordered_map<std::string, float> default_params;
    std::unordered_map<std::string, float> applied_params;
    u64 last_reload_generation = 0;
    u64 frame_count = 0;
    std::chrono::steady_clock::time_point last_frame_time{};
    bool has_last_frame_time = false;
    u32 last_src_w = 0;
    u32 last_src_h = 0;
    u32 last_dst_w = 0;
    u32 last_dst_h = 0;

#ifdef ENABLE_VULKAN
    libra_vk_filter_chain_t vk_chain = nullptr;
    vk::Device vk_device{};
#endif

    libra_gl_filter_chain_t gl_chain = nullptr;
    GLuint gl_out_tex = 0;
    GLuint gl_out_fbo = 0;
    u32 gl_out_w = 0;
    u32 gl_out_h = 0;

    u32 ComputeFrametimeDeltaMs() {
        const auto now = std::chrono::steady_clock::now();
        u32 delta = 16;
        if (has_last_frame_time) {
            const auto ms =
                std::chrono::duration_cast<std::chrono::milliseconds>(now - last_frame_time).count();
            delta = static_cast<u32>(std::clamp<s64>(ms, 1, 100));
        }
        last_frame_time = now;
        has_last_frame_time = true;
        return delta;
    }

    template <typename SetParamFn>
    void SyncParameterOverrides(const std::string& raw_params, SetParamFn&& set_param_fn) {
        if (raw_params == applied_params_raw) {
            return;
        }
        const auto parsed = ParseParameterOverrides(raw_params);
        std::unordered_map<std::string, float> next_map;
        next_map.reserve(parsed.size());
        for (const auto& [name, val] : parsed) {
            next_map[name] = val;
        }

        // Restore any previously overridden parameters that were removed back to preset defaults.
        for (const auto& [prev_name, _] : applied_params) {
            if (next_map.find(prev_name) == next_map.end()) {
                if (const auto def_it = default_params.find(prev_name);
                    def_it != default_params.end()) {
                    set_param_fn(prev_name.c_str(), def_it->second);
                }
            }
        }

        for (const auto& [name, val] : parsed) {
            set_param_fn(name.c_str(), val);
        }

        applied_params_raw = raw_params;
        applied_params = std::move(next_map);
    }
};

StormLibrashader::StormLibrashader() : impl(std::make_unique<Impl>()) {}

StormLibrashader::~StormLibrashader() {
#ifdef ENABLE_VULKAN
    ResetVulkan();
#endif
    ResetOpenGL();
}

bool StormLibrashader::IsLibraryAvailable() {
    return GetLibra().EnsureLoaded();
}

void StormLibrashader::RequestClearHistory() {
    g_clear_history_requested.store(true, std::memory_order_release);
}

void StormLibrashader::RequestReload() {
    g_reload_generation.fetch_add(1, std::memory_order_acq_rel);
}

std::string StormLibrashader::GetLastDiagnosticSummary() {
    return GetDiagnostics().LastSummary();
}

std::vector<std::string> StormLibrashader::ConsumeDiagnostics() {
    return GetDiagnostics().Consume();
}

std::vector<std::pair<std::string, float>> StormLibrashader::ParseParameterOverrides(
    const std::string& raw) {
    std::vector<std::pair<std::string, float>> out;
    if (raw.empty()) {
        return out;
    }

    std::size_t start = 0;
    while (start <= raw.size()) {
        const auto end = raw.find_first_of("\n\r;,", start);
        const std::string token = TrimWhitespace(
            end == std::string::npos ? raw.substr(start) : raw.substr(start, end - start));
        if (!token.empty()) {
            const auto eq = token.find('=');
            if (eq != std::string::npos) {
                std::string key = TrimWhitespace(token.substr(0, eq));
                std::string val_str = TrimWhitespace(token.substr(eq + 1));
                if (!key.empty() && !val_str.empty()) {
                    std::istringstream iss(val_str);
                    iss.imbue(std::locale::classic());
                    float val = 0.0f;
                    if ((iss >> val) && std::isfinite(val)) {
                        out.emplace_back(std::move(key), val);
                    }
                }
            }
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return out;
}

std::vector<LibrashaderParam> StormLibrashader::QueryPresetParameters(
    const std::string& preset_path) {
    std::vector<LibrashaderParam> result;
    if (preset_path.empty()) {
        return result;
    }
    auto& api = GetLibra();
    if (!api.EnsureLoaded() || !api.preset_get_runtime_params || !api.preset_free_runtime_params) {
        return result;
    }

    libra_preset_ctx_t ctx = nullptr;
    libra_shader_preset_t preset = nullptr;
    PresetCleanupGuard guard{api, ctx, preset};

    if (libra_error_t err = api.preset_ctx_create(&ctx)) {
        api.LogErr("QueryPresetParameters preset_ctx_create", err);
        return result;
    }
    ConfigurePresetContext(api, &ctx, LIBRA_PRESET_CTX_RUNTIME_VULKAN, 16, 9);

    libra_preset_opt_t p_opt{};
    p_opt.version = LIBRASHADER_CURRENT_ABI;
    p_opt.original_aspect_uniforms = true;
    p_opt.frametime_uniforms = true;

    if (libra_error_t err =
            api.preset_create_with_options(preset_path.c_str(), &ctx, &p_opt, &preset)) {
        api.LogErr("QueryPresetParameters preset_create_with_options", err);
        return result;
    }

    libra_preset_param_list_t list{};
    if (libra_error_t err = api.preset_get_runtime_params(&preset, &list)) {
        api.LogErr("QueryPresetParameters preset_get_runtime_params", err);
        return result;
    }

    if (list.parameters && list.length > 0) {
        result.reserve(static_cast<std::size_t>(list.length));
        for (uint64_t i = 0; i < list.length; ++i) {
            const auto& p = list.parameters[i];
            if (!p.name || p.name[0] == '\0') {
                continue;
            }
            LibrashaderParam item;
            item.name = p.name;
            item.description = (p.description && p.description[0] != '\0') ? p.description : p.name;
            item.initial = p.initial;
            item.minimum = p.minimum;
            item.maximum = (p.maximum >= p.minimum) ? p.maximum : p.minimum;
            item.step = (p.step > 0.0f) ? p.step : 0.01f;
            result.push_back(std::move(item));
        }
    }
    if (libra_error_t err = api.preset_free_runtime_params(list)) {
        api.LogErr("QueryPresetParameters preset_free_runtime_params", err);
    }
    return result;
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
    impl->applied_params_raw.clear();
    impl->applied_params.clear();
    impl->default_params.clear();
    impl->frame_count = 0;
    impl->has_last_frame_time = false;
}

bool StormLibrashader::ApplyVulkan(vk::Instance instance, vk::PhysicalDevice physical_device,
                                   vk::Device device, vk::Queue queue,
                                   PFN_vkGetInstanceProcAddr get_proc_addr,
                                   vk::CommandBuffer cmdbuf, vk::Image source_image,
                                   vk::Format source_format, u32 source_width, u32 source_height,
                                   vk::Image output_image, vk::Format output_format,
                                   u32 output_width, u32 output_height, u32 frames_in_flight) {
    if (!Settings::values.librashader_enabled.GetValue()) {
        if (impl->vk_chain) {
            ResetVulkan();
        }
        return false;
    }
    const std::string preset_path = Settings::values.librashader_preset_path.GetValue();
    if (preset_path.empty() || source_width == 0 || source_height == 0 || output_width == 0 ||
        output_height == 0) {
        return false;
    }

    auto& api = GetLibra();
    if (!api.EnsureLoaded() || !api.vk_chain_create || !api.vk_chain_frame) {
        GetDiagnostics().Record("Vulkan", false, preset_path, source_width, source_height,
                                output_width, output_height, "librashader library unavailable");
        return false;
    }

    std::scoped_lock lock{impl->mutex};
    const u64 reload_gen = g_reload_generation.load(std::memory_order_acquire);
    if (reload_gen != impl->last_reload_generation) {
        impl->last_reload_generation = reload_gen;
        impl->failed_preset.clear();
    }

    if (!impl->failed_preset.empty() && impl->failed_preset == preset_path) {
        return false;
    }

    if (impl->vk_chain && (impl->active_preset != preset_path || impl->vk_device != device)) {
        if (api.vk_chain_free) {
            api.LogErr("vk_chain_free", api.vk_chain_free(&impl->vk_chain));
        }
        impl->vk_chain = nullptr;
        impl->active_preset.clear();
        impl->applied_params_raw.clear();
        impl->applied_params.clear();
        impl->default_params.clear();
        impl->frame_count = 0;
        impl->has_last_frame_time = false;
    }

    if (!impl->vk_chain) {
        libra_preset_ctx_t ctx = nullptr;
        libra_shader_preset_t preset = nullptr;
        PresetCleanupGuard cleanup_guard{api, ctx, preset};

        if (libra_error_t err = api.preset_ctx_create(&ctx)) {
            const std::string reason = api.DescribeAndFreeErr("preset_ctx_create", err);
            GetDiagnostics().Record("Vulkan", false, preset_path, source_width, source_height,
                                    output_width, output_height, reason);
            impl->failed_preset = preset_path;
            return false;
        }
        ConfigurePresetContext(api, &ctx, LIBRA_PRESET_CTX_RUNTIME_VULKAN, output_width,
                               output_height);

        libra_preset_opt_t p_opt{};
        p_opt.version = LIBRASHADER_CURRENT_ABI;
        p_opt.original_aspect_uniforms = true;
        p_opt.frametime_uniforms = true;

        if (libra_error_t err =
                api.preset_create_with_options(preset_path.c_str(), &ctx, &p_opt, &preset)) {
            const std::string reason = api.DescribeAndFreeErr("preset_create_with_options", err);
            GetDiagnostics().Record("Vulkan", false, preset_path, source_width, source_height,
                                    output_width, output_height, reason);
            impl->failed_preset = preset_path;
            return false;
        }

        ExtractPresetDefaults(api, preset, impl->default_params);

        libra_device_vk_t dev{};
        dev.physical_device = static_cast<VkPhysicalDevice>(physical_device);
        dev.instance = static_cast<VkInstance>(instance);
        dev.device = static_cast<VkDevice>(device);
        dev.queue = static_cast<VkQueue>(queue);
        dev.entry = get_proc_addr;

        filter_chain_vk_opt_t c_opt{};
        c_opt.version = LIBRASHADER_CURRENT_ABI;
        c_opt.frames_in_flight = std::max<u32>(3u, frames_in_flight);
        c_opt.force_no_mipmaps = false;
        c_opt.use_dynamic_rendering = false;
        c_opt.disable_cache = false;

        if (libra_error_t err = api.vk_chain_create(&preset, dev, &c_opt, &impl->vk_chain)) {
            const std::string reason = api.DescribeAndFreeErr("vk_filter_chain_create", err);
            GetDiagnostics().Record("Vulkan", false, preset_path, source_width, source_height,
                                    output_width, output_height, reason);
            impl->failed_preset = preset_path;
            return false;
        }
        impl->active_preset = preset_path;
        impl->vk_device = device;
        impl->failed_preset.clear();
        impl->applied_params_raw.clear();
        impl->applied_params.clear();
        impl->frame_count = 0;

        if (api.vk_chain_set_param) {
            impl->SyncParameterOverrides(
                Settings::values.librashader_parameters.GetValue(),
                [&](const char* name, float val) {
                    if (libra_error_t err = api.vk_chain_set_param(&impl->vk_chain, name, val)) {
                        api.LogErr("vk_filter_chain_set_param", err);
                    }
                });
        }
        GetDiagnostics().Record("Vulkan", true, preset_path, source_width, source_height,
                                output_width, output_height, "");
    } else if (api.vk_chain_set_param) {
        impl->SyncParameterOverrides(
            Settings::values.librashader_parameters.GetValue(),
            [&](const char* name, float val) {
                if (libra_error_t err = api.vk_chain_set_param(&impl->vk_chain, name, val)) {
                    api.LogErr("vk_filter_chain_set_param", err);
                }
            });
    }

    const bool resized = (impl->last_src_w != source_width || impl->last_src_h != source_height ||
                          impl->last_dst_w != output_width || impl->last_dst_h != output_height);
    impl->last_src_w = source_width;
    impl->last_src_h = source_height;
    impl->last_dst_w = output_width;
    impl->last_dst_h = output_height;

    const u32 frametime_ms = impl->ComputeFrametimeDeltaMs();
    impl->frame_count++;
    const bool clear_history =
        (impl->frame_count <= 1) || g_clear_history_requested.exchange(false) || resized;

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
    f_opt.clear_history = clear_history;
    f_opt.frame_direction = 1;
    f_opt.rotation = 0;
    f_opt.total_subframes = 1;
    f_opt.current_subframe = 1;
    f_opt.aspect_ratio =
        static_cast<float>(output_width) / std::max(1.0f, static_cast<float>(output_height));
    f_opt.frames_per_second = 1000.0f / static_cast<float>(frametime_ms);
    f_opt.frametime_delta = frametime_ms;

    if (libra_error_t err = api.vk_chain_frame(&impl->vk_chain, static_cast<VkCommandBuffer>(cmdbuf),
                                               static_cast<size_t>(impl->frame_count), src, dst,
                                               &vp, nullptr, &f_opt)) {
        const std::string reason = api.DescribeAndFreeErr("vk_filter_chain_frame", err);
        GetDiagnostics().Record("Vulkan", false, preset_path, source_width, source_height,
                                output_width, output_height, reason);
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
    impl->applied_params_raw.clear();
    impl->applied_params.clear();
    impl->default_params.clear();
    impl->frame_count = 0;
    impl->has_last_frame_time = false;
}

bool StormLibrashader::ApplyOpenGL(GLuint source_tex, u32 source_width, u32 source_height,
                                   GLuint output_fbo, u32 output_width, u32 output_height) {
    if (!Settings::values.librashader_enabled.GetValue()) {
        if (impl->gl_chain) {
            ResetOpenGL();
        }
        return false;
    }
    const std::string preset_path = Settings::values.librashader_preset_path.GetValue();
    if (preset_path.empty() || source_tex == 0 || source_width == 0 || source_height == 0 ||
        output_width == 0 || output_height == 0) {
        return false;
    }

    auto& api = GetLibra();
    if (!api.EnsureLoaded() || !api.gl_chain_create || !api.gl_chain_frame) {
        GetDiagnostics().Record("OpenGL", false, preset_path, source_width, source_height,
                                output_width, output_height, "librashader library unavailable");
        return false;
    }

    std::scoped_lock lock{impl->mutex};
    const u64 reload_gen = g_reload_generation.load(std::memory_order_acquire);
    if (reload_gen != impl->last_reload_generation) {
        impl->last_reload_generation = reload_gen;
        impl->failed_preset.clear();
    }

    if (!impl->failed_preset.empty() && impl->failed_preset == preset_path) {
        return false;
    }

    if (impl->gl_chain && impl->active_preset != preset_path) {
        if (api.gl_chain_free) {
            api.LogErr("gl_chain_free", api.gl_chain_free(&impl->gl_chain));
        }
        impl->gl_chain = nullptr;
        impl->active_preset.clear();
        impl->applied_params_raw.clear();
        impl->applied_params.clear();
        impl->default_params.clear();
        impl->frame_count = 0;
        impl->has_last_frame_time = false;
    }

    GlStateGuard state_guard;

    if (!impl->gl_chain) {
        libra_preset_ctx_t ctx = nullptr;
        libra_shader_preset_t preset = nullptr;
        PresetCleanupGuard cleanup_guard{api, ctx, preset};

        if (libra_error_t err = api.preset_ctx_create(&ctx)) {
            const std::string reason = api.DescribeAndFreeErr("preset_ctx_create", err);
            GetDiagnostics().Record("OpenGL", false, preset_path, source_width, source_height,
                                    output_width, output_height, reason);
            impl->failed_preset = preset_path;
            return false;
        }
        ConfigurePresetContext(api, &ctx, LIBRA_PRESET_CTX_RUNTIME_GL_CORE, output_width,
                               output_height);

        libra_preset_opt_t p_opt{};
        p_opt.version = LIBRASHADER_CURRENT_ABI;
        p_opt.original_aspect_uniforms = true;
        p_opt.frametime_uniforms = true;

        if (libra_error_t err =
                api.preset_create_with_options(preset_path.c_str(), &ctx, &p_opt, &preset)) {
            const std::string reason = api.DescribeAndFreeErr("preset_create_with_options", err);
            GetDiagnostics().Record("OpenGL", false, preset_path, source_width, source_height,
                                    output_width, output_height, reason);
            impl->failed_preset = preset_path;
            return false;
        }

        ExtractPresetDefaults(api, preset, impl->default_params);

#ifdef __ANDROID__
        libra_gl_loader_t loader = reinterpret_cast<libra_gl_loader_t>(&AndroidGlProcLoader);
#elif defined(_WIN32)
        libra_gl_loader_t loader = reinterpret_cast<libra_gl_loader_t>(&WinGlProcLoader);
#else
        libra_gl_loader_t loader = nullptr;
#endif
        filter_chain_gl_opt_t c_opt{};
        c_opt.version = LIBRASHADER_CURRENT_ABI;
        c_opt.glsl_version = 0;
        c_opt.use_dsa = false;
        c_opt.force_no_mipmaps = false;
        c_opt.disable_cache = false;

        if (libra_error_t err = api.gl_chain_create(&preset, loader, &c_opt, &impl->gl_chain)) {
            const std::string reason = api.DescribeAndFreeErr("gl_filter_chain_create", err);
            GetDiagnostics().Record("OpenGL", false, preset_path, source_width, source_height,
                                    output_width, output_height, reason);
            impl->failed_preset = preset_path;
            return false;
        }
        impl->active_preset = preset_path;
        impl->failed_preset.clear();
        impl->applied_params_raw.clear();
        impl->applied_params.clear();
        impl->frame_count = 0;

        if (api.gl_chain_set_param) {
            impl->SyncParameterOverrides(
                Settings::values.librashader_parameters.GetValue(),
                [&](const char* name, float val) {
                    if (libra_error_t err = api.gl_chain_set_param(&impl->gl_chain, name, val)) {
                        api.LogErr("gl_filter_chain_set_param", err);
                    }
                });
        }
        GetDiagnostics().Record("OpenGL", true, preset_path, source_width, source_height,
                                output_width, output_height, "");
    } else if (api.gl_chain_set_param) {
        impl->SyncParameterOverrides(
            Settings::values.librashader_parameters.GetValue(),
            [&](const char* name, float val) {
                if (libra_error_t err = api.gl_chain_set_param(&impl->gl_chain, name, val)) {
                    api.LogErr("gl_filter_chain_set_param", err);
                }
            });
    }

    if (impl->gl_out_tex == 0 || impl->gl_out_w != output_width ||
        impl->gl_out_h != output_height) {
        if (!impl->gl_out_fbo) {
            glGenFramebuffers(1, &impl->gl_out_fbo);
        }
        if (!impl->gl_out_tex) {
            glGenTextures(1, &impl->gl_out_tex);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, impl->gl_out_tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, output_width, output_height, 0, GL_RGBA,
                     GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, impl->gl_out_fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                               impl->gl_out_tex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        impl->gl_out_w = output_width;
        impl->gl_out_h = output_height;
    }

    const bool resized = (impl->last_src_w != source_width || impl->last_src_h != source_height ||
                          impl->last_dst_w != output_width || impl->last_dst_h != output_height);
    impl->last_src_w = source_width;
    impl->last_src_h = source_height;
    impl->last_dst_w = output_width;
    impl->last_dst_h = output_height;

    const u32 frametime_ms = impl->ComputeFrametimeDeltaMs();
    impl->frame_count++;
    const bool clear_history =
        (impl->frame_count <= 1) || g_clear_history_requested.exchange(false) || resized;

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
    f_opt.clear_history = clear_history;
    f_opt.frame_direction = 1;
    f_opt.rotation = 0;
    f_opt.total_subframes = 1;
    f_opt.current_subframe = 1;
    f_opt.aspect_ratio =
        static_cast<float>(output_width) / std::max(1.0f, static_cast<float>(output_height));
    f_opt.frames_per_second = 1000.0f / static_cast<float>(frametime_ms);
    f_opt.frametime_delta = frametime_ms;

    if (libra_error_t err = api.gl_chain_frame(
            &impl->gl_chain, static_cast<size_t>(impl->frame_count), src, dst, &vp, nullptr,
            &f_opt)) {
        const std::string reason = api.DescribeAndFreeErr("gl_filter_chain_frame", err);
        GetDiagnostics().Record("OpenGL", false, preset_path, source_width, source_height,
                                output_width, output_height, reason);
        impl->failed_preset = preset_path;
        return false;
    }

    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, impl->gl_out_fbo);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, output_fbo);
    glBlitFramebuffer(0, 0, output_width, output_height, 0, 0, output_width, output_height,
                      GL_COLOR_BUFFER_BIT, GL_LINEAR);
    return true;
}

} // namespace VideoCore
