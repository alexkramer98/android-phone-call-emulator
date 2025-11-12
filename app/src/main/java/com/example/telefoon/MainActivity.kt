package com.example.telefoon

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts // Import this
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat // Import this

class MainActivity : ComponentActivity() {

    // Modern way to handle permission requests
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                openTelecomSettings()
            } else {
                Toast.makeText(this, "Microphone permission is required for this app to function", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUi()
        registerPhoneAccount()
    }

    private fun setupUi() {
        // Your setupUi code remains the same...
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Phone Call Emulator",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Button(
                            onClick = { enable() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Enable Calling Account")
                        }
                    }
                }
            }
        }
    }

    private fun registerPhoneAccount() {
        // Your registerPhoneAccount code remains the same...
        try {
            val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
            val phoneAccountHandle = PhoneAccountHandle(
                ComponentName(this, MyConnectionService::class.java),
                "AndroidPhoneCallEmulator"
            )
            val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "Android Phone Call Emulator")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build()

            telecomManager.registerPhoneAccount(phoneAccount)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // This function is now correctly implemented
    private fun enable() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted, proceed to settings
                openTelecomSettings()
            }
            else -> {
                // Directly ask for the permission
                requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun openTelecomSettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.server.telecom",
                    "com.android.server.telecom.settings.EnableAccountPreferenceActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}