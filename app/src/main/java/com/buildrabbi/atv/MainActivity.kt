package com.buildrabbi.atv

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var keyInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var progressBar: ProgressBar
    private var adminWhatsApp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = FirebaseDatabase.getInstance().reference
        keyInput = findViewById(R.id.keyInput)
        loginBtn = findViewById(R.id.loginBtn)
        progressBar = findViewById(R.id.progressBar)

        database.child("settings").child("whatsapp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                adminWhatsApp = snapshot.getValue(String::class.java) ?: "Not Set"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        loginBtn.setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this@MainActivity, "Please enter your key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkKey(key)
        }
    }

    private fun checkKey(key: String) {
        progressBar.visibility = View.VISIBLE
        loginBtn.isEnabled = false

        database.child("keys").child(key).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE
                loginBtn.isEnabled = true

                if (snapshot.exists()) {
                    val status = snapshot.child("status").getValue(String::class.java) ?: "active"
                    val expiry = snapshot.child("expiry").getValue(String::class.java) ?: ""

                    if (status == "paused") {
                        showPopup("Account Paused ⚠️", "Your account is currently paused by admin.\n\nPlease contact via WhatsApp: " + adminWhatsApp)
                        return
                    }

                    if (expiry.isNotEmpty() && expiry != "No Limit") {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val expiryDate = sdf.parse(expiry)
                            val currentDate = Date()
                            if (expiryDate != null && currentDate.after(expiryDate)) {
                                showPopup("Subscription Expired ❌", "Your access expired on " + expiry + ".\n\nPlease renew via WhatsApp: " + adminWhatsApp)
                                return
                            }
                        } catch (e: Exception) {}
                    }

                    Toast.makeText(this@MainActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Invalid Key", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                loginBtn.isEnabled = true
            }
        })
    }

    private fun showPopup(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .show()
    }
}