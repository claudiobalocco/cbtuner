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

package com.example.cbtuner

/**
 * Acts as the interface binding the Kotlin application to the underlying C++ audio engine via JNI.
 */
class TunerNativeBridge {

    /**
     * Interface to receive synchronous audio processing results directly from the native thread.
     */
    interface AudioProcessListener {
        /**
         * Invoked every time the native engine completes processing an audio buffer.
         *
         * @param pitchInHz The fundamental frequency detected by YIN.
         * @param strobeI The In-Phase component computed by the Strobe Oscillator.
         * @param strobeQ The Quadrature component computed by the Strobe Oscillator.
         */
        fun onAudioProcessed(pitchInHz: Float, strobeI: Float, strobeQ: Float)
    }

    companion object {
        init {
            System.loadLibrary("cbtuner")
        }
    }

    /**
     * Initializes and starts the native Oboe audio stream.
     * @param listener The callback object to receive real-time audio metrics.
     */
    external fun startAudioEngine(listener: AudioProcessListener)

    /**
     * Stops and tears down the native audio engine.
     */
    external fun stopAudioEngine()

    /**
     * Updates the base tracking frequency inside the native C++ strobe oscillator.
     * @param freq The target frequency in Hz.
     */
    external fun setStrobeTargetFrequency(freq: Float)
}