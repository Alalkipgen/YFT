/*
 * Minimal configuration for building LAME 3.100's encoder with the Android NDK (YFT, T18).
 * Replaces the autotools config.h: every target is a little-endian IEEE 754 machine with a
 * standard C library. The decoder (mpglib) and the SSE code are not built.
 */
#ifndef YFT_LAME_CONFIG_H
#define YFT_LAME_CONFIG_H

#define STDC_HEADERS 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_LIMITS_H 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_STRCHR 1
#define HAVE_MEMCPY 1
#define PROTOTYPES 1

typedef float ieee754_float32_t;
typedef double ieee754_float64_t;
typedef long double ieee854_float80_t;

#endif
