package com.capji.signalpro.qr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.IOException

/** Renders the shareable CAPJI Wi-Fi card. The password appears only inside the code. */
object QrCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1500
    private val cyan = Color.parseColor("#00E5FF")
    private val emerald = Color.parseColor("#00E676")
    private val violet = Color.parseColor("#7C4DFF")
    private val navy = Color.parseColor("#070B14")

    fun qrBitmap(payload: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    fun render(payload: String, ssid: String, arabic: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            intArrayOf(navy, Color.parseColor("#0D1630"), navy), null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = cyan
        canvas.drawRoundRect(RectF(30f, 30f, WIDTH - 30f, HEIGHT - 30f), 56f, 56f, paint)

        drawEmblem(canvas, WIDTH / 2f, 180f, 100f)

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        fun line(value: String, y: Float, size: Float, color: Int) {
            text.textSize = size
            text.color = color
            val fitted = TextUtils.ellipsize(value, text, WIDTH - 140f, TextUtils.TruncateAt.END)
            canvas.drawText(fitted.toString(), WIDTH / 2f, y, text)
        }
        line("CAPJI", 365f, 76f, Color.WHITE)
        line(if (arabic) "اتصل بشبكة الواي فاي" else "Connect to Wi-Fi", 435f, 44f, cyan)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(150f, 490f, 930f, 1270f), 40f, 40f, paint)
        // Nearest-neighbour drawing keeps the modules crisp.
        canvas.drawBitmap(qrBitmap(payload, 720), null, RectF(180f, 520f, 900f, 1240f), Paint())

        line(ssid, 1345f, 54f, Color.WHITE)
        line(
            if (arabic) "وجّه كاميرا هاتفك نحو الرمز" else "Point your camera at the code",
            1405f, 34f, emerald,
        )
        line("CAPJI Signal Pro", 1448f, 26f, Color.parseColor("#8FA3C0"))
        return bitmap
    }

    private fun drawEmblem(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = violet
        paint.strokeWidth = 5f
        canvas.drawCircle(x, y, radius, paint)
        paint.color = cyan
        paint.strokeWidth = 18f
        val r = radius * 0.68f
        canvas.drawArc(RectF(x - r, y - r, x + r, y + r), 45f, 270f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = emerald
        val s = radius / 100f
        val bolt = Path().apply {
            moveTo(x + 14 * s, y - 52 * s)
            lineTo(x - 28 * s, y + 8 * s)
            lineTo(x - 2 * s, y + 8 * s)
            lineTo(x - 14 * s, y + 52 * s)
            lineTo(x + 30 * s, y - 12 * s)
            lineTo(x + 4 * s, y - 12 * s)
            close()
        }
        canvas.drawPath(bolt, paint)
    }

    /** Saves into Pictures/CAPJI. Needs WRITE_EXTERNAL_STORAGE only before Android 10. */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        val resolver = context.contentResolver
        var uri: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "CAPJI-WiFi-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CAPJI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IOException("No output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            false
        }
    }

    fun share(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "capji-wifi-card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri("CAPJI", uri)
        context.startActivity(
            Intent.createChooser(send, "CAPJI").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.isSuccess
}
