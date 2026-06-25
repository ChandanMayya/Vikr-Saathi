package com.loctell.vikrsaathi.ui.scanner

/**
 * Simple callback bus for continuous barcode scanning from [BarcodeScannerActivity].
 */
object BarcodeScanBus {
    var onBarcodeScanned: ((String) -> Unit)? = null
}
