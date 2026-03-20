#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_androidclaw_app_voice_WhisperJni_initModel(
    JNIEnv *env, jobject obj, jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading whisper model from: %s", path);

    struct whisper_context_params params = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to load whisper model");
        return 0;
    }

    LOGI("Whisper model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_androidclaw_app_voice_WhisperJni_transcribe(
    JNIEnv *env, jobject obj, jlong contextPtr, jfloatArray audioSamples) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jfloat *samples = env->GetFloatArrayElements(audioSamples, nullptr);
    jsize numSamples = env->GetArrayLength(audioSamples);

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.language = "en";
    wparams.n_threads = 4;
    wparams.no_context = true;
    wparams.single_segment = true;

    LOGI("Transcribing %d samples", numSamples);

    int result = whisper_full(ctx, wparams, samples, numSamples);
    env->ReleaseFloatArrayElements(audioSamples, samples, JNI_ABORT);

    if (result != 0) {
        LOGE("Whisper transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    int numSegments = whisper_full_n_segments(ctx);
    std::string text;
    for (int i = 0; i < numSegments; i++) {
        const char *segmentText = whisper_full_get_segment_text(ctx, i);
        text += segmentText;
    }

    LOGI("Transcription: %s", text.c_str());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_androidclaw_app_voice_WhisperJni_freeModel(
    JNIEnv *env, jobject obj, jlong contextPtr) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper model freed");
    }
}

} // extern "C"
