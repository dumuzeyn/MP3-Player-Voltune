cmake_minimum_required(VERSION 3.22.1)
project(voltune_audio LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(THIRD_PARTY "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party")
set(RNNOISE "${THIRD_PARTY}/rnnoise")
if(NOT EXISTS "${RNNOISE}/src/rnn_data.c")
    message(FATAL_ERROR "Run git submodule update --init third_party/rnnoise")
endif()
add_library(voltune_audio SHARED speech_jni.cpp
    ${RNNOISE}/src/denoise.c ${RNNOISE}/src/rnn.c ${RNNOISE}/src/rnn_data.c
    ${RNNOISE}/src/pitch.c ${RNNOISE}/src/kiss_fft.c ${RNNOISE}/src/celt_lpc.c)
target_include_directories(voltune_audio PRIVATE ${RNNOISE}/include ${RNNOISE}/src)
target_compile_options(voltune_audio PRIVATE -O2)
if(CMAKE_HOST_WIN32)
    # Windows 8.3 paths strip "++" from clang++ and otherwise select the C linker driver.
    target_link_options(voltune_audio PRIVATE --driver-mode=g++)
endif()
target_link_libraries(voltune_audio m)

set(LAME "${THIRD_PARTY}/lame/liblame/src/main/cpp/liblame")
if(NOT EXISTS "${LAME}/lame.c")
    message(FATAL_ERROR "Run git submodule update --init third_party/lame")
endif()
set(LAME_SOURCES
    ${LAME}/bitstream.c ${LAME}/id3tag.c ${LAME}/presets.c
    ${LAME}/reservoir.c ${LAME}/util.c ${LAME}/encoder.c ${LAME}/lame.c
    ${LAME}/psymodel.c ${LAME}/set_get.c ${LAME}/vbrquantize.c
    ${LAME}/fft.c ${LAME}/quantize.c ${LAME}/tables.c ${LAME}/VbrTag.c
    ${LAME}/gain_analysis.c ${LAME}/newmdct.c ${LAME}/quantize_pvt.c
    ${LAME}/takehiro.c ${LAME}/version.c ${LAME}/vector/xmm_quantize_sub.c)
add_library(voltune_mp3 SHARED mp3_jni.cpp ${LAME_SOURCES})
target_include_directories(voltune_mp3 PRIVATE ${LAME})
target_compile_definitions(voltune_mp3 PRIVATE HAVE_CONFIG_H)
target_compile_options(voltune_mp3 PRIVATE -O3)
if(CMAKE_HOST_WIN32)
    target_link_options(voltune_mp3 PRIVATE --driver-mode=g++)
endif()
target_link_libraries(voltune_mp3 m)

set(DEMUCS "${THIRD_PARTY}/demucs")
if(NOT EXISTS "${DEMUCS}/vendor/eigen/Eigen/Dense")
    message(FATAL_ERROR "Initialize third_party/demucs and its vendor/eigen submodule")
endif()
file(GLOB DEMUCS_SOURCES "${DEMUCS}/src/*.cpp")
include(prepare_demucs.cmake)
add_library(voltune_demucs SHARED demucs_jni.cpp ${DEMUCS_SOURCES})
target_include_directories(voltune_demucs PRIVATE ${CMAKE_CURRENT_LIST_DIR} ${GENERATED_DEMUCS} ${DEMUCS}/vendor/eigen)
target_compile_definitions(voltune_demucs PRIVATE EIGEN_MPL2_ONLY NDEBUG)
target_compile_options(voltune_demucs PRIVATE -O3 -fopenmp -include chrono)
target_link_options(voltune_demucs PRIVATE -fopenmp -static-openmp)
if(CMAKE_HOST_WIN32)
    target_link_options(voltune_demucs PRIVATE --driver-mode=g++)
endif()
