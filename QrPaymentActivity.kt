package com.buildrabbi.atv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class QrPaymentActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private var keyListener: ValueEventListener? = null
    private lateinit var statusText: TextView

    companion object {
        const val BASE_URL = "https://build-rabbi.github.io/A-TV/bkash-payment.html"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_payment)

        val qrImage = findViewById<ImageView>(R.id.qrImage)
        val btnBack = findViewById<Button>(R.id.btnBack)
        statusText = findViewById(R.id.statusText)
        database = FirebaseDatabase.getInstance().reference

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val paymentUrl = "$BASE_URL?device=$deviceId"

        try {
            val writer = QRCodeWriter()
            val size = 512
            val bitMatrix = writer.encode(paymentUrl, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            qrImage.setImageBitmap(bmp)
        } catch (e: Exception) {
            findViewById<TextView>(R.id.qrError).text = "QR generation failed"
        }

        // Real-time listen for key assigned to this device
        listenForKey(deviceId)

        btnBack.setOnClickListener {
            keyListener?.let { database.child("pending_devices").child(deviceId).removeEventListener(it) }
            finish()
        }
    }

    private fun listenForKey(deviceId: String) {
        statusText.text = "⏳ Waiting for payment..."

        keyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val key = snapshot.child("key").getValue(String::class.java) ?: return
                val userName = snapshot.child("name").getValue(String::class.java) ?: ""
                val expiry = snapshot.child("expiry").getValue(String::class.java) ?: "No Limit"

                if (key.isNotEmpty()) {
                    // Save key + auto login
                    val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("saved_key", key)
                        .putString("user_name", userName)
                        .putString("expiry", expiry)
                        .putBoolean("welcome_shown", false) // show welcome again
                        .apply()

                    statusText.text = "✅ Payment received! Starting A-TV..."

                    // Write deviceId to the key in Firebase
                    database.child("keys").child(key).child("deviceId").setValue(deviceId)

                    // Remove pending device entry
                    database.child("pending_devices").child(deviceId).removeValue()

                    // Auto launch player after short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        startActivity(Intent(this@QrPaymentActivity, PlayerActivity::class.java))
                        finish()
                    }, 2000)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("pending_devices").child(deviceId)
            .addValueEventListener(keyListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        keyListener?.let { database.child("pending_devices").child(deviceId).removeEventListener(it) }
    }
}
