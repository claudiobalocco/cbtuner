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

#include <jni.h>
#include "AudioEngine.h"

AudioEngine::AudioEngine(JavaVM* vm, jobject callbackObj) : javaVM(vm) {
    JNIEnv* env;
    vm->GetEnv((void**)&env, JNI_VERSION_1_6);
    kotlinCallbackObj = env->NewGlobalRef(callbackObj);
    jclass callbackClass = env->GetObjectClass(kotlinCallbackObj);
    onAudioProcessedMethodId = env->GetMethodID(callbackClass, "onAudioProcessed", "(FFF)V");
}

AudioEngine::~AudioEngine() {
    JNIEnv* env;
    javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    env->DeleteGlobalRef(kotlinCallbackObj);
    delete yinDetector;
    delete strobeOscillator;
}

void AudioEngine::start() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setFramesPerCallback(2048)
            ->setCallback(this);

    builder.openStream(stream);
    float sampleRate = (float)stream->getSampleRate();

    yinDetector = new YinPitchDetector(sampleRate, 2048);
    strobeOscillator = new StrobeOscillator(sampleRate, 5.0f);

    stream->requestStart();
}

void AudioEngine::stop() {
    if (stream) {
        stream->requestStop();
        stream->close();
    }
}

void AudioEngine::setStrobeTarget(float freq) {
    if (strobeOscillator) {
        strobeOscillator->setTargetFrequency(freq);
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    if (numFrames < 2048) return oboe::DataCallbackResult::Continue;

    float* floatData = static_cast<float*>(audioData);

    float pitchInHz = yinDetector->detectPitch(floatData);

    float strobeI = 0.0f;
    float strobeQ = 0.0f;
    strobeOscillator->processBuffer(floatData, numFrames, strobeI, strobeQ);

    JNIEnv* env;
    javaVM->AttachCurrentThread(&env, nullptr);
    env->CallVoidMethod(kotlinCallbackObj, onAudioProcessedMethodId, pitchInHz, strobeI, strobeQ);
    javaVM->DetachCurrentThread();

    return oboe::DataCallbackResult::Continue;
}

AudioEngine* engine = nullptr;
JavaVM* jvm = nullptr;

/**
 * @brief JNI library load entry point.
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    jvm = vm;
    return JNI_VERSION_1_6;
}

/**
 * @brief Starts the audio engine from Kotlin.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_cbtuner_TunerNativeBridge_startAudioEngine(JNIEnv *env, jobject thiz, jobject callback) {
    if (!engine) {
        engine = new AudioEngine(jvm, callback);
        engine->start();
    }
}

/**
 * @brief Stops the audio engine from Kotlin.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_cbtuner_TunerNativeBridge_stopAudioEngine(JNIEnv *env, jobject thiz) {
    if (engine) {
        engine->stop();
        delete engine;
        engine = nullptr;
    }
}

/**
 * @brief Sets the target frequency for the strobe oscillator from Kotlin.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_cbtuner_TunerNativeBridge_setStrobeTargetFrequency(JNIEnv *env, jobject thiz, jfloat freq) {
    if (engine) {
        engine->setStrobeTarget(freq);
    }
}