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
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/**
 * @class StrobeOscillator
 * @brief Generates a local quadrature oscillator and applies a low-pass filter to determine phase shifts relative to a target frequency.
 */
class StrobeOscillator {
private:
    float sampleRate;
    float currentPhase;
    float targetFrequency;
    float filteredI;
    float filteredQ;
    float alpha;

public:
    /**
     * @brief Constructs a StrobeOscillator.
     * @param sampleRate The audio sample rate in Hz.
     * @param cutoffHz The low-pass filter cutoff frequency in Hz.
     */
    StrobeOscillator(float sampleRate, float cutoffHz = 5.0f)
            : sampleRate(sampleRate), currentPhase(0.0f), targetFrequency(0.0f),
              filteredI(0.0f), filteredQ(0.0f) {

        float dt = 1.0f / sampleRate;
        float rc = 1.0f / (2.0f * M_PI * cutoffHz);
        alpha = dt / (rc + dt);
    }

    /**
     * @brief Sets the target frequency for the oscillator to track.
     * @param freq The target frequency in Hz.
     */
    void setTargetFrequency(float freq) {
        if (targetFrequency != freq) {
            targetFrequency = freq;
            currentPhase = 0.0f;
            filteredI = 0.0f;
            filteredQ = 0.0f;
        }
    }

    /**
     * @brief Processes an incoming audio buffer to update the I and Q filter states.
     * @param audioBuffer Pointer to the audio data.
     * @param bufferSize The number of audio frames.
     * @param outI Output parameter containing the filtered In-phase signal.
     * @param outQ Output parameter containing the filtered Quadrature signal.
     */
    void processBuffer(const float* audioBuffer, int bufferSize, float& outI, float& outQ) {
        if (targetFrequency <= 0.0f) {
            outI = 0.0f;
            outQ = 0.0f;
            return;
        }

        float phaseIncrement = 2.0f * M_PI * targetFrequency / sampleRate;

        for (int i = 0; i < bufferSize; ++i) {
            float sample = audioBuffer[i];

            float loI = std::cos(currentPhase);
            float loQ = std::sin(currentPhase);

            filteredI = alpha * (sample * loI) + (1.0f - alpha) * filteredI;
            filteredQ = alpha * (sample * loQ) + (1.0f - alpha) * filteredQ;

            currentPhase += phaseIncrement;
            if (currentPhase >= 2.0f * M_PI) {
                currentPhase -= 2.0f * M_PI;
            }
        }

        outI = filteredI;
        outQ = filteredQ;
    }
};