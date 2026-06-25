package com.buildrabbi.atv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ১. মেইন লেআউট তৈরি (Vertical LinearLayout)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            padding = 50
            setBackgroundColor(android.graphics.Color.parseColor("#121212")) // ডার্ক থিম
        }

        // ২. কী (Key) ইনপুট বক্স
        val keyEditText = EditText(this).apply {
            hint = "Enter Your Key"
            setHintTextColor(android.graphics.Color.GRAY)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(800, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 40)
            }
        }

        // ৩. এন্টার বাটন (Key চেক করার জন্য)
        val enterButton = Button(this).apply {
            text = "Enter"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        // ৪. রেজিস্টার বাটন (হোয়াটসঅ্যাপে যাওয়ার জন্য)
        val registerButton = Button(this).apply {
            text = "Register"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // লেআউটে উপাদানগুলো যুক্ত করা
        mainLayout.addView(keyEditText)
        mainLayout.addView(enterButton)
        mainLayout.addView(registerButton)
        setContentView(mainLayout)

        // ---- বাটন অ্যাকশন (Logic) ----

        // হোয়াটসঅ্যাপ রিডাইরেক্ট লজিক
        registerButton.setOnClickListener {
            val whatsappNumber = "+8801731410341"
            val url = "https://wa.me/$whatsappNumber?text=Hello,%20I%20want%20to%20buy%20a%20key%20for%20IPTV%20App."
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            startActivity(intent)
        }

        // ফায়ারবেস কী ভ্যালিডেশন লজিক
        enterButton.setOnClickListener {
            val inputKey = keyEditText.text.toString().trim()

            if (inputKey.isEmpty()) {
                Toast.makeText(this, "Please enter a key!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ফায়ারবেস ডাটাবেজে কী চেক করা
            val databaseRef = FirebaseDatabase.getInstance().getReference("keys").child(inputKey)
            
            databaseRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val status = snapshot.child("status").value.toString()
                    if (status == "active") {
                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                        
                        // সফল হলে ভিডিও প্লেয়ার স্ক্রিনে নিয়ে যাবে (এই স্ক্রিনটি আমরা পরের ধাপে বানাবো)
                        val intent = Intent(this, PlayerActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "This key is paused or expired!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Invalid Key! Please register.", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Database Error! Check Internet.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
