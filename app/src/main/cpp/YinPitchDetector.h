/*
* CBTuner - a pitch tuner for musical instruments
* Copyright (C) 2026 Claudio Balocco
* E: cbsoftware00@gmail.com
*
* This program is free software: you redistribute it and/or modify
* it under the terms of the GNU General Public License version 3 as published by
* the Free Software Foundation.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

#pragma once
#include <vector>
#include <cmath>
#include <deque>
#include <algorithm>

/**
 * @class YinPitchDetector
 * @brief Implements the YIN algorithm for real-time fundamental frequency detection.
 */
class YinPitchDetector {
private:
    int bufferSize;
    int halfBufferSize;
    float sampleRate;
    float threshold = 0.1f;
    std::vector<float> yinBuffer;

    std::deque<float> pitchHistory;
    const int historySize = 7;

    int silenceCount = 0;
    const int maxSilenceFrames = 10;

    /**
     * @brief Performs parabolic interpolation to refine the tau estimate.
     * @param tauEstimate The initial integer tau estimate.
     * @return The interpolated floating-point tau estimate.
     */
    float parabolicInterpolation(int tauEstimate) {
        if (tauEstimate < 1 || tauEstimate >= halfBufferSize - 1) return tauEstimate;
        float s0 = yinBuffer[tauEstimate - 1];
        float s1 = yinBuffer[tauEstimate];
        float s2 = yinBuffer[tauEstimate + 1];
        return tauEstimate + (s2 - s0) / (2.0f * (2.0f * s1 - s2 - s0));
    }

    /**
     * @brief Filters the raw pitch estimate using a median filter to remove outliers.
     * @param newPitch The newly calculated pitch in Hz.
     * @return The median-filtered pitch in Hz, or -1.0f if silence is detected.
     */
    float getMedianPitch(float newPitch) {
        if (newPitch > 0) {
            silenceCount = 0;
            pitchHistory.push_back(newPitch);
            if (pitchHistory.size() > historySize) {
                pitchHistory.pop_front();
            }
        } else {
            silenceCount++;
            if (silenceCount > maxSilenceFrames) {
                pitchHistory.clear();
            }
            return -1.0f;
        }

        if (pitchHistory.empty()) return -1.0f;

        std::vector<float> sortedHistory(pitchHistory.begin(), pitchHistory.end());
        std::sort(sortedHistory.begin(), sortedHistory.end());

        return sortedHistory[sortedHistory.size() / 2];
    }

public:
    /**
     * @brief Constructs a new YinPitchDetector.
     * @param sampleRate The audio sample rate in Hz.
     * @param bufferSize The size of the audio buffer to process.
     */
    YinPitchDetector(float sampleRate, int bufferSize)
            : sampleRate(sampleRate), bufferSize(bufferSize), halfBufferSize(bufferSize / 2) {
        yinBuffer.resize(halfBufferSize, 0.0f);
    }

    /**
     * @brief Analyzes an audio buffer to detect the fundamental frequency.
     * @param audioBuffer Pointer to the float audio data.
     * @return The detected fundamental frequency in Hz, or -1.0f if no pitch is found.
     */
    float detectPitch(const float* audioBuffer) {
        float sumSquares = 0.0f;
        for (int i = 0; i < bufferSize; i++) {
            sumSquares += audioBuffer[i] * audioBuffer[i];
        }
        float rms = std::sqrt(sumSquares / bufferSize);

        if (rms < 0.001f) {
            return getMedianPitch(-1.0f);
        }

        for (int tau = 0; tau < halfBufferSize; tau++) {
            yinBuffer[tau] = 0.0f;
            for (int i = 0; i < halfBufferSize; i++) {
                float delta = audioBuffer[i] - audioBuffer[i + tau];
                yinBuffer[tau] += delta * delta;
            }
        }

        yinBuffer[0] = 1.0f;
        float runningSum = 0.0f;
        for (int tau = 1; tau < halfBufferSize; tau++) {
            runningSum += yinBuffer[tau];
            yinBuffer[tau] = yinBuffer[tau] * tau / runningSum;
        }

        int tauEstimate = -1;
        for (int tau = 1; tau < halfBufferSize; tau++) {
            if (yinBuffer[tau] < threshold) {
                while (tau + 1 < halfBufferSize && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++;
                }
                tauEstimate = tau;
                break;
            }
        }

        if (tauEstimate != -1) {
            float betterTau = parabolicInterpolation(tauEstimate);
            return getMedianPitch(sampleRate / betterTau);
        }

        return getMedianPitch(-1.0f);
    }
};