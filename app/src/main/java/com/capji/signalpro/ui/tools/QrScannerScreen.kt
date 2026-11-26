package com.capji.signalpro.ui.tools

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Full-screen camera that reports the first QR code it reads. Runs fully on-device. */
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onClose: () -> Unit) {
    val t = LocalLang.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPreview(onResult)
            ScanFrame()
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = t("إغلاق", "Close"), tint = Color.White)
            }
            Text(
                t("وجّه الكاميرا نحو رمز الواي فاي", "Point the camera at a Wi-Fi QR code"),
                Modifier.align(Alignment.BottomCenter).padding(48.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val delivered = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setImageAnalysisAnalyzer(executor) { image ->
            analyze(image, scanner) { value ->
                if (delivered.compareAndSet(false, true)) mainExecutor.execute { result(value) }
            }
        }
        controller.bindToLifecycle(owner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: BarcodeScanner, onValue: (String) -> Unit) {
    val image = proxy.image
    if (image == null) {
        proxy.close()
        return
    }
    runCatching {
        scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onValue) }
            .addOnCompleteListener { proxy.close() }
    }.onFailure { proxy.close() }
}

@Composable
private fun ScanFrame() {
    val transition = rememberInfiniteTransition(label = "scan")
    val line = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "line",
    )
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.68f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
        drawRoundRect(
            Capji.Cyan, topLeft, Size(side, side),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(3.dp.toPx()),
        )
        val y = topLeft.y + side * (0.06f + 0.88f * line.value)
        drawLine(Capji.Emerald, Offset(topLeft.x + 16, y), Offset(topLeft.x + side - 16, y), 3.dp.toPx())
    }
}
