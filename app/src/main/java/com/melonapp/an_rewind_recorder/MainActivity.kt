package com.melonapp.an_rewind_recorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.melonapp.an_rewind_recorder.ui.BacktrackScreen
import com.melonapp.an_rewind_recorder.ui.BacktrackViewModel
import com.melonapp.an_rewind_recorder.ui.theme.AnrewindrecorderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BacktrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnrewindrecorderTheme {
                var hasPermissions by remember {
                    mutableStateOf(checkPermissionsGranted())
                }

                val permissionsToRequest = remember {
                    val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    list.toTypedArray()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionsMap ->
                    val recordAudioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] == true
                    hasPermissions = recordAudioGranted
                }

                LaunchedEffect(Unit) {
                    if (!hasPermissions) {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BacktrackScreen(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            permissionLauncher.launch(permissionsToRequest)
                        },
                        hasPermissions = hasPermissions
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationStatus()
        viewModel.refreshRecordings()
    }

    private fun checkPermissionsGranted(): Boolean {
        val audioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return audioGranted && notificationGranted
    }
}