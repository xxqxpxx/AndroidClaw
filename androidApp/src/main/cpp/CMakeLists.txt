cmake_minimum_required(VERSION 3.22)
project(androidclaw_native)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/whisper.cpp)

# Only build if whisper.cpp submodule is present
if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    # Disable examples and tests in whisper.cpp
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

    add_subdirectory(${WHISPER_DIR} ${CMAKE_BINARY_DIR}/whisper)

    # JNI bridge library
    add_library(whisper_jni SHARED jni_whisper.cpp)
    target_link_libraries(whisper_jni whisper android log)
    target_include_directories(whisper_jni PRIVATE ${WHISPER_DIR}/include)
else()
    message(WARNING "whisper.cpp not found at ${WHISPER_DIR}. Skipping native build.")
endif()
