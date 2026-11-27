// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project / STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <optional>
#include <string>
#include <tuple>
#include <utility>
#include <vector>
#ifdef _WIN32
#include <windows.h>
#include <winhttp.h>
#else
#include <httplib.h>
#endif
#include <json.hpp>
#include "common/logging/log.h"
#include "update_checker.h"

namespace {

#ifdef _WIN32
std::optional<std::string> WinHttpGet(const std::wstring& host, const std::wstring& path) {
    HINTERNET hSession = WinHttpOpen(L"STORM-3DS-Updater/1.9", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                                     WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) {
        return {};
    }
    WinHttpSetTimeouts(hSession, 8000, 8000, 10000, 10000);

    HINTERNET hConnect = WinHttpConnect(hSession, host.c_str(), INTERNET_DEFAULT_HTTPS_PORT, 0);
    if (!hConnect) {
        WinHttpCloseHandle(hSession);
        return {};
    }

    HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"GET", path.c_str(), nullptr,
                                            WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES,
                                            WINHTTP_FLAG_SECURE);
    if (!hRequest) {
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return {};
    }

    const wchar_t* headers = L"Accept: application/json\r\nUser-Agent: STORM-3DS-Updater\r\n";
    BOOL sent = WinHttpSendRequest(hRequest, headers, static_cast<DWORD>(-1L),
                                   WINHTTP_NO_REQUEST_DATA, 0, 0, 0);
    if (!sent || !WinHttpReceiveResponse(hRequest, nullptr)) {
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return {};
    }

    DWORD status = 0;
    DWORD status_size = sizeof(status);
    WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                        WINHTTP_HEADER_NAME_BY_INDEX, &status, &status_size, WINHTTP_NO_HEADER_INDEX);
    if (status < 200 || status >= 400) {
        LOG_WARNING(Frontend, "WinHTTP GET status {} for {}", status,
                    std::string(path.begin(), path.end()));
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return {};
    }

    std::string body;
    DWORD available = 0;
    while (WinHttpQueryDataAvailable(hRequest, &available) && available > 0) {
        std::string chunk(available, '\0');
        DWORD read = 0;
        if (!WinHttpReadData(hRequest, chunk.data(), available, &read) || read == 0) {
            break;
        }
        chunk.resize(read);
        body += chunk;
        available = 0;
    }

    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);
    if (body.empty()) {
        return {};
    }
    return body;
}
#endif

std::optional<std::string> GetResponse(const std::string& host, const std::string& path) {
#ifdef _WIN32
    return WinHttpGet(std::wstring(host.begin(), host.end()),
                      std::wstring(path.begin(), path.end()));
#else
    constexpr std::size_t timeout_seconds = 10;
    std::unique_ptr<httplib::Client> client = std::make_unique<httplib::Client>("https://" + host);
    client->set_connection_timeout(timeout_seconds);
    client->set_read_timeout(timeout_seconds);
    client->set_write_timeout(timeout_seconds);

    httplib::Request request{
        .method = "GET",
        .path = path,
        .headers = {{"User-Agent", "STORM-3DS-Updater"}, {"Accept", "application/json"}},
    };
    client->set_follow_location(true);
    const auto result = client->send(request);
    if (!result || result->status >= 400 || result->body.empty()) {
        return {};
    }
    return result->body;
#endif
}

std::string CleanVersionTag(std::string tag) {
    while (!tag.empty() && std::isspace(static_cast<unsigned char>(tag.front()))) {
        tag.erase(tag.begin());
    }
    while (!tag.empty() && std::isspace(static_cast<unsigned char>(tag.back()))) {
        tag.pop_back();
    }
    if (!tag.empty() && (tag[0] == 'v' || tag[0] == 'V')) {
        tag.erase(tag.begin());
    }
    return tag;
}

std::string JsonString(const nlohmann::json& obj, const char* key) {
    if (obj.is_object() && obj.contains(key) && obj[key].is_string()) {
        return obj[key].get<std::string>();
    }
    return {};
}

std::uint64_t JsonUInt64(const nlohmann::json& obj, const char* key) {
    if (!obj.is_object() || !obj.contains(key)) {
        return 0;
    }
    const auto& v = obj[key];
    if (v.is_number_unsigned()) {
        return v.get<std::uint64_t>();
    }
    if (v.is_number_integer()) {
        const auto signed_val = v.get<std::int64_t>();
        return signed_val > 0 ? static_cast<std::uint64_t>(signed_val) : 0;
    }
    return 0;
}

std::string ToLowerAscii(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return s;
}

bool IsWindowsDesktopAsset(const std::string& name) {
    const std::string low = ToLowerAscii(name);
    if (low.ends_with(".zip") && low.find("windows") != std::string::npos) {
        return true;
    }
    return low.ends_with(".zip") && low.find("win64") != std::string::npos;
}

std::optional<UpdateChecker::ReleaseInfo> ParseGitHubRelease(const nlohmann::json& item) {
    if (!item.is_object() || item.value("draft", false)) {
        return {};
    }
    std::string ver = CleanVersionTag(JsonString(item, "tag_name"));
    if (ver.empty()) {
        return {};
    }

    UpdateChecker::ReleaseInfo info;
    info.version = ver;
    info.title = JsonString(item, "name");
    if (info.title.empty()) {
        info.title = "STORM 3DS " + ver;
    }
    info.changelog_md = JsonString(item, "body");
    info.published_at = JsonString(item, "published_at");
    if (info.published_at.empty()) {
        info.published_at = JsonString(item, "created_at");
    }
    info.release_url = JsonString(item, "html_url");
    if (info.release_url.empty()) {
        info.release_url = "https://github.com/ReiKatari/STORM_3DS/releases/tag/" + ver;
    }
    info.source_name = "GitHub";

    if (item.contains("assets") && item["assets"].is_array()) {
        for (const auto& asset : item["assets"]) {
            const std::string aname = JsonString(asset, "name");
            if (IsWindowsDesktopAsset(aname)) {
                info.asset_name = aname;
                info.asset_size = JsonUInt64(asset, "size");
                info.download_url = JsonString(asset, "browser_download_url");
                break;
            }
        }
    }
    return info;
}

std::optional<UpdateChecker::ReleaseInfo> ParseSgwRelease(const nlohmann::json& rel,
                                                          const std::string& base_host) {
    if (!rel.is_object()) {
        return {};
    }
    std::string ver = CleanVersionTag(JsonString(rel, "version"));
    if (ver.empty()) {
        ver = CleanVersionTag(JsonString(rel, "tag_name"));
    }
    if (ver.empty()) {
        return {};
    }

    UpdateChecker::ReleaseInfo info;
    info.version = ver;
    info.title = JsonString(rel, "title");
    if (info.title.empty()) {
        info.title = JsonString(rel, "name");
    }
    if (info.title.empty()) {
        info.title = "STORM 3DS " + ver;
    }
    info.changelog_md = JsonString(rel, "notes");
    if (info.changelog_md.empty()) {
        info.changelog_md = JsonString(rel, "body");
    }
    if (info.changelog_md.empty()) {
        info.changelog_md = JsonString(rel, "changelog");
    }
    info.published_at = JsonString(rel, "createdAt");
    if (info.published_at.empty()) {
        info.published_at = JsonString(rel, "publishedAt");
    }
    info.release_url = base_host + "/programs?repo=STORM_3DS&tag=" + ver;
    info.source_name = "STORM GAMES WORLD";

    if (rel.contains("assets") && rel["assets"].is_array()) {
        for (const auto& asset : rel["assets"]) {
            const std::string aname = JsonString(asset, "name");
            if (IsWindowsDesktopAsset(aname)) {
                info.asset_name = aname;
                info.asset_size = JsonUInt64(asset, "size");
                std::string dl = JsonString(asset, "downloadUrl");
                if (!dl.empty()) {
                    if (dl.rfind("http", 0) != 0) {
                        if (dl[0] != '/') {
                            dl = "/" + dl;
                        }
                        dl = base_host + dl;
                    }
                    info.download_url = dl;
                }
                break;
            }
        }
    }
    return info;
}

std::tuple<int, int, int> ParseReleaseSemVer(const std::string& ver) {
    int major = 0;
    int minor = 0;
    int patch = 0;
    const std::string clean = CleanVersionTag(ver);
    for (std::size_t i = 0; i < clean.size(); ++i) {
        if (std::isdigit(static_cast<unsigned char>(clean[i]))) {
            if (std::sscanf(clean.c_str() + i, "%d.%d.%d", &major, &minor, &patch) >= 2) {
                return {major, minor, patch};
            }
        }
    }
    return {0, 0, 0};
}

bool IsBetterCandidate(const UpdateChecker::ReleaseInfo& candidate,
                       const std::optional<UpdateChecker::ReleaseInfo>& current_best) {
    if (!current_best.has_value()) {
        return true;
    }
    const auto cand_ver = ParseReleaseSemVer(candidate.version);
    const auto best_ver = ParseReleaseSemVer(current_best->version);
    if (cand_ver != best_ver) {
        return cand_ver > best_ver;
    }
    return current_best->download_url.empty() && !candidate.download_url.empty();
}

} // namespace

std::optional<UpdateChecker::ReleaseInfo> UpdateChecker::GetLatestRelease(bool include_prereleases) {
    std::optional<UpdateChecker::ReleaseInfo> best;
    try {
        const std::string gh_path = include_prereleases
                                        ? "/repos/ReiKatari/STORM_3DS/releases?per_page=10"
                                        : "/repos/ReiKatari/STORM_3DS/releases/latest";
        if (const auto response = GetResponse("api.github.com", gh_path)) {
            const auto parsed = nlohmann::json::parse(*response);
            if (include_prereleases && parsed.is_array()) {
                for (const auto& item : parsed) {
                    if (auto info = ParseGitHubRelease(item); info && IsBetterCandidate(*info, best)) {
                        best = *info;
                    }
                }
            } else if (parsed.is_object()) {
                if (auto info = ParseGitHubRelease(parsed); info && IsBetterCandidate(*info, best)) {
                    best = *info;
                }
            }
        }

        const std::pair<std::string, std::string> sgw_endpoints[] = {
            {"stormgamesworld.ru", "/api/programs/releases?repo=STORM_3DS"},
            {"stormgamesworld.ru", "/api/programs?repo=STORM_3DS"},
            {"storm-games.world", "/api/programs/releases?repo=STORM_3DS"},
            {"storm-games.world", "/api/programs?repo=STORM_3DS"},
        };
        bool sgw_succeeded = false;
        for (const auto& [host, path] : sgw_endpoints) {
            try {
                const auto sgw_resp = GetResponse(host, path);
                if (!sgw_resp) {
                    continue;
                }
                const auto parsed = nlohmann::json::parse(*sgw_resp);
                const nlohmann::json* releases_arr = nullptr;
                if (parsed.contains("repository") && parsed["repository"].is_object() &&
                    parsed["repository"].contains("releases") &&
                    parsed["repository"]["releases"].is_array()) {
                    releases_arr = &parsed["repository"]["releases"];
                } else if (parsed.contains("releases") && parsed["releases"].is_array()) {
                    releases_arr = &parsed["releases"];
                }
                if (!releases_arr) {
                    continue;
                }
                const std::string base_host = "https://" + host;
                for (const auto& rel : *releases_arr) {
                    const bool is_pre = rel.value("isPrerelease", false);
                    if (!include_prereleases && is_pre) {
                        continue;
                    }
                    if (auto info = ParseSgwRelease(rel, base_host);
                        info && IsBetterCandidate(*info, best)) {
                        best = *info;
                    }
                }
                sgw_succeeded = true;
            } catch (const nlohmann::json::exception& ex) {
                LOG_WARNING(Frontend, "SGW update endpoint {}{} JSON parse error: {}", host, path, ex.what());
            } catch (const std::exception& ex) {
                LOG_WARNING(Frontend, "SGW update endpoint {}{} error: {}", host, path, ex.what());
            }
            if (sgw_succeeded) {
                break;
            }
        }
    } catch (const nlohmann::json::exception& ex) {
        LOG_ERROR(Frontend, "Update check JSON parse error: {}", ex.what());
    } catch (const std::exception& ex) {
        LOG_ERROR(Frontend, "Update check error: {}", ex.what());
    }
    return best;
}
