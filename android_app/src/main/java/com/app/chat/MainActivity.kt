package com.app.chat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.app.chat.ble.BleMeshManager
import com.app.chat.bridge.PythonCoreBridge
import com.app.chat.model.Screen
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.TextPrimary
import com.app.chat.ui.screens.CreateGroupScreen
import com.app.chat.ui.screens.DirectChatScreen
import com.app.chat.ui.screens.GroupChatScreen
import com.app.chat.ui.screens.HomeScreen
import com.app.chat.ui.screens.MeshGraphScreen
import com.app.chat.ui.screens.MnemonicDisplayScreen
import com.app.chat.ui.screens.NearbyRadarScreen
import com.app.chat.ui.screens.OnboardingScreen
import com.app.chat.ui.screens.PermissionScreen
import com.app.chat.ui.screens.ProfileScreen
import com.app.chat.ui.screens.SettingsScreen
import com.app.chat.ui.screens.SplashScreen
import com.app.chat.ui.screens.StorageScreen
import com.app.chat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PythonCoreBridge.initialize(applicationContext)
        val bleManager = BleMeshManager.getInstance(applicationContext)
        viewModel.setBleManager(bleManager)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BackgroundDark,
                    surface = SurfaceDark,
                    primary = PrimaryBlue,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                    onPrimary = Color.White
                )
            ) {
                AppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val permissions = remember { requiredBlePermissions() }
    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val isNowEnabled = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        viewModel.isBluetoothEnabled = isNowEnabled
        if (isNowEnabled) {
            val ble = BleMeshManager.getInstance(context)
            if (ble.initialize()) {
                ble.startAdvertising(viewModel.userProfile.displayName)
                ble.startScanning()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onPromptEnableBluetooth = {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        viewModel.checkBluetoothStatus()
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            val ble = BleMeshManager.getInstance(context)
            if (ble.initialize()) {
                viewModel.isBluetoothEnabled = true
                ble.startAdvertising(viewModel.userProfile.displayName)
                ble.startScanning()
            } else {
                viewModel.checkBluetoothStatus()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = permissions.all { results[it] == true }
        viewModel.isBlePermissionGranted = hasPermissions
        if (hasPermissions) {
            val ble = BleMeshManager.getInstance(context)
            if (ble.initialize()) {
                viewModel.isBluetoothEnabled = true
                ble.startAdvertising(viewModel.userProfile.displayName)
                ble.startScanning()
            } else {
                viewModel.requestEnableBluetooth()
            }
        }
        viewModel.navigateTo(Screen.Home)
    }

    AppNavigationHost(
        viewModel = viewModel,
        hasPermissions = hasPermissions,
        onRequestPermissions = {
            if (!hasPermissions) {
                launcher.launch(permissions)
            } else {
                viewModel.requestEnableBluetooth()
                viewModel.navigateTo(Screen.Home)
            }
        },
        onSkipPermissions = { viewModel.navigateTo(Screen.Home) }
    )
}

@Composable
fun AppNavigationHost(
    viewModel: ChatViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onSkipPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = viewModel.currentScreen != Screen.Home && viewModel.currentScreen != Screen.Splash) {
        viewModel.handleBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (viewModel.currentScreen) {
            Screen.Splash -> {
                SplashScreen(
                    onContinue = { viewModel.navigateTo(Screen.Onboarding) },
                    onImportPhrase = { viewModel.navigateTo(Screen.MnemonicDisplay) }
                )
            }
            Screen.Onboarding -> {
                OnboardingScreen(
                    onNext = {
                        if (hasPermissions) {
                            viewModel.navigateTo(Screen.Home)
                        } else {
                            viewModel.navigateTo(Screen.Permissions)
                        }
                    },
                    onSkip = { viewModel.navigateTo(Screen.Home) },
                    onBack = { viewModel.handleBack() }
                )
            }
            Screen.Permissions -> {
                PermissionScreen(
                    onAllow = onRequestPermissions,
                    onSkip = onSkipPermissions,
                    onBack = { viewModel.handleBack() }
                )
            }
            Screen.MnemonicDisplay -> {
                MnemonicDisplayScreen(
                    words = viewModel.mnemonicWords,
                    onSaved = { viewModel.navigateTo(Screen.Home) },
                    onBack = { viewModel.handleBack() }
                )
            }
            Screen.Home -> {
                HomeScreen(viewModel = viewModel)
            }
            Screen.DirectChat -> {
                DirectChatScreen(viewModel = viewModel)
            }
            Screen.GroupChat -> {
                GroupChatScreen(viewModel = viewModel)
            }
            Screen.Nearby -> {
                NearbyRadarScreen(viewModel = viewModel)
            }
            Screen.CreateGroup -> {
                CreateGroupScreen(viewModel = viewModel)
            }
            Screen.MeshGraph -> {
                MeshGraphScreen(viewModel = viewModel)
            }
            Screen.Settings -> {
                SettingsScreen(viewModel = viewModel)
            }
            Screen.Storage -> {
                StorageScreen(viewModel = viewModel)
            }
            Screen.Profile -> {
                ProfileScreen(viewModel = viewModel)
            }
        }
    }
}

private fun requiredBlePermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list += Manifest.permission.BLUETOOTH_SCAN
        list += Manifest.permission.BLUETOOTH_ADVERTISE
        list += Manifest.permission.BLUETOOTH_CONNECT
    }
    return list.toTypedArray()
}
