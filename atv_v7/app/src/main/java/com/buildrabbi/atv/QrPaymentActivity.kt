package com.buildrabbi.atv

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class QrPaymentActivity : AppCompatActivity() {

    companion object {
        const val PAYMENT_URL = "https://build-rabbi.github.io/A-TV/bkash-payment.html"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_payment)

        val qrImage = findViewById<ImageView>(R.id.qrImage)
        val btnBack = findViewById<Button>(R.id.btnBack)

        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(PAYMENT_URL, BarcodeFormat.QR_CODE, 600, 600)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            qrImage.setImageBitmap(bmp)
        } catch (e: Exception) {
            findViewById<TextView>(R.id.qrError).text = "Could not generate QR code"
        }

        btnBack.setOnClickListener { finish() }
    }
}
