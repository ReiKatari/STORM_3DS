add_library(storm3ds SHARED
    android_common/android_common.cpp
    android_common/android_common.h
    applets/mii_selector.cpp
    applets/mii_selector.h
    applets/swkbd.cpp
    applets/swkbd.h
    input_manager.cpp
    input_manager.h
    camera/ndk_camera.cpp
    camera/ndk_camera.h
    camera/still_image_camera.cpp
    camera/still_image_camera.h
    cheats/cheat.cpp
    cheats/cheat.h
    cheats/cheat_engine.cpp
    config.cpp
    config.h
    default_ini.h
    emu_window/emu_window.cpp
    emu_window/emu_window.h
    game_info.cpp
    id_cache.cpp
    id_cache.h
    ${CMAKE_BINARY_DIR}/storm_generated/jni_setting_keys.cpp
    multiplayer.cpp
    multiplayer.h
    native.cpp
    ndk_motion.cpp
    ndk_motion.h
    performance_hint.cpp
    performance_hint.h
    system_save_game.cpp
    native_log.cpp
    storm3ds_memory.cpp
    EmulationThreadCpuPolicy.cpp
    EmulationThreadCpuPolicy.h
    storm3ds_rewind.cpp
    storm3ds_extensions.cpp
)

target_include_directories(storm3ds PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/..")

target_link_libraries(storm3ds PRIVATE audio_core citra_common citra_core input_common network)
target_link_libraries(storm3ds PRIVATE android camera2ndk inih jnigraphics log mediandk yuv)

if (ENABLE_OPENGL)
    target_sources(storm3ds PRIVATE
        emu_window/emu_window_gl.cpp
        emu_window/emu_window_gl.h
    )
    target_link_libraries(storm3ds PRIVATE EGL glad)
endif()
if (ENABLE_VULKAN)
    target_sources(storm3ds PRIVATE
        emu_window/emu_window_vk.cpp
        emu_window/emu_window_vk.h
    )
    target_link_libraries(storm3ds PRIVATE vulkan-headers)
    if (STORM_ENABLE_ADRENOTOOLS AND "arm64" IN_LIST ARCHITECTURE)
        target_compile_definitions(storm3ds PRIVATE STORM_ENABLE_ADRENOTOOLS=1)
        target_link_libraries(storm3ds PRIVATE adrenotools)
    endif()
endif()
