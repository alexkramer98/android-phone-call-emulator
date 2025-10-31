package com.example.telefoon

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.net.toUri

class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
            val handle = android.telecom.PhoneAccountHandle(
                ComponentName(this, MyConnectionService::class.java),
                "AndroidPhoneCallEmulator"
            )

            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    "tel:69696969".toUri()
                )
            }

            telecomManager.addNewIncomingCall(handle, extras)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
