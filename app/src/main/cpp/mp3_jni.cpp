#include <jni.h>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <vector>
#include <lame.h>

namespace {
uint16_t little16(const unsigned char* bytes) {
    return static_cast<uint16_t>(bytes[0] | (bytes[1] << 8));
}

uint32_t little32(const unsigned char* bytes) {
    return static_cast<uint32_t>(bytes[0]) |
        (static_cast<uint32_t>(bytes[1]) << 8) |
        (static_cast<uint32_t>(bytes[2]) << 16) |
        (static_cast<uint32_t>(bytes[3]) << 24);
}

int encodeWave(const char* inputPath, const char* outputPath) {
    FILE* input = std::fopen(inputPath, "rb");
    if (!input) return -1;
    FILE* output = std::fopen(outputPath, "wb");
    if (!output) {
        std::fclose(input);
        return -2;
    }

    int result = -3;
    lame_global_flags* lame = nullptr;
    unsigned char header[44]{};
    if (std::fread(header, 1, sizeof(header), input) != sizeof(header) ||
        std::memcmp(header, "RIFF", 4) != 0 || std::memcmp(header + 8, "WAVEfmt ", 8) != 0 ||
        std::memcmp(header + 36, "data", 4) != 0 || little16(header + 20) != 1 ||
        little16(header + 34) != 16) goto cleanup;

    {
        const int channels = little16(header + 22);
        const int sampleRate = static_cast<int>(little32(header + 24));
        uint32_t remaining = little32(header + 40);
        if ((channels != 1 && channels != 2) || sampleRate <= 0 || remaining == 0) goto cleanup;

        lame = lame_init();
        if (!lame) { result = -4; goto cleanup; }
        lame_set_num_channels(lame, channels);
        lame_set_in_samplerate(lame, sampleRate);
        lame_set_brate(lame, 256);
        lame_set_quality(lame, 2);
        if (lame_init_params(lame) < 0) { result = -5; goto cleanup; }

        constexpr int framesPerChunk = 8192;
        std::vector<short> pcm(static_cast<size_t>(framesPerChunk * channels));
        std::vector<unsigned char> bytes(pcm.size() * 2);
        std::vector<unsigned char> encoded(static_cast<size_t>(7200 + framesPerChunk * 1.25));
        while (remaining > 0) {
            const size_t wanted = std::min<size_t>(bytes.size(), remaining);
            const size_t read = std::fread(bytes.data(), 1, wanted, input);
            if (read == 0 || read % (channels * 2) != 0) { result = -6; goto cleanup; }
            remaining -= static_cast<uint32_t>(read);
            const int frames = static_cast<int>(read / (channels * 2));
            for (size_t i = 0; i < read / 2; ++i) {
                pcm[i] = static_cast<short>(little16(bytes.data() + i * 2));
            }
            const int count = channels == 2
                ? lame_encode_buffer_interleaved(lame, pcm.data(), frames, encoded.data(), encoded.size())
                : lame_encode_buffer(lame, pcm.data(), pcm.data(), frames, encoded.data(), encoded.size());
            if (count < 0 || (count > 0 && std::fwrite(encoded.data(), 1, count, output) !=
                static_cast<size_t>(count))) { result = -7; goto cleanup; }
        }
        {
            const int count = lame_encode_flush(lame, encoded.data(), encoded.size());
            if (count < 0 || (count > 0 && std::fwrite(encoded.data(), 1, count, output) !=
                static_cast<size_t>(count))) { result = -8; goto cleanup; }
        }
        result = 0;
    }

cleanup:
    if (lame) lame_close(lame);
    std::fclose(input);
    std::fclose(output);
    if (result != 0) std::remove(outputPath);
    return result;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dumuzeyn_mp3player_Mp3AudioConverter_encodeWave(
    JNIEnv* env, jobject, jstring inputPath, jstring outputPath) {
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    const int result = encodeWave(input, output);
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    return result;
}
