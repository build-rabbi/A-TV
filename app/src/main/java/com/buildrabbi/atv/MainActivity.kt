package com.buildrabbi.atv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etKey = findViewById<EditText>(R.id.etKey)
        val btnEnter = findViewById<Button>(R.id.btnEnter)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnEnter.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter your key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            validateKey(key)
        }

        btnRegister.setOnClickListener {
            val url = "https://wa.me/8801731410341?text=Hello,%20I%20want%20to%20buy%20a%20key%20for%20IPTV%20App."
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateKey(key: String) {
        val ref = FirebaseDatabase.getInstance().getReference("keys/$key")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(this@MainActivity, "Invalid key. Please check again.", Toast.LENGTH_LONG).show()
                    return
                }
                when (snapshot.child("status").getValue(String::class.java)) {
                    "active" -> {
                        Toast.makeText(this@MainActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
                    }
                    "paused" -> Toast.makeText(
                        this@MainActivity,
                        "Your key is paused. Contact support.",
                        Toast.LENGTH_LONG
                    ).show()
                    else -> Toast.makeText(
                        this@MainActivity,
                        "Key is not active.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Connection error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }
}
