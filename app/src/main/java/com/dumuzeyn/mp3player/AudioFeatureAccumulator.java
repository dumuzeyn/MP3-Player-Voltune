package com.dumuzeyn.mp3player;

import android.media.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

/** Streaming audio statistics with bounded memory and a small, decimated spectrum. */
final class AudioFeatureAccumulator {
    private static final int FRAME_SIZE = 256;
    private static final int SPECTRAL_BINS = 48;
    private static final int TIMBRE_BANDS = 16;
    private static final int TIMBRE_COEFFICIENTS = 6;
    private static final int SPECTRAL_FRAME_STEP = 8;
    private static final double[] HANN = new double[FRAME_SIZE];
    private static final double[][] COSINE = new double[SPECTRAL_BINS][FRAME_SIZE];
    private static final double[][] SINE = new double[SPECTRAL_BINS][FRAME_SIZE];

    static {
        for (int sample = 0; sample < FRAME_SIZE; sample++) {
            HANN[sample] = 0.5d - 0.5d * Math.cos(2.0d * Math.PI * sample
                    / (FRAME_SIZE - 1));
            for (int bin = 0; bin < SPECTRAL_BINS; bin++) {
                double angle = 2.0d * Math.PI * (bin + 1) * sample / FRAME_SIZE;
                COSINE[bin][sample] = Math.cos(angle);
                SINE[bin][sample] = Math.sin(angle);
            }
        }
    }

    private final int sampleRate;
    private final int envelopeBlockSize;
    private final float[] frame = new float[FRAME_SIZE];
    private final ArrayList<Double> envelope = new ArrayList<>();
    private final ArrayList<Double> blockLevels = new ArrayList<>();
    private final double[] timbre = new double[TIMBRE_COEFFICIENTS];
    private int framePosition;
    private int completedFrames;
    private long sampleCount;
    private double squareSum;
    private double absoluteSum;
    private double peak;
    private float previous;
    private boolean hasPrevious;
    private long zeroCrossings;
    private int blockSamples;
    private double blockSquareSum;
    private double centroidSum;
    private double bandwidthSum;
    private double rolloffSum;
    private double bassRatioSum;
    private double trebleRatioSum;
    private double contrastSum;
    private int spectralFrames;

    AudioFeatureAccumulator(int sampleRate) {
        this.sampleRate = Math.max(8000, sampleRate);
        this.envelopeBlockSize = Math.max(1, this.sampleRate / 50);
    }

    void beginSegment() {
        hasPrevious = false;
        finishEnvelopeBlock();
    }

    void addPcm(ByteBuffer source, int encoding, int channels) {
        ByteBuffer pcm = source.order(ByteOrder.LITTLE_ENDIAN);
        int channelCount = Math.max(1, channels);
        while (hasFrame(pcm, encoding, channelCount)) {
            double mono = 0.0d;
            for (int channel = 0; channel < channelCount; channel++) {
                mono += readSample(pcm, encoding);
            }
            addSample((float) (mono / channelCount));
        }
    }

    void addSample(float source) {
        float sample = Math.max(-1.0f, Math.min(1.0f, source));
        double absolute = Math.abs(sample);
        sampleCount++;
        squareSum += sample * sample;
        absoluteSum += absolute;
        peak = Math.max(peak, absolute);
        if (hasPrevious && ((previous < 0.0f && sample >= 0.0f)
                || (previous >= 0.0f && sample < 0.0f))) {
            zeroCrossings++;
        }
        previous = sample;
        hasPrevious = true;
        blockSquareSum += sample * sample;
        blockSamples++;
        if (blockSamples >= envelopeBlockSize) {
            finishEnvelopeBlock();
        }
        frame[framePosition++] = sample;
        if (framePosition == FRAME_SIZE) {
            if (completedFrames++ % SPECTRAL_FRAME_STEP == 0) {
                analyzeSpectrum();
            }
            framePosition = 0;
        }
    }

    double[] finish() {
        finishEnvelopeBlock();
        if (sampleCount < sampleRate / 2L) {
            return new double[0];
        }
        double[] result = new double[TrackAudioProfile.FEATURE_COUNT];
        double rms = Math.sqrt(squareSum / sampleCount);
        result[TrackAudioProfile.BPM] = estimateBpm();
        result[TrackAudioProfile.ENERGY] = absoluteSum / sampleCount;
        result[TrackAudioProfile.LOUDNESS] = decibels(rms);
        result[TrackAudioProfile.DYNAMIC_RANGE] = dynamicRange();
        int frames = Math.max(1, spectralFrames);
        result[TrackAudioProfile.CENTROID] = centroidSum / frames;
        result[TrackAudioProfile.BANDWIDTH] = bandwidthSum / frames;
        result[TrackAudioProfile.ROLLOFF] = rolloffSum / frames;
        result[TrackAudioProfile.ZERO_CROSSING] = zeroCrossings / (double) sampleCount;
        result[TrackAudioProfile.BASS] = bassRatioSum / frames;
        result[TrackAudioProfile.TREBLE] = trebleRatioSum / frames;
        result[TrackAudioProfile.RHYTHM] = rhythmStrength();
        result[TrackAudioProfile.CONTRAST] = contrastSum / frames;
        for (int index = 0; index < TIMBRE_COEFFICIENTS; index++) {
            result[TrackAudioProfile.TIMBRE_START + index] = timbre[index] / frames;
        }
        return result;
    }

    private void finishEnvelopeBlock() {
        if (blockSamples == 0) {
            return;
        }
        double rms = Math.sqrt(blockSquareSum / blockSamples);
        envelope.add(rms);
        blockLevels.add(decibels(rms));
        blockSquareSum = 0.0d;
        blockSamples = 0;
    }

    private void analyzeSpectrum() {
        double[] powers = new double[SPECTRAL_BINS];
        double total = 0.0d;
        for (int bin = 0; bin < SPECTRAL_BINS; bin++) {
            double real = 0.0d;
            double imaginary = 0.0d;
            for (int sample = 0; sample < FRAME_SIZE; sample++) {
                double value = frame[sample] * HANN[sample];
                real += value * COSINE[bin][sample];
                imaginary -= value * SINE[bin][sample];
            }
            powers[bin] = real * real + imaginary * imaginary;
            total += powers[bin];
        }
        if (total <= 1.0e-12d) {
            spectralFrames++;
            return;
        }
        double weighted = 0.0d;
        double bass = 0.0d;
        double treble = 0.0d;
        double cumulative = 0.0d;
        int rolloffBin = powers.length - 1;
        for (int bin = 0; bin < powers.length; bin++) {
            double frequency = frequency(bin);
            weighted += frequency * powers[bin];
            if (frequency <= 250.0d) {
                bass += powers[bin];
            }
            if (frequency >= 4000.0d) {
                treble += powers[bin];
            }
            cumulative += powers[bin];
            if (cumulative >= total * 0.85d && rolloffBin == powers.length - 1) {
                rolloffBin = bin;
            }
        }
        double centroid = weighted / total;
        double spread = 0.0d;
        for (int bin = 0; bin < powers.length; bin++) {
            double delta = frequency(bin) - centroid;
            spread += delta * delta * powers[bin];
        }
        double nyquist = sampleRate / 2.0d;
        centroidSum += centroid / nyquist;
        bandwidthSum += Math.sqrt(spread / total) / nyquist;
        rolloffSum += frequency(rolloffBin) / nyquist;
        bassRatioSum += bass / total;
        trebleRatioSum += treble / total;
        contrastSum += spectralContrast(powers);
        addTimbre(powers);
        spectralFrames++;
    }

    private void addTimbre(double[] powers) {
        double[] bands = new double[TIMBRE_BANDS];
        for (int bin = 0; bin < powers.length; bin++) {
            int band = Math.min(TIMBRE_BANDS - 1, bin * TIMBRE_BANDS / powers.length);
            bands[band] += powers[bin];
        }
        for (int coefficient = 0; coefficient < TIMBRE_COEFFICIENTS; coefficient++) {
            double value = 0.0d;
            for (int band = 0; band < bands.length; band++) {
                value += Math.log1p(bands[band]) * Math.cos(Math.PI * coefficient
                        * (band + 0.5d) / bands.length);
            }
            timbre[coefficient] += value / bands.length;
        }
    }

    private double estimateBpm() {
        if (envelope.size() < 100) {
            return 0.0d;
        }
        int minimumLag = 50 * 60 / 200;
        int maximumLag = Math.min(envelope.size() / 2, 50 * 60 / 60);
        double mean = 0.0d;
        for (double value : envelope) {
            mean += value;
        }
        mean /= envelope.size();
        double best = Double.NEGATIVE_INFINITY;
        int bestLag = minimumLag;
        for (int lag = minimumLag; lag <= maximumLag; lag++) {
            double correlation = 0.0d;
            for (int index = lag; index < envelope.size(); index++) {
                correlation += (envelope.get(index) - mean)
                        * (envelope.get(index - lag) - mean);
            }
            if (correlation > best) {
                best = correlation;
                bestLag = lag;
            }
        }
        return 3000.0d / bestLag;
    }

    private double rhythmStrength() {
        if (envelope.size() < 3) {
            return 0.0d;
        }
        double positiveChanges = 0.0d;
        double level = 0.0d;
        for (int index = 1; index < envelope.size(); index++) {
            positiveChanges += Math.max(0.0d, envelope.get(index) - envelope.get(index - 1));
            level += envelope.get(index);
        }
        return positiveChanges / Math.max(1.0e-9d, level);
    }

    private double dynamicRange() {
        if (blockLevels.isEmpty()) {
            return 0.0d;
        }
        double[] sorted = new double[blockLevels.size()];
        for (int index = 0; index < sorted.length; index++) {
            sorted[index] = blockLevels.get(index);
        }
        Arrays.sort(sorted);
        return sorted[(int) ((sorted.length - 1) * 0.90d)]
                - sorted[(int) ((sorted.length - 1) * 0.10d)];
    }

    private static double spectralContrast(double[] powers) {
        double[] sorted = powers.clone();
        Arrays.sort(sorted);
        int quarter = Math.max(1, sorted.length / 4);
        double low = 0.0d;
        double high = 0.0d;
        for (int index = 0; index < quarter; index++) {
            low += sorted[index];
            high += sorted[sorted.length - 1 - index];
        }
        return Math.log1p(high / quarter) - Math.log1p(low / quarter);
    }

    private double frequency(int bin) {
        return (bin + 1.0d) * sampleRate / FRAME_SIZE;
    }

    private static double decibels(double value) {
        return 20.0d * Math.log10(Math.max(1.0e-9d, value));
    }

    private static boolean hasFrame(ByteBuffer source, int encoding, int channels) {
        return source.remaining() >= bytesPerSample(encoding) * channels;
    }

    private static int bytesPerSample(int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT
                || encoding == AudioFormat.ENCODING_PCM_32BIT) {
            return 4;
        }
        if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
            return 3;
        }
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            return 1;
        }
        return 2;
    }

    private static float readSample(ByteBuffer source, int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            return source.getFloat();
        }
        if (encoding == AudioFormat.ENCODING_PCM_32BIT) {
            return source.getInt() / 2147483648.0f;
        }
        if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
            int value = (source.get() & 0xff) | ((source.get() & 0xff) << 8)
                    | (source.get() << 16);
            return value / 8388608.0f;
        }
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            return ((source.get() & 0xff) - 128) / 128.0f;
        }
        return source.getShort() / 32768.0f;
    }
}
