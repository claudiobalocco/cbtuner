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

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Entry point for the application. Initializes the main view model and composes the user interface.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: TunerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TunerApp(viewModel)
        }
    }
}

/**
 * The root composable function managing the primary UI state, navigation, and dialogs.
 *
 * @param viewModel The state holder managing the audio engine and tuning mathematics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerApp(viewModel: TunerViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()

    val state by viewModel.tuningState.collectAsState()
    val a4Ref by viewModel.a4Reference.collectAsState()
    val tolerance by viewModel.toleranceCents.collectAsState()

    val context = LocalContext.current

    val scalaFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    var fileName = "custom.scl"
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }

                    context.contentResolver.openInputStream(it)?.use { inputStream: InputStream ->
                        if (inputStream.available() > 100 * 1024) {
                            Toast.makeText(context, "File too large. Must be under 100KB.", Toast.LENGTH_SHORT).show()
                            return@use
                        }

                        val content = inputStream.bufferedReader().use { reader -> reader.readText() }

                        val testParse = ScalaTuning.parse(content)
                        viewModel.loadCustomScala(content, "Custom Scala", fileName)
                        Toast.makeText(context, "Loaded: ${testParse.name}", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, "Invalid Scala file", Toast.LENGTH_SHORT).show()
                    viewModel.updateTuningSystem("12TET (standard)")
                }
            }
        }
    )

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isStrobeView by remember { mutableStateOf(false) }
    var a4Input by remember { mutableStateOf(String.format(Locale.US, "%.2f", a4Ref)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasMicPermission = isGranted }
    )

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasMicPermission) {
        LaunchedEffect(Unit) { viewModel.startTuning() }
    }

    MaterialTheme(colorScheme = colorScheme) {
        if (showSettingsDialog) {
            val currentTuning by viewModel.selectedTuning.collectAsState()
            val isScala = currentTuning == "Custom Scala"

            val scalaFileName by viewModel.scalaFileName.collectAsState()
            val rootPitch by viewModel.scalaRootPitch.collectAsState()
            val customFreq by viewModel.scalaCustomFreq.collectAsState()

            val tuningOptions = listOf("12TET (standard)", "24TET (quarter tone)", "31TET (Huygens-Fokker)", "Load Custom Scala...")
            val rootOptions = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B", "Custom Hz")

            var tuningExpanded by remember { mutableStateOf(false) }
            var rootExpanded by remember { mutableStateOf(false) }

            var customFreqInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", customFreq)) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        ExposedDropdownMenuBox(
                            expanded = tuningExpanded,
                            onExpandedChange = { tuningExpanded = !tuningExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (isScala) "Custom Scala" else currentTuning,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tuning System") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tuningExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = tuningExpanded,
                                onDismissRequest = { tuningExpanded = false }
                            ) {
                                tuningOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            if (option == "Load Custom Scala...") {
                                                scalaFileLauncher.launch(arrayOf("*/*"))
                                            } else {
                                                viewModel.updateTuningSystem(option)
                                            }
                                            tuningExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = a4Input,
                            onValueChange = { input -> a4Input = input.filter { it.isDigit() || it == '.' } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            label = { Text("Standard A4 Reference (Hz)") },
                            enabled = !isScala,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        OutlinedTextField(
                            value = if (isScala) scalaFileName else "No file loaded",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Active Scala File") },
                            enabled = isScala,
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExposedDropdownMenuBox(
                            expanded = rootExpanded && isScala,
                            onExpandedChange = { if (isScala) rootExpanded = !rootExpanded }
                        ) {
                            OutlinedTextField(
                                value = rootPitch,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Scala Root Pitch (1/1)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rootExpanded) },
                                enabled = isScala,
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, isScala)
                            )
                            ExposedDropdownMenu(
                                expanded = rootExpanded,
                                onDismissRequest = { rootExpanded = false }
                            ) {
                                rootOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            viewModel.updateScalaRootPitch(option)
                                            rootExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (isScala && rootPitch == "Custom Hz") {
                            OutlinedTextField(
                                value = customFreqInput,
                                onValueChange = { input -> customFreqInput = input.filter { it.isDigit() || it == '.' } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                label = { Text("Custom Root Frequency (Hz)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text("Tolerance (Cents)", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            (1..5).forEach { value ->
                                FilterChip(
                                    selected = tolerance.toInt() == value,
                                    onClick = { viewModel.updateTolerance(value.toFloat()) },
                                    label = { Text("±$value") }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        a4Input.toFloatOrNull()?.let {
                            val formatted = String.format(Locale.US, "%.2f", it).toFloat()
                            viewModel.updateA4(formatted)
                        }

                        customFreqInput.toFloatOrNull()?.let {
                            val formatted = String.format(Locale.US, "%.2f", it).toFloat()
                            viewModel.updateScalaCustomFreq(formatted)
                        }
                        showSettingsDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("How to Use") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val githubLink = buildAnnotatedString {
                            append("CBTuner is free and open source. Visit ")

                            withLink(LinkAnnotation.Url("https://github.com/claudiobalocco/cbtuner")) {
                                withStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                                    append("GitHub")
                                }
                            }
                            append(" to show your support and view the source code.")
                        }

                        Text(text = githubLink)

                        val inlineIconMap = mapOf(
                            "tuningIcon" to InlineTextContent(
                                Placeholder(
                                    width = 18.sp,
                                    height = 18.sp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Tuning Icon",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )

                        val instructionText = buildAnnotatedString {
                            append("Press ")
                            appendInlineContent("tuningIcon", "[icon]")
                            append(" to toggle between the standard and strobe tuner.")
                        }

                        Text(
                            text = instructionText,
                            inlineContent = inlineIconMap
                        )

                        Text("Adjust your instrument tuning in the standard tuner first, or the wheel rotation in the strobe tuner will be too high. You can slow down the wheel speed by 2, 4, or 8 for clarity.")

                        val inlineIconMap2 = mapOf(
                            "settingsIcon" to InlineTextContent(
                                Placeholder(
                                    width = 18.sp,
                                    height = 18.sp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings Icon",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )

                        val instructionText2 = buildAnnotatedString {
                            append("Use the settings")
                            appendInlineContent("settingsIcon", "[icon]")
                            append(" to select the A4 reference pitch. The modern standard is 440 Hz.")
                        }

                        Text(
                            text = instructionText2,
                            inlineContent = inlineIconMap2
                        )

                        Text("You can select your preferred tuning system among 12TET, 24TET, and 31TET. "+
                                "If you don't what this means, you may want to leave the default 12TET, "+
                                "the conventional tuning system in western music.")

                        Text("You can also import a custom set of pitches using a SCALA file. " +
                                "Once you have imported the desired SCALA file you must set the pitch of the root note, "+
                                "for example 261.63 Hz for a C4 root.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CB Tuner") },
                    actions = {
                        IconButton(onClick = { viewModel.toggleTheme(!isDarkTheme) }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                        IconButton(onClick = { isStrobeView = !isStrobeView }) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Toggle Strobe View",
                                tint = if (isStrobeView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Information")
                        }
                        IconButton(onClick = {
                            a4Input = String.format(Locale.US, "%.2f", a4Ref)
                            showSettingsDialog = true
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = MaterialTheme.colorScheme.background) {
                if (!hasMicPermission) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Microphone permission is required to tune your instrument.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    return@Surface
                }

                if (isStrobeView) {
                    val strobeState by viewModel.strobeState.collectAsState()
                    val phaseDegrees = (strobeState.continuousPhase * (180f / Math.PI.toFloat()))
                    val visualAlpha = (strobeState.magnitude * 50f).coerceIn(0f, 1f)
                    val speedFactor by viewModel.strobeSpeedFactor.collectAsState()
                    val speedOptions = listOf(1, 2, 4, 8)

                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (state.isActive) state.closestNote else "-",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = if (strobeState.targetFrequency > 0)
                                "Target: ${strobeState.targetFrequency.roundToInt()} Hz | ${String.format(Locale.US, "%.1f", strobeState.strobeCents)} ¢"
                            else "Awaiting pitch...",
                            color = if (kotlin.math.abs(strobeState.strobeCents) < tolerance.toInt()) Color.Green else Color.Gray,
                            modifier = Modifier.padding(bottom = 40.dp)
                        )

                        Box(contentAlignment = Alignment.Center) {
                            val strobeColor = MaterialTheme.colorScheme.primary

                            Canvas(
                                modifier = Modifier
                                    .size(260.dp)
                                    .rotate(phaseDegrees)
                            ) {
                                val numDots = 12
                                val dotRadius = 16.dp.toPx()
                                val orbitRadius = size.minDimension / 2 - 20.dp.toPx()

                                for (i in 0 until numDots) {
                                    val angle = i * (2.0 * Math.PI / numDots)
                                    val cx = center.x + (orbitRadius * kotlin.math.cos(angle)).toFloat()
                                    val cy = center.y + (orbitRadius * kotlin.math.sin(angle)).toFloat()

                                    val dotColor = if (i == 0) Color.Red else strobeColor

                                    drawCircle(
                                        color = dotColor.copy(alpha = visualAlpha),
                                        radius = dotRadius,
                                        center = Offset(cx, cy)
                                    )
                                }
                            }

                            Canvas(modifier = Modifier.size(260.dp)) {
                                drawCircle(
                                    color = Color.DarkGray,
                                    radius = size.minDimension / 2 - 40.dp.toPx(),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = Color.Red,
                                    start = center.copy(y = center.y - (size.minDimension / 2)),
                                    end = center.copy(y = center.y - (size.minDimension / 2) + 20.dp.toPx()),
                                    strokeWidth = 6.dp.toPx()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        Text("Rotation Speed", color = Color.Gray, fontSize = 14.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                        ) {
                            speedOptions.forEach { factor ->
                                FilterChip(
                                    selected = speedFactor == factor,
                                    onClick = { viewModel.strobeSpeedFactor.value = factor },
                                    label = { Text("1/${factor}x") }
                                )
                            }
                        }

                        Text("Spinning Clockwise = Sharp", color = Color.Gray)
                        Text("Spinning Counter-Clockwise = Flat", color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val circleColor =
                            if (state.isInTune && state.isActive) Color.Green.copy(alpha = 0.5f) else Color.Transparent

                        Box(
                            modifier = Modifier.size(200.dp)
                                .background(color = circleColor, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.isActive) state.closestNote else "-",
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        Text(
                            text = if (state.isActive) "${state.centsOff.toInt()} cents" else "Listening...",
                            fontSize = 24.sp,
                            color = if (state.isInTune) Color.Green else Color.Gray
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val progress = if (state.isActive) ((state.centsOff + 50f) / 100f).coerceIn(
                            0f,
                            1f
                        ) else 0.5f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            color = if (state.isInTune) Color.Green else Color.Red,
                            trackColor = Color.LightGray
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Flat")
                            Text("In tune")
                            Text("Sharp")
                        }
                    }
                }
            }
        }
    }
}