#include <jni.h>
#include <array>
#include <cmath>
#include <mutex>
extern "C" {
#include "rnnoise.h"
}
static std::once_flag initialized;

extern "C" JNIEXPORT jlong JNICALL
Java_com_dumuzeyn_mp3player_SpeechDenoiser_create(JNIEnv* env, jobject) {
    auto* state = rnnoise_create();
    if (!state) env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "RNNoise allocation failed");
    else std::call_once(initialized, [state] {
        std::array<float, 480> zeros{};
        rnnoise_process_frame(state, zeros.data(), zeros.data());
        rnnoise_init(state);
    });
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dumuzeyn_mp3player_SpeechDenoiser_process(JNIEnv* env, jobject, jlong handle, jfloatArray samples) {
    if (!handle || env->GetArrayLength(samples) != 480) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "RNNoise requires 480 samples");
        return;
    }
    std::array<float, 480> buffer{};
    env->GetFloatArrayRegion(samples, 0, 480, buffer.data());
    if (env->ExceptionCheck()) return;
    for (auto& sample : buffer) sample = std::isfinite(sample) ? sample * 32768.0f : 0.0f;
    rnnoise_process_frame(reinterpret_cast<DenoiseState*>(handle), buffer.data(), buffer.data());
    for (auto& sample : buffer) sample /= 32768.0f;
    env->SetFloatArrayRegion(samples, 0, 480, buffer.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_dumuzeyn_mp3player_SpeechDenoiser_destroy(JNIEnv*, jobject, jlong handle) {
    if (handle) rnnoise_destroy(reinterpret_cast<DenoiseState*>(handle));
}
