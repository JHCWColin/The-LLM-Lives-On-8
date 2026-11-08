cmake_minimum_required(VERSION 3.16)
project(llama_jni CXX)

# 交叉编译关键设置：关闭 OpenMP（NDK 下避免 libomp 依赖）与 native 优化（避免 -march=native）
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_CPU_AARCH64 OFF CACHE BOOL "" FORCE)

# 强制静态库：把 llama.cpp / ggml 静态链接进单一 libllama.so，避免运行时共享库依赖与同名冲突
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# 引入 llama.cpp（作为子目录会自动把 LLAMA_STANDALONE 设为 OFF，从而不构建 tests/tools/examples）
add_subdirectory(${LLAMA_DIR} llama_build EXCLUDE_FROM_ALL)

# 编译 JNI 共享库，输出 libllama.so
add_library(llama_jni SHARED llama_jni.cpp)
target_link_libraries(llama_jni PRIVATE llama)
set_target_properties(llama_jni PROPERTIES OUTPUT_NAME "llama")
