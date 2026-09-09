package com.example.cipherview.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WatermarkOverlay(
    viewerNickname: String,
    deviceId: String,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    val watermarkText = "CIPHERVIEW SECURE • $viewerNickname • $dateStr • $deviceId"

    Canvas(modifier = modifier.fillMaxSize()) {
        val paint = Paint().apply {
            color = android.graphics.Color.argb(35, 120, 140, 180) // Semi-transparent subtle watermark
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val canvasWidth = size.width
        val canvasHeight = size.height

        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.rotate(-30f, canvasWidth / 2f, canvasHeight / 2f)

        val stepY = 160f
        val stepX = 450f

        var y = -canvasHeight
        while (y < canvasHeight * 2) {
            var x = -canvasWidth
            while (x < canvasWidth * 2) {
                drawContext.canvas.nativeCanvas.drawText(watermarkText, x, y, paint)
                x += stepX
            }
            y += stepY
        }

        drawContext.canvas.nativeCanvas.restore()
    }
}
