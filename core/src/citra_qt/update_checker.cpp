// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project / STORM TEAM
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

#include <optional>
#include <string>
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
    HINTERNET hSession = WinHttpOpen(L"STORM-3DS-Updater/1.6", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
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
    if (!tag.empty() && (tag[0] == 'v' || tag[0] == 'V')) {
        tag.erase(tag.begin());
    }
    return tag;
}

} // namespace

std::optional<std::string> UpdateChecker::GetLatestRelease(bool include_prereleases) {
    try {
        const std::string gh_path = include_prereleases
                                        ? "/repos/ReiKatari/STORM_3DS/releases?per_page=10"
                                        : "/repos/ReiKatari/STORM_3DS/releases/latest";
        if (const auto response = GetResponse("api.github.com", gh_path)) {
            const auto parsed = nlohmann::json::parse(*response);
            if (include_prereleases && parsed.is_array()) {
                for (const auto& item : parsed) {
                    if (!item.value("draft", false) && item.contains("tag_name") &&
                        item["tag_name"].is_string()) {
                        return CleanVersionTag(item["tag_name"].get<std::string>());
                    }
                }
            } else if (parsed.is_object() && parsed.contains("tag_name") &&
                       parsed["tag_name"].is_string()) {
                return CleanVersionTag(parsed["tag_name"].get<std::string>());
            }
        }

        if (const auto sgw_resp =
                GetResponse("stormgamesworld.ru", "/api/programs/releases?repo=STORM_3DS")) {
            const auto parsed = nlohmann::json::parse(*sgw_resp);
            if (parsed.contains("releases") && parsed["releases"].is_array()) {
                for (const auto& rel : parsed["releases"]) {
                    const bool is_pre = rel.value("isPrerelease", false);
                    if (!include_prereleases && is_pre) {
                        continue;
                    }
                    std::string ver = rel.value("version", "");
                    if (ver.empty()) {
                        ver = rel.value("tag_name", "");
                    }
                    ver = CleanVersionTag(ver);
                    if (!ver.empty()) {
                        return ver;
                    }
                }
            }
        }
    } catch (const nlohmann::json::exception& ex) {
        LOG_ERROR(Frontend, "Update check JSON parse error: {}", ex.what());
    } catch (const std::exception& ex) {
        LOG_ERROR(Frontend, "Update check error: {}", ex.what());
    }
    return {};
}
