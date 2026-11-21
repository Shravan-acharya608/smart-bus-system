package com.smartbus.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun generateRollingTOTP(secret: String, timeStepSeconds: Long = 15): String {
    return try {
        val counter = System.currentTimeMillis() / 1000 / timeStepSeconds
        val buffer = ByteBuffer.allocate(8).putLong(counter).array()
        val secretBytes = secret.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val hmac = mac.doFinal(buffer)
        val offset = hmac[hmac.size - 1].toInt() and 0x0f
        val code = ((hmac[offset].toInt() and 0x7f) shl 24) or
                   ((hmac[offset + 1].toInt() and 0xff) shl 16) or
                   ((hmac[offset + 2].toInt() and 0xff) shl 8) or
                   (hmac[offset + 3].toInt() and 0xff)
        (Math.abs(code) % 10000).toString().padStart(4, '0')
    } catch (_: Exception) {
        ((System.currentTimeMillis() / 1000 / timeStepSeconds) % 10000).toString().padStart(4, '0')
    }
}

fun generateQRBitmap(content: String, size: Int = 400): Bitmap? {
    return try {
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
fun RollingQRCode(
    ticketId: String,
    ticketCode: String,
    modifier: Modifier = Modifier
) {
    val timeStep = 15L
    var otp by remember { mutableStateOf(generateRollingTOTP(ticketCode, timeStep)) }
    var secondsLeft by remember { mutableStateOf(timeStep - (System.currentTimeMillis() / 1000 % timeStep)) }

    val qrContent = "$ticketCode:$otp"
    val qrBitmap by remember(qrContent) {
        derivedStateOf { generateQRBitmap(qrContent) }
    }

    LaunchedEffect(ticketCode) {
        while (true) {
            val now = System.currentTimeMillis() / 1000
            secondsLeft = timeStep - (now % timeStep)
            otp = generateRollingTOTP(ticketCode, timeStep)
            delay(1000L)
        }
    }

    val progress = secondsLeft.toFloat() / timeStep.toFloat()
    val arcColor = when {
        secondsLeft > 9 -> Color(0xFF2E7D32)
        secondsLeft > 4 -> Color(0xFFF57F17)
        else -> Color(0xFFC62828)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Countdown ring overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 6.dp.toPx()
                val radius = size.minDimension / 2f - strokeWidth
                drawArc(
                    color = arcColor.copy(alpha = 0.2f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth, strokeWidth),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(strokeWidth, strokeWidth),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }

            // QR Code
            qrBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Dynamic Ticket QR Code",
                    modifier = Modifier.size(190.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Rotates in ${secondsLeft}s (Anti-Screenshot Protected)",
            style = MaterialTheme.typography.labelSmall,
            color = arcColor,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4-Digit Fallback OTP Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "4-DIGIT BACKUP PIN",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = otp,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
