package com.buildrabbi.atv

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var keyInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var registerBtn: Button
    private lateinit var database: DatabaseReference
    private var adminWhatsApp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keyInput = findViewById(R.id.etKey)
        loginBtn = findViewById(R.id.btnEnter)
        registerBtn = findViewById(R.id.btnRegister)
        database = FirebaseDatabase.getInstance().reference

        // Auto login if key saved
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("saved_key", null)
        if (savedKey != null) {
            keyInput.setText(savedKey)
            checkKey(savedKey)
        }

        database.child("settings").child("whatsapp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                adminWhatsApp = snapshot.getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        keyInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val key = keyInput.text.toString().trim()
                if (key.isNotEmpty()) checkKey(key)
                true
            } else false
        }

        loginBtn.setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter your access key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkKey(key)
        }

        registerBtn.setOnClickListener {
            showPopup("Buy Access Key", "Contact admin on WhatsApp:\n\n$adminWhatsApp")
        }
    }

    private fun checkKey(key: String) {
        loginBtn.isEnabled = false
        loginBtn.text = "Checking..."

        database.child("keys").child(key).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loginBtn.isEnabled = true
                loginBtn.text = "ENTER"

                if (!snapshot.exists()) {
                    getSharedPreferences("atv_prefs", MODE_PRIVATE).edit().remove("saved_key").apply()
                    showPopup("Invalid Key ❌", "This key is not valid.\n\nContact admin:\n$adminWhatsApp")
                    return
                }

                val status = snapshot.child("status").getValue(String::class.java) ?: "active"
                val expiry = snapshot.child("expiry").getValue(String::class.java) ?: "No Limit"
                val userName = snapshot.child("user").getValue(String::class.java) ?: ""
                val deviceId = snapshot.child("deviceId").getValue(String::class.java) ?: ""
                val myDeviceId = android.provider.Settings.Secure.getString(
                    contentResolver, android.provider.Settings.Secure.ANDROID_ID)

                if (status == "paused") {
                    getSharedPreferences("atv_prefs", MODE_PRIVATE).edit().remove("saved_key").apply()
                    showRenewPopup("Account Paused ⚠️", "Your account is paused.\n\nContact via WhatsApp:")
                    return
                }

                if (expiry != "No Limit" && expiry.isNotEmpty()) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val expiryDate = sdf.parse(expiry)
                        if (expiryDate != null && Date().after(expiryDate)) {
                            getSharedPreferences("atv_prefs", MODE_PRIVATE).edit().remove("saved_key").apply()
                            showRenewPopup("Subscription Expired ❌", "Expired on $expiry.\n\nRenew via WhatsApp:")
                            return
                        }
                    } catch (e: Exception) {}
                }

                if (deviceId.isEmpty()) {
                    database.child("keys").child(key).child("deviceId").setValue(myDeviceId)
                } else if (deviceId != myDeviceId) {
                    showPopup("Device Locked 🔒", "This key is active on another device.\n\nContact admin:\n$adminWhatsApp")
                    return
                }

                // Save key + user info for auto login and profile
                getSharedPreferences("atv_prefs", MODE_PRIVATE).edit()
                    .putString("saved_key", key)
                    .putString("user_name", userName)
                    .putString("expiry", expiry)
                    .apply()

                val greeting = if (userName.isNotEmpty()) "Welcome, $userName!" else "Login Successful"
                Toast.makeText(this@MainActivity, greeting, Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
                finish()
            }

            override fun onCancelled(error: DatabaseError) {
                loginBtn.isEnabled = true
                loginBtn.text = "ENTER"
                Toast.makeText(this@MainActivity, "Connection error. Try again.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showPopup(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(message)
            .setCancelable(false).setPositiveButton("OK", null).show()
    }

    private fun showRenewPopup(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("$message\n\n📱 $adminWhatsApp")
            .setCancelable(false)
            .setPositiveButton("WhatsApp") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://wa.me/${adminWhatsApp.replace("+","").replace(" ","")}")))
                } catch (e: Exception) {}
            }
            .setNegativeButton("Close", null).show()
    }
}
