package com.ashuchat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.BrandPrimary
import com.ashuchat.app.ui.theme.MidnightBg
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    onRoomScanned: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scannedCode by remember { mutableStateOf<String?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
            cameraExecutor.shutdown()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scannerLaser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val barcodeScanner = BarcodeScanning.getClient()

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && scannedCode == null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            if (barcode.format == Barcode.FORMAT_QR_CODE) {
                                                val raw = barcode.rawValue
                                                if (!raw.isNullOrBlank() && scannedCode == null) {
                                                    scannedCode = raw
                                                    onRoomScanned(raw)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("QrScannerScreen", "Camera binding error: ${e.localizedMessage}", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Target Reticle & Laser Overlay
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(3.5.dp, com.ashuchat.app.ui.theme.NeoHeaderYellow, RoundedCornerShape(24.dp))
                ) {
                    // Moving scan laser
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, laserY.dp.roundToPx()) }
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, com.ashuchat.app.ui.theme.NeoHeaderYellow, Color.Transparent)
                                )
                            )
                    )
                }
            }

            // Top Instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp)
            ) {
                com.ashuchat.app.ui.components.NeoPillBadge(
                    text = "POINT AT QR CODE",
                    backgroundColor = com.ashuchat.app.ui.theme.NeoSurfaceElevated
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Align the code inside the yellow frame to connect",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Permission request fallback
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.ashuchat.app.ui.theme.NeoBackground)
                    .padding(32.dp)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.Center
            ) {
                com.ashuchat.app.ui.components.NeoBrutalCard(
                    backgroundColor = com.ashuchat.app.ui.theme.NeoCardYellow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CAMERA ACCESS NEEDED",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = com.ashuchat.app.ui.theme.NeoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AshuChat needs camera access to scan your peer's QR code and establish a direct connection.",
                            fontSize = 13.sp,
                            color = com.ashuchat.app.ui.theme.NeoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        com.ashuchat.app.ui.components.NeoBrutalButton(
                            text = "GRANT PERMISSION",
                            backgroundColor = com.ashuchat.app.ui.theme.NeoAccentYellow,
                            textColor = com.ashuchat.app.ui.theme.NeoBackground,
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                }
            }
        }

        // Back Button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
        ) {
            com.ashuchat.app.ui.components.NeoBrutalIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                backgroundColor = com.ashuchat.app.ui.theme.NeoSurfaceElevated,
                tint = com.ashuchat.app.ui.theme.NeoTextPrimary,
                size = 46.dp,
                onClick = onNavigateBack
            )
        }
    }
}
