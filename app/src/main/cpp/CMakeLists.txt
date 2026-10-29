cmake_minimum_required(VERSION 3.22.1)
project(vera_jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# سرعة كاملة حتى لو البناء debug
set(CMAKE_C_FLAGS_DEBUG   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# جوالات 2018 وطالع تدعم dotprod، وهذا يسرّع التوليد هواية
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)

include(FetchContent)
# اذا فشل البناء بسبب نسخة llama.cpp، غيّر الـ tag (مثلاً b5300 او b5500)
FetchContent_Declare(llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG b5400
    GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(llama)

add_library(vera-jni SHARED vera_jni.cpp)
target_link_libraries(vera-jni llama android log)
target_link_options(vera-jni PRIVATE -Wl,-z,max-page-size=16384)
