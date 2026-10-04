/*
 * JNI glue between YFT's LameMp3Encoder (Kotlin) and LAME 3.100 (T18).
 *
 * One handle is one encoder: constant bitrate, no Xing/LAME tag and no automatic ID3 tag, because
 * YFT writes its own ID3v2 title before the first frame.
 */
#include <jni.h>
#include <stdint.h>

#include "lame.h"

#define YFT_ERROR_HANDLE (-100)
#define YFT_ERROR_ARGUMENT (-101)
#define YFT_ERROR_MEMORY (-102)

static lame_global_flags *flags_of(jlong handle) {
    return (lame_global_flags *) (intptr_t) handle;
}

JNIEXPORT jlong JNICALL
Java_com_alal_yft_core_download_LameNative_open(
        JNIEnv *env, jobject thiz, jint sample_rate, jint channels, jint bitrate_kbps) {
    (void) env;
    (void) thiz;
    if (channels < 1 || channels > 2) return 0;
    lame_global_flags *flags = lame_init();
    if (flags == NULL) return 0;
    lame_set_in_samplerate(flags, sample_rate);
    lame_set_num_channels(flags, channels);
    lame_set_mode(flags, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_VBR(flags, vbr_off);
    lame_set_brate(flags, bitrate_kbps);
    lame_set_quality(flags, 5);
    lame_set_bWriteVbrTag(flags, 0);
    lame_set_write_id3tag_automatic(flags, 0);
    if (lame_init_params(flags) < 0) {
        lame_close(flags);
        return 0;
    }
    return (jlong) (intptr_t) flags;
}

JNIEXPORT jint JNICALL
Java_com_alal_yft_core_download_LameNative_encode(
        JNIEnv *env, jobject thiz, jlong handle, jshortArray pcm, jint samples_per_channel,
        jbyteArray output) {
    (void) thiz;
    lame_global_flags *flags = flags_of(handle);
    if (flags == NULL) return YFT_ERROR_HANDLE;
    if (pcm == NULL || output == NULL || samples_per_channel < 0) return YFT_ERROR_ARGUMENT;
    int channels = lame_get_num_channels(flags);
    jsize pcm_length = (*env)->GetArrayLength(env, pcm);
    if ((jlong) samples_per_channel * channels > pcm_length) return YFT_ERROR_ARGUMENT;
    jsize output_length = (*env)->GetArrayLength(env, output);

    jshort *input = (*env)->GetShortArrayElements(env, pcm, NULL);
    if (input == NULL) return YFT_ERROR_MEMORY;
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (bytes == NULL) {
        (*env)->ReleaseShortArrayElements(env, pcm, input, JNI_ABORT);
        return YFT_ERROR_MEMORY;
    }
    int written;
    if (channels == 1) {
        written = lame_encode_buffer(
                flags, input, input, samples_per_channel, (unsigned char *) bytes, output_length);
    } else {
        written = lame_encode_buffer_interleaved(
                flags, input, samples_per_channel, (unsigned char *) bytes, output_length);
    }
    (*env)->ReleaseShortArrayElements(env, pcm, input, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, bytes, written > 0 ? 0 : JNI_ABORT);
    return written;
}

JNIEXPORT jint JNICALL
Java_com_alal_yft_core_download_LameNative_flush(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray output) {
    (void) thiz;
    lame_global_flags *flags = flags_of(handle);
    if (flags == NULL) return YFT_ERROR_HANDLE;
    if (output == NULL) return YFT_ERROR_ARGUMENT;
    jsize output_length = (*env)->GetArrayLength(env, output);
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (bytes == NULL) return YFT_ERROR_MEMORY;
    int written = lame_encode_flush(flags, (unsigned char *) bytes, output_length);
    (*env)->ReleaseByteArrayElements(env, output, bytes, written > 0 ? 0 : JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_alal_yft_core_download_LameNative_close(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    lame_global_flags *flags = flags_of(handle);
    if (flags != NULL) lame_close(flags);
}
