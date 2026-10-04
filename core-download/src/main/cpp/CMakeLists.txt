# YFT MP3 encoder (T18): LAME 3.100 as a shared library plus a small JNI glue library.
# LAME is LGPL-2.0-or-later and stays a separate libmp3lame.so so it can be replaced; see
# docs/THIRD_PARTY_NOTICES.md. Only the encoder is built (no mpglib decoder, no SSE code).
cmake_minimum_required(VERSION 3.22.1)
project(yft_mp3 C)

set(LAME_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lame-3.100)

add_library(mp3lame SHARED
    ${LAME_DIR}/libmp3lame/VbrTag.c
    ${LAME_DIR}/libmp3lame/bitstream.c
    ${LAME_DIR}/libmp3lame/encoder.c
    ${LAME_DIR}/libmp3lame/fft.c
    ${LAME_DIR}/libmp3lame/gain_analysis.c
    ${LAME_DIR}/libmp3lame/id3tag.c
    ${LAME_DIR}/libmp3lame/lame.c
    ${LAME_DIR}/libmp3lame/mpglib_interface.c
    ${LAME_DIR}/libmp3lame/newmdct.c
    ${LAME_DIR}/libmp3lame/presets.c
    ${LAME_DIR}/libmp3lame/psymodel.c
    ${LAME_DIR}/libmp3lame/quantize.c
    ${LAME_DIR}/libmp3lame/quantize_pvt.c
    ${LAME_DIR}/libmp3lame/reservoir.c
    ${LAME_DIR}/libmp3lame/set_get.c
    ${LAME_DIR}/libmp3lame/tables.c
    ${LAME_DIR}/libmp3lame/takehiro.c
    ${LAME_DIR}/libmp3lame/util.c
    ${LAME_DIR}/libmp3lame/vbrquantize.c
    ${LAME_DIR}/libmp3lame/version.c
)
target_include_directories(mp3lame
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/lame-config ${LAME_DIR}/libmp3lame
    PUBLIC ${LAME_DIR}/include
)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
# Upstream code, built unchanged: its warnings are not ours to fix.
target_compile_options(mp3lame PRIVATE -O2 -w)
target_link_libraries(mp3lame m)

add_library(yft_mp3 SHARED yft_mp3_jni.c)
target_compile_options(yft_mp3 PRIVATE -O2 -Wall -Werror)
target_link_libraries(yft_mp3 mp3lame)
