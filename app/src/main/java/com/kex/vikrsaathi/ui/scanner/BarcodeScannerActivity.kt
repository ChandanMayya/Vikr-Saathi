package com.kex.vikrsaathi.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.databinding.ActivityBarcodeScannerBinding
import com.kex.vikrsaathi.util.SystemBarInsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private var camera: Camera? = null
    private var flashEnabled = false
    private val processing = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private var continuousMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarInsets.applyFullscreenBottomControls(this, binding.root)

        continuousMode = intent.getBooleanExtra(EXTRA_CONTINUOUS, false)

        binding.buttonCloseScanner.setOnClickListener { finish() }
        binding.buttonToggleFlash.setOnClickListener { toggleFlash() }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient()
            analyzer.setAnalyzer(executor) { imageProxy ->
                processImage(scanner, imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImage(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue
                if (!value.isNullOrBlank()) {
                    runOnUiThread {
                        binding.textScanStatus.text = getString(R.string.barcode_found, value)
                    }
                    if (continuousMode) {
                        BarcodeScanBus.onBarcodeScanned?.invoke(value)
                        processing.set(false)
                    } else {
                        returnResult(value)
                    }
                } else {
                    processing.set(false)
                }
            }
            .addOnFailureListener {
                processing.set(false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun returnResult(barcode: String) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_BARCODE, barcode)
        )
        finish()
    }

    private fun toggleFlash() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, R.string.flash_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        flashEnabled = !flashEnabled
        cam.cameraControl.enableTorch(flashEnabled)
        binding.buttonToggleFlash.text = getString(
            if (flashEnabled) R.string.flashlight_on else R.string.flashlight
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        const val EXTRA_BARCODE = "extra_barcode"
        const val EXTRA_CONTINUOUS = "extra_continuous"
        private const val REQUEST_CAMERA = 1001
    }
}
