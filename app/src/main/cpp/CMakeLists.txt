cmake_minimum_required(VERSION 3.22.1)
project(whisper-jni)

set(CMAKE_CXX_STANDARD 17)

# Force high optimization levels for all targets (both whisper and ggml)
if (NOT MSVC)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math -fno-finite-math-only")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -fno-finite-math-only")
    add_compile_options(-O3 -ffast-math -fno-finite-math-only)
    
    # Overwrite CMake's default debug flags to prevent unoptimized compilation in Android Studio debug runs
    set(CMAKE_C_FLAGS_DEBUG "-O3 -ffast-math -fno-finite-math-only -DNDEBUG")
    set(CMAKE_CXX_FLAGS_DEBUG "-O3 -ffast-math -fno-finite-math-only -DNDEBUG")
    set(CMAKE_C_FLAGS_RELEASE "-O3 -ffast-math -fno-finite-math-only -DNDEBUG")
    set(CMAKE_CXX_FLAGS_RELEASE "-O3 -ffast-math -fno-finite-math-only -DNDEBUG")
    
    if (ANDROID)
        if (${ANDROID_ABI} STREQUAL "arm64-v8a")
            set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+dotprod")
            set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod")
            add_compile_options(-march=armv8.2-a+dotprod)
            add_definitions(-D__ARM_NEON -D__ARM_FEATURE_DOTPROD)
        endif()
    endif()
endif()

# Path to the cloned whisper.cpp repository
set(WHISPER_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper-cpp)

# Disable ggml examples and tests
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Add ggml subdirectory
add_subdirectory(${WHISPER_SRC_DIR}/ggml ggml EXCLUDE_FROM_ALL)

# Add whisper library
add_library(whisper SHARED
    ${WHISPER_SRC_DIR}/src/whisper.cpp
    jni.c
)

# Compiler definitions
target_compile_definitions(whisper PUBLIC
    GGML_USE_CPU
    WHISPER_VERSION=\"1.8.5\"
)

# Include directories
target_include_directories(whisper PUBLIC
    ${WHISPER_SRC_DIR}/include
    ${WHISPER_SRC_DIR}/src
    ${WHISPER_SRC_DIR}/ggml/include
    ${WHISPER_SRC_DIR}/ggml/src
)

# Link libraries
# ggml target is defined in ggml/src/CMakeLists.txt
target_link_libraries(whisper log android ggml)
