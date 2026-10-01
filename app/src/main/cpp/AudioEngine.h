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
#include <oboe/Oboe.h>
#include <jni.h>
#include <memory>
#include "YinPitchDetector.h"
#include "StrobeOscillator.h"

/**
 * @class AudioEngine
 * @brief Manages the Oboe audio stream, pitch detection, and strobe oscillator processing.
 */
class AudioEngine : public oboe::AudioStreamCallback {
private:
    std::shared_ptr<oboe::AudioStream> stream;
    YinPitchDetector* yinDetector = nullptr;
    StrobeOscillator* strobeOscillator = nullptr;
    JavaVM* javaVM;
    jobject kotlinCallbackObj;
    jmethodID onAudioProcessedMethodId;

public:
    /**
     * @brief Constructs an AudioEngine and initializes the JNI callbacks.
     * @param vm Pointer to the JavaVM.
     * @param callbackObj JNI global reference to the Kotlin listener object.
     */
    AudioEngine(JavaVM* vm, jobject callbackObj);

    /**
     * @brief Destructor to clean up resources and JNI references.
     */
    ~AudioEngine();

    /**
     * @brief Configures and starts the Oboe audio stream.
     */
    void start();

    /**
     * @brief Stops and closes the Oboe audio stream.
     */
    void stop();

    /**
     * @brief Sets the target frequency for the strobe oscillator.
     * @param freq The target frequency in Hz.
     */
    void setStrobeTarget(float freq);

    /**
     * @brief Callback invoked by Oboe when audio data is ready for processing.
     * @param oboeStream The audio stream triggering the callback.
     * @param audioData Pointer to the raw audio buffer.
     * @param numFrames The number of audio frames in the buffer.
     * @return The status of the data callback indicating whether to continue.
     */
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
};