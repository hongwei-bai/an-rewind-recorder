package com.melonapp.an_rewind_recorder.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melonapp.an_rewind_recorder.audio.AudioConstants
import com.melonapp.an_rewind_recorder.util.BatteryOptimizationHelper
import com.melonapp.an_rewind_recorder.util.PlaybackState
import com.melonapp.an_rewind_recorder.util.RecordingItem
import java.util.Date
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktrackScreen(
    viewModel: BacktrackViewModel,
    onRequestPermissions: () -> Unit,
    hasPermissions: Boolean
) {
    val context = LocalContext.current
    val isListening by viewModel.isListening.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val bufferedSeconds by viewModel.bufferedSeconds.collectAsState()
    val memoryUsageMb by viewModel.memoryUsageMb.collectAsState()
    val amplitude by viewModel.currentAmplitude.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isIgnoringBatteryOpt by viewModel.isIgnoringBatteryOptimizations.collectAsState()

    var showBatteryBanner by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Backtrack",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Retroactive Audio Buffer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    StatusPill(isListening = isListening, isPaused = isPaused)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Battery Optimization Banner
            if (!isIgnoringBatteryOpt && showBatteryBanner) {
                item {
                    BatteryExemptionCard(
                        onExemptClick = {
                            val intent = BatteryOptimizationHelper.createBatteryOptimizationIntent(context)
                            context.startActivity(intent)
                        },
                        onDismiss = { showBatteryBanner = false }
                    )
                }
            }

            // Status Dashboard with Waveform & Buffer Metrics
            item {
                StatusDashboardCard(
                    isListening = isListening,
                    isPaused = isPaused,
                    amplitude = amplitude,
                    bufferedSeconds = bufferedSeconds,
                    memoryUsageMb = memoryUsageMb
                )
            }

            // Primary Control Actions
            item {
                ControlPanelCard(
                    isListening = isListening,
                    isPaused = isPaused,
                    bufferedSeconds = bufferedSeconds,
                    isExporting = isExporting,
                    hasPermissions = hasPermissions,
                    onToggleListening = {
                        if (!hasPermissions) {
                            onRequestPermissions()
                        } else {
                            if (isListening) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        }
                    },
                    onSave5Min = { viewModel.saveLast5Minutes() },
                    onSave10Min = { viewModel.saveLast10Minutes() }
                )
            }

            // Recordings Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Captures",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "${recordings.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Recordings List or Empty State
            if (recordings.isEmpty()) {
                item {
                    EmptyRecordingsCard()
                }
            } else {
                items(recordings, key = { it.file.absolutePath }) { recording ->
                    RecordingItemCard(
                        recording = recording,
                        playbackState = playbackState,
                        onPlayClick = { viewModel.playRecording(recording.file) },
                        onPauseClick = { viewModel.pausePlayback() },
                        onSeek = { positionMs -> viewModel.seekPlayback(positionMs) },
                        onShareClick = {
                            val shareIntent = viewModel.createShareIntent(recording.file)
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio Recording"))
                        },
                        onDeleteClick = { viewModel.deleteRecording(recording) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(isListening: Boolean, isPaused: Boolean) {
    val (bgColor, dotColor, text) = when {
        isListening && isPaused -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFF57C00),
            "PAUSED"
        )
        isListening -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "LISTENING"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline,
            "IDLE"
        )
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
        }
    }
}

@Composable
fun StatusDashboardCard(
    isListening: Boolean,
    isPaused: Boolean,
    amplitude: Float,
    bufferedSeconds: Int,
    memoryUsageMb: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Waveform Visualizer
            AudioVisualizer(
                isListening = isListening,
                isPaused = isPaused,
                amplitude = amplitude
            )

            // Progress Bar (0 to 600s / 10 min)
            val maxSeconds = AudioConstants.MAX_BUFFER_DURATION_SECONDS
            val progress = (bufferedSeconds.toFloat() / maxSeconds).coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rolling RAM Buffer",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val minutes = bufferedSeconds / 60
                    val seconds = bufferedSeconds % 60
                    Text(
                        text = String.format("%02d:%02d / 10:00", minutes, seconds),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            // Specs and Footprint Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RAM Footprint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f MB / 18.3 MB", memoryUsageMb),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp)
                    )
                ) {
                    Text(
                        text = "16 kHz • 16-bit • Mono • 0% Disk Wear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioVisualizer(
    isListening: Boolean,
    isPaused: Boolean,
    amplitude: Float
) {
    val barCount = 28
    val activeColor = if (isPaused) Color(0xFFF57C00) else MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until barCount) {
                val factor = (sin(i * 0.45) * 0.5 + 0.5).toFloat()
                val targetHeightFraction = if (isListening && !isPaused) {
                    (0.12f + amplitude * factor * 0.88f).coerceIn(0.12f, 1f)
                } else if (isListening && isPaused) {
                    0.25f
                } else {
                    0.1f
                }

                val animatedHeight by animateFloatAsState(
                    targetValue = targetHeightFraction,
                    animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
                    label = "bar_height_$i"
                )

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxWidth(fraction = 1f)
                        .height((40 * animatedHeight).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isListening) activeColor else idleColor)
                )
            }
        }
    }
}

@Composable
fun ControlPanelCard(
    isListening: Boolean,
    isPaused: Boolean,
    bufferedSeconds: Int,
    isExporting: Boolean,
    hasPermissions: Boolean,
    onToggleListening: () -> Unit,
    onSave5Min: () -> Unit,
    onSave10Min: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Start/Stop Listening Button
            Button(
                onClick = onToggleListening,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !hasPermissions -> "Grant Mic Permission"
                        isListening -> "Stop Listening"
                        else -> "Start Listening (Rolling Buffer)"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

            Text(
                text = "Retroactive Capture",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Direct Save 5m & Save 10m Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val canSave = isListening && bufferedSeconds > 0 && !isExporting
                val fiveMinLabel = if (bufferedSeconds < 300 && bufferedSeconds > 0) {
                    val m = bufferedSeconds / 60
                    val s = bufferedSeconds % 60
                    String.format("Save Available (%02d:%02d)", m, s)
                } else {
                    "Save Last 5 Min"
                }

                val tenMinLabel = if (bufferedSeconds < 600 && bufferedSeconds > 0) {
                    val m = bufferedSeconds / 60
                    val s = bufferedSeconds % 60
                    String.format("Save Available (%02d:%02d)", m, s)
                } else {
                    "Save Full (10 Min)"
                }

                FilledTonalButton(
                    onClick = onSave5Min,
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = fiveMinLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onSave10Min,
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tenMinLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryExemptionCard(
    onExemptClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Battery Exemption",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "To keep listening continuously with screen locked, disable battery optimization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onExemptClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Exempt App", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(text = "Dismiss", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyRecordingsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No Retroactive Captures Yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Start listening. When an epiphany, discussion, or instruction happens, tap 'Save Last 5 Min' to capture what already occurred.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun RecordingItemCard(
    recording: RecordingItem,
    playbackState: PlaybackState,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isCurrentFile = playbackState.currentFile?.absolutePath == recording.file.absolutePath
    val isPlaying = isCurrentFile && playbackState.isPlaying
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentFile) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, date, size, duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val dateFormatted = DateFormat.format("MMM dd, yyyy • hh:mm a", Date(recording.lastModified)).toString()
                    Text(
                        text = "$dateFormatted • ${recording.formattedDuration} • ${recording.formattedSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Player Controls & Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) onPauseClick() else onPlayClick()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val durationMs = if (isCurrentFile && playbackState.durationMs > 0) {
                        playbackState.durationMs
                    } else {
                        (recording.durationSeconds * 1000).toInt()
                    }

                    val currentPosMs = if (isCurrentFile) playbackState.currentPositionMs else 0

                    Slider(
                        value = if (durationMs > 0) currentPosMs.toFloat() / durationMs else 0f,
                        onValueChange = { fraction ->
                            if (isCurrentFile) {
                                onSeek((fraction * durationMs).toInt())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentSeconds = currentPosMs / 1000
                        val currentFormatted = String.format("%02d:%02d", currentSeconds / 60, currentSeconds % 60)
                        Text(
                            text = currentFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = recording.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "Delete Recording?") },
            text = { Text(text = "Are you sure you want to permanently delete \"${recording.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
