package com.dumuzeyn.mp3player;

import android.media.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/** EBU R128-style K-weighting, absolute/relative gating and inter-sample peak estimate. */
final class R128LoudnessMeter {
    private static final double ABSOLUTE_GATE_LUFS = -70.0;
    private static final double RELATIVE_GATE_LU = 10.0;

    private final int channels;
    private final Biquad[][] filters;
    private final double[] previousSamples;
    private final double[] frameChannelEnergy;
    private final double[] energyWindow;
    private final int stepFrames;
    private final ArrayList<Double> blockEnergies = new ArrayList<>();
    private int channelIndex;
    private int windowPosition;
    private int windowFrames;
    private int framesSinceBlock;
    private double rollingEnergy;
    private double peak;

    R128LoudnessMeter(int sampleRate, int channelCount) {
        int safeRate = Math.max(8000, sampleRate);
        channels = Math.max(1, channelCount);
        filters = new Biquad[channels][2];
        previousSamples = new double[channels];
        frameChannelEnergy = new double[channels];
        energyWindow = new double[Math.max(1, Math.round(safeRate * 0.4f))];
        stepFrames = Math.max(1, Math.round(safeRate * 0.1f));
        for (int channel = 0; channel < channels; channel++) {
            filters[channel][0] = Biquad.highShelf(safeRate, 1500.0, 4.0, Math.sqrt(0.5));
            filters[channel][1] = Biquad.highPass(safeRate, 38.0, 0.5);
        }
    }

    void add(ByteBuffer buffer, int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            while (buffer.remaining() >= 4) {
                addSample(clamp(buffer.getFloat()));
            }
        } else {
            while (buffer.remaining() >= 2) {
                addSample(buffer.getShort() / 32768.0);
            }
        }
    }

    void addSamples(float[] interleaved) {
        if (interleaved == null) {
            return;
        }
        for (float sample : interleaved) {
            addSample(clamp(sample));
        }
    }

    LoudnessAnalysisResult result() {
        if (blockEnergies.isEmpty() || peak <= 0.0) {
            return null;
        }
        ArrayList<Double> absolute = gated(blockEnergies, ABSOLUTE_GATE_LUFS);
        if (absolute.isEmpty()) {
            return null;
        }
        double ungatedLoudness = loudness(mean(absolute));
        ArrayList<Double> relative = gated(absolute, ungatedLoudness - RELATIVE_GATE_LU);
        double integrated = loudness(mean(relative.isEmpty() ? absolute : relative));
        double peakDb = 20.0 * Math.log10(Math.max(1.0e-12, peak));
        return new LoudnessAnalysisResult((float) integrated, (float) peakDb);
    }

    private void addSample(double sample) {
        int channel = channelIndex;
        double previous = previousSamples[channel];
        for (int step = 1; step <= 4; step++) {
            double interpolated = previous + ((sample - previous) * step / 4.0);
            peak = Math.max(peak, Math.abs(interpolated));
        }
        previousSamples[channel] = sample;
        double weighted = filters[channel][1].process(filters[channel][0].process(sample));
        frameChannelEnergy[channel] = weighted * weighted;
        channelIndex++;
        if (channelIndex >= channels) {
            channelIndex = 0;
            completeFrame();
        }
    }

    private void completeFrame() {
        double energy = 0.0;
        for (int channel = 0; channel < channels; channel++) {
            energy += frameChannelEnergy[channel];
        }
        if (windowFrames >= energyWindow.length) {
            rollingEnergy -= energyWindow[windowPosition];
        } else {
            windowFrames++;
        }
        energyWindow[windowPosition] = energy;
        rollingEnergy += energy;
        windowPosition = (windowPosition + 1) % energyWindow.length;
        framesSinceBlock++;
        if (windowFrames == energyWindow.length && framesSinceBlock >= stepFrames) {
            blockEnergies.add(rollingEnergy / energyWindow.length);
            framesSinceBlock = 0;
        }
    }

    private static ArrayList<Double> gated(ArrayList<Double> source, double gateLufs) {
        ArrayList<Double> accepted = new ArrayList<>();
        for (double energy : source) {
            if (loudness(energy) >= gateLufs) {
                accepted.add(energy);
            }
        }
        return accepted;
    }

    private static double loudness(double energy) {
        return -0.691 + (10.0 * Math.log10(Math.max(1.0e-15, energy)));
    }

    private static double mean(ArrayList<Double> values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return values.isEmpty() ? 0.0 : sum / values.size();
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static final class Biquad {
        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double x1;
        private double x2;
        private double y1;
        private double y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        double process(double input) {
            double output = (b0 * input) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2);
            x2 = x1;
            x1 = input;
            y2 = y1;
            y1 = output;
            return output;
        }

        static Biquad highPass(double rate, double frequency, double q) {
            double w0 = 2.0 * Math.PI * frequency / rate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2.0 * q);
            return new Biquad((1.0 + cos) / 2.0, -(1.0 + cos),
                    (1.0 + cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
        }

        static Biquad highShelf(double rate, double frequency, double gainDb, double q) {
            double a = Math.pow(10.0, gainDb / 40.0);
            double w0 = 2.0 * Math.PI * frequency / rate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2.0 * q);
            double root = 2.0 * Math.sqrt(a) * alpha;
            double b0 = a * ((a + 1.0) + ((a - 1.0) * cos) + root);
            double b1 = -2.0 * a * ((a - 1.0) + ((a + 1.0) * cos));
            double b2 = a * ((a + 1.0) + ((a - 1.0) * cos) - root);
            double a0 = (a + 1.0) - ((a - 1.0) * cos) + root;
            double a1 = 2.0 * ((a - 1.0) - ((a + 1.0) * cos));
            double a2 = (a + 1.0) - ((a - 1.0) * cos) - root;
            return new Biquad(b0, b1, b2, a0, a1, a2);
        }
    }
}
