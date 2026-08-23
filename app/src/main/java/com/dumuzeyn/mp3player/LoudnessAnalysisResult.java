package com.dumuzeyn.mp3player;

final class LoudnessAnalysisResult {
    final float integratedLufs;
    final float peakDbfs;

    LoudnessAnalysisResult(float integratedLufs, float peakDbfs) {
        this.integratedLufs = integratedLufs;
        this.peakDbfs = peakDbfs;
    }
}
