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

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Holds the current visual state for the Strobe Tuner visualization.
 */
data class StrobeState(
    val continuousPhase: Float = 0f,
    val magnitude: Float = 0f,
    val targetFrequency: Float = 0f,
    val strobeCents: Float = 0f
)

/**
 * Holds the current tracking state for the standard linear pitch tuner.
 */
data class TuningState(
    val isActive: Boolean = false,
    val closestNote: String = "-",
    val centsOff: Float = 0f,
    val isInTune: Boolean = false
)

/**
 * Manages the persistence, math state, and UI updates for the tuner application.
 */
class TunerViewModel(application: Application) : AndroidViewModel(application), TunerNativeBridge.AudioProcessListener {

    private val prefs = application.getSharedPreferences("TunerSettings", Context.MODE_PRIVATE)
    private val nativeBridge = TunerNativeBridge()

    private val _tuningState = MutableStateFlow(TuningState())
    val tuningState = _tuningState.asStateFlow()

    private val _strobeState = MutableStateFlow(StrobeState())
    val strobeState = _strobeState.asStateFlow()

    val a4Reference = MutableStateFlow(prefs.getFloat("A4_REF", 440f))
    val toleranceCents = MutableStateFlow(prefs.getFloat("TOLERANCE", 5f))
    val isDarkTheme = MutableStateFlow(prefs.getBoolean("DARK_THEME", false))
    val selectedTuning = MutableStateFlow(prefs.getString("TUNING_SYSTEM", "12TET (standard)") ?: "12TET (standard)")

    val customScalaContent = MutableStateFlow(prefs.getString("SCALA_CONTENT", "") ?: "")
    val scalaFileName = MutableStateFlow(prefs.getString("SCALA_FILE_NAME", "None") ?: "None")
    val scalaRootPitch = MutableStateFlow(prefs.getString("SCALA_ROOT_PITCH", "C") ?: "C")
    val scalaCustomFreq = MutableStateFlow(prefs.getFloat("SCALA_CUSTOM_FREQ", 261.63f))
    val strobeSpeedFactor = MutableStateFlow(1)

    private var smoothedFrequency = 0f
    private val smoothingFactor = 0.3f
    private var currentTargetFreq = 0f
    private var previousRawPhase = 0f
    private var continuousPhase = 0f
    private var smoothedStrobeCents = 0f
    private var silenceFrames = 0
    private val maxSilenceFrames = 10

    /**
     * Updates the root pitch anchor to be used when a Scala tuning file is active.
     */
    fun updateScalaRootPitch(pitch: String) {
        scalaRootPitch.value = pitch
        prefs.edit { putString("SCALA_ROOT_PITCH", pitch) }
        currentTargetFreq = 0f
    }

    /**
     * Updates the custom base frequency to be used when the user dictates a custom Hz anchor.
     */
    fun updateScalaCustomFreq(freq: Float) {
        scalaCustomFreq.value = freq
        prefs.edit { putFloat("SCALA_CUSTOM_FREQ", freq) }
        currentTargetFreq = 0f
    }

    /**
     * Initializes the native audio engine and begins active listening.
     */
    fun startTuning() {
        nativeBridge.startAudioEngine(this)
    }

    /**
     * Shuts down the native audio engine and clears the current tuning state.
     */
    fun stopTuning() {
        nativeBridge.stopAudioEngine()
        _tuningState.update { TuningState(isActive = false) }
        _strobeState.update { StrobeState() }
        smoothedFrequency = 0f
    }

    private fun getActiveTuningSystem(): TuningSystem {
        return when (selectedTuning.value) {
            "31TET (Huygens-Fokker)" -> TuningPresets.huygens31TET
            "24TET (quarter tone)" -> TuningPresets.quarterTone24TET
            "Custom Scala" -> {
                try {
                    ScalaTuning.parse(customScalaContent.value)
                } catch (_: Exception) {
                    TuningPresets.standard12TET
                }
            }
            else -> TuningPresets.standard12TET
        }
    }

    /**
     * Parses and persists a custom Scala tuning system file in memory.
     */
    fun loadCustomScala(content: String, systemName: String = "Custom Scala", fileName: String = "Unknown.scl") {
        prefs.edit {
            putString("SCALA_CONTENT", content)
            putString("TUNING_SYSTEM", systemName)
            putString("SCALA_FILE_NAME", fileName)
        }
        customScalaContent.value = content
        selectedTuning.value = systemName
        scalaFileName.value = fileName
        currentTargetFreq = 0f
    }

    /**
     * JNI Callback invoked when the native C++ engine finishes an audio buffer frame.
     */
    override fun onAudioProcessed(pitchInHz: Float, strobeI: Float, strobeQ: Float) {
        val currentRawPhase = atan2(strobeQ, strobeI)
        val magnitude = sqrt(strobeI * strobeI + strobeQ * strobeQ)

        var dPhase = currentRawPhase - previousRawPhase
        while (dPhase > Math.PI) dPhase -= (2 * Math.PI).toFloat()
        while (dPhase < -Math.PI) dPhase += (2 * Math.PI).toFloat()
        previousRawPhase = currentRawPhase

        continuousPhase += (dPhase / strobeSpeedFactor.value)

        var instantaneousCents = 0f
        if (currentTargetFreq > 0f) {
            val dt = 2048f / 48000f
            val deltaF = dPhase / (2f * Math.PI.toFloat() * dt)
            val frequencyRatio = 1f + (deltaF / currentTargetFreq)

            if (frequencyRatio > 0f) {
                instantaneousCents = 1200f * kotlin.math.log2(frequencyRatio)
            }
        }

        smoothedStrobeCents = if (smoothedStrobeCents == 0f) {
            instantaneousCents
        } else {
            (0.1f * instantaneousCents) + (0.9f * smoothedStrobeCents)
        }

        _strobeState.update {
            it.copy(
                continuousPhase = continuousPhase,
                magnitude = magnitude,
                targetFrequency = currentTargetFreq,
                strobeCents = smoothedStrobeCents
            )
        }

        if (pitchInHz <= 0f) {
            silenceFrames++
            if (silenceFrames > maxSilenceFrames) {
                _tuningState.update { TuningState(isActive = false) }
                smoothedFrequency = 0f
            }
            return
        }

        silenceFrames = 0

        smoothedFrequency = if (smoothedFrequency == 0f) {
            pitchInHz
        } else {
            (smoothingFactor * pitchInHz) + ((1f - smoothingFactor) * smoothedFrequency)
        }

        val refFreq = if (selectedTuning.value == "Custom Scala") {
            if (scalaRootPitch.value == "Custom Hz") {
                scalaCustomFreq.value
            } else {
                val noteOffsets = mapOf(
                    "C" to -9, "C♯" to -8, "D" to -7, "E♭" to -6, "E" to -5, "F" to -4,
                    "F♯" to -3, "G" to -2, "A♭" to -1, "A" to 0, "B♭" to 1, "B" to 2
                )
                val offset = noteOffsets[scalaRootPitch.value] ?: -9
                a4Reference.value * 2.0.pow(offset.toDouble() / 12.0).toFloat()
            }
        } else {
            a4Reference.value
        }

        val tuningResult = getActiveTuningSystem().getClosestNote(smoothedFrequency, refFreq)

        _tuningState.update {
            TuningState(
                isActive = true,
                closestNote = tuningResult.noteName,
                centsOff = tuningResult.centsOff,
                isInTune = kotlin.math.abs(tuningResult.centsOff) <= toleranceCents.value
            )
        }

        if (tuningResult.targetFrequency != currentTargetFreq) {
            currentTargetFreq = tuningResult.targetFrequency
            nativeBridge.setStrobeTargetFrequency(currentTargetFreq)
        }
    }

    /**
     * Updates the base A4 anchor used across all tuning algorithms.
     */
    fun updateA4(newA4: Float) {
        a4Reference.value = newA4
        currentTargetFreq = 0f
        prefs.edit { putFloat("A4_REF", newA4) }
    }

    /**
     * Updates the visual tuning tolerance range in cents.
     */
    fun updateTolerance(newTolerance: Float) {
        toleranceCents.value = newTolerance
        prefs.edit { putFloat("TOLERANCE", newTolerance) }
    }

    /**
     * Sets the active tuning system algorithm.
     */
    fun updateTuningSystem(systemName: String) {
        selectedTuning.value = systemName
        prefs.edit { putString("TUNING_SYSTEM", systemName) }
        currentTargetFreq = 0f
    }

    /**
     * Modifies the UI theme.
     */
    fun toggleTheme(isDark: Boolean) {
        isDarkTheme.value = isDark
        prefs.edit { putBoolean("DARK_THEME", isDark) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTuning()
    }
}