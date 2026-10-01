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

import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Represents the result of analyzing an input frequency against a specific tuning system.
 *
 * @property noteName The human-readable or mathematical name of the closest note.
 * @property targetFrequency The exact theoretical frequency of that closest note.
 * @property centsOff The logarithmic distance in cents between the input and the target frequency.
 */
data class TuningResult(
    val noteName: String,
    val targetFrequency: Float,
    val centsOff: Float
)

/**
 * Defines a general mathematical interface for parsing arbitrary tuning systems.
 */
interface TuningSystem {
    /** The name of the tuning system. */
    val name: String

    /**
     * Calculates the closest note in this tuning system for a given input frequency.
     *
     * @param frequency The detected fundamental pitch in Hz.
     * @param referenceFrequency The mathematical anchor pitch (e.g., A4 or 1/1 Root).
     * @return A TuningResult containing the nearest note and pitch deviation.
     */
    fun getClosestNote(frequency: Float, referenceFrequency: Float): TuningResult
}

/**
 * Calculates note mappings for generalized Equal Temperament structures (n-TET).
 */
class EqualTemperament(
    val divisions: Int,
    val stepsFromCToA: Int,
    override val name: String,
    private val customNoteNames: List<String>? = null
) : TuningSystem {

    override fun getClosestNote(frequency: Float, referenceFrequency: Float): TuningResult {
        val c4Frequency = referenceFrequency * 2.0.pow(-stepsFromCToA.toDouble() / divisions).toFloat()

        val stepsFromC4 = (divisions * log2(frequency / c4Frequency)).roundToInt()
        val targetFreq = c4Frequency * 2.0.pow(stepsFromC4.toDouble() / divisions).toFloat()
        val centsOff = 1200f * log2(frequency / targetFreq)

        val octaveOffset = floor(stepsFromC4.toDouble() / divisions).toInt()
        val octave = 4 + octaveOffset
        val degree = Math.floorMod(stepsFromC4, divisions)

        val noteName = if (customNoteNames != null && customNoteNames.size == divisions) {
            "${customNoteNames[degree]}$octave"
        } else {
            "$degree|$octave"
        }

        return TuningResult(noteName, targetFreq, centsOff)
    }
}

/**
 * Contains pre-configured constants for standard and historical Equal Temperaments.
 */
object TuningPresets {
    val standard12TET = EqualTemperament(
        divisions = 12,
        stepsFromCToA = 9,
        name = "12TET (standard)",
        customNoteNames = listOf(
            "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"
        )
    )

    val quarterTone24TET = EqualTemperament(
        divisions = 24,
        stepsFromCToA = 18,
        name = "24TET (quarter tone)",
        customNoteNames = listOf(
            "C", "C‡", "C♯", "C♯‡",
            "D", "D‡", "D♯", "D♯‡",
            "E", "E‡",
            "F", "F‡", "F♯", "F♯‡",
            "G", "G‡", "G♯", "G♯‡",
            "A", "A‡", "A♯", "A♯‡",
            "B", "B‡"
        )
    )

    val huygens31TET = EqualTemperament(
        divisions = 31,
        stepsFromCToA = 23,
        name = "31TET (Huygens-Fokker)",
        customNoteNames = listOf(
            "C", "C‡", "C♯", "D♭", "D𝄲",
            "D", "D‡", "D♯", "E♭", "E𝄲",
            "E", "E‡", "F𝄲",
            "F", "F‡", "F♯", "G♭", "G𝄲",
            "G", "G‡", "G♯", "A♭", "A𝄲",
            "A", "A‡", "A♯", "B♭", "B𝄲",
            "B", "B‡", "C𝄲"
        )
    )

    val all = listOf(standard12TET, quarterTone24TET, huygens31TET)
}

/**
 * Handles tuning logic for arbitrary scale files adhering to the Scala (.scl) specification.
 */
class ScalaTuning(
    override val name: String,
    private val ratiosToRoot: List<Double>
) : TuningSystem {

    override fun getClosestNote(frequency: Float, referenceFrequency: Float): TuningResult {
        if (ratiosToRoot.isEmpty()) return TuningResult("Invalid Scala", referenceFrequency, 0f)

        val rootFreq = referenceFrequency

        val period = ratiosToRoot.last()
        val periodLog = kotlin.math.log2(period)

        val freqRatio = frequency / rootFreq
        val periodCount = kotlin.math.floor(kotlin.math.log2(freqRatio.toDouble()) / periodLog).toInt()

        val normalizedRatio = freqRatio / period.pow(periodCount)

        var closestIndex = 0
        var minLogDistance = Double.MAX_VALUE

        for (i in ratiosToRoot.indices) {
            val logDistance = kotlin.math.abs(kotlin.math.log2(normalizedRatio) - kotlin.math.log2(ratiosToRoot[i]))
            if (logDistance < minLogDistance) {
                minLogDistance = logDistance
                closestIndex = i
            }
        }

        val exactTargetFreq = (rootFreq * period.pow(periodCount) * ratiosToRoot[closestIndex]).toFloat()
        val centsOff = 1200f * kotlin.math.log2(frequency / exactTargetFreq)

        val octave = 4 + periodCount
        val noteName = "$closestIndex|$octave"

        return TuningResult(noteName, exactTargetFreq, centsOff)
    }

    companion object {
        /**
         * Parses a standard Scala text file format into a valid ScalaTuning object.
         *
         * @param sclContent The raw string contents of the Scala file.
         * @return A constructed ScalaTuning instance representing the parsed scale.
         * @throws IllegalArgumentException if the file syntax is invalid or no repeating period is found.
         */
        fun parse(sclContent: String): ScalaTuning {
            val lines = sclContent.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("!") }

            if (lines.size < 2) throw IllegalArgumentException("Invalid Scala file: Missing header")

            val name = lines[0]
            val noteCount = lines[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid note count")

            val ratios = mutableListOf(1.0)

            for (i in 0 until noteCount) {
                if (i + 2 >= lines.size) break

                val line = lines[i + 2].substringBefore("!").trim()

                when {
                    line.contains(".") -> {
                        val cents = line.toDoubleOrNull() ?: continue
                        ratios.add(2.0.pow(cents / 1200.0))
                    }
                    line.contains("/") -> {
                        val parts = line.split("/")
                        val num = parts.getOrNull(0)?.toDoubleOrNull() ?: continue
                        val den = parts.getOrNull(1)?.toDoubleOrNull() ?: continue
                        if (den > 0.0 && num > 0.0) ratios.add(num / den)
                    }
                    else -> {
                        val num = line.toDoubleOrNull() ?: continue
                        if (num > 0.0) ratios.add(num)
                    }
                }
            }

            if (ratios.size < 2 || ratios.last() <= 1.0) {
                throw IllegalArgumentException("Invalid Scala file: No valid repeating period found")
            }

            return ScalaTuning(name, ratios)
        }
    }
}