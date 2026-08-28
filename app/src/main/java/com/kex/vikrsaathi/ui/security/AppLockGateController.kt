package com.kex.vikrsaathi.ui.security

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.security.AppLockManager
import com.kex.vikrsaathi.data.security.PinHasher
import com.kex.vikrsaathi.data.security.PinVerifyResult
import java.util.concurrent.Executor

class AppLockGateController(
    private val activity: AppCompatActivity,
    private val overlayRoot: View,
    private val appLockManager: AppLockManager,
    private val onUnlocked: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val lockContent: View = overlayRoot.findViewById(R.id.lockContent)
    private val pinSection: View = overlayRoot.findViewById(R.id.layoutPinSection)
    private val dots: List<View>
    private val errorView: TextView
    private val biometricButton: View
    private val executor: Executor = ContextCompat.getMainExecutor(activity)
    private val enteredDigits = StringBuilder()
    private var lockoutRunnable: Runnable? = null

    private val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                completeUnlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    showError(errString.toString())
                }
            }
        }
    )

    init {
        dots = listOf(
            overlayRoot.findViewById(R.id.dot1),
            overlayRoot.findViewById(R.id.dot2),
            overlayRoot.findViewById(R.id.dot3),
            overlayRoot.findViewById(R.id.dot4)
        )
        errorView = overlayRoot.findViewById(R.id.textLockError)
        biometricButton = overlayRoot.findViewById(R.id.buttonKeyBiometric)

        ViewCompat.setOnApplyWindowInsetsListener(lockContent) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = insets.top + view.resources.getDimensionPixelSize(R.dimen.pin_header_top_padding),
                bottom = insets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(lockContent)

        bindDigitKey(R.id.buttonKey0, "0")
        bindDigitKey(R.id.buttonKey1, "1")
        bindDigitKey(R.id.buttonKey2, "2")
        bindDigitKey(R.id.buttonKey3, "3")
        bindDigitKey(R.id.buttonKey4, "4")
        bindDigitKey(R.id.buttonKey5, "5")
        bindDigitKey(R.id.buttonKey6, "6")
        bindDigitKey(R.id.buttonKey7, "7")
        bindDigitKey(R.id.buttonKey8, "8")
        bindDigitKey(R.id.buttonKey9, "9")

        overlayRoot.findViewById<View>(R.id.buttonKeyBackspace).setOnClickListener {
            if (enteredDigits.isNotEmpty()) {
                enteredDigits.deleteCharAt(enteredDigits.lastIndex)
                updateDots()
            }
            clearError()
        }

        biometricButton.setOnClickListener { showBiometricPrompt() }
        updateBiometricVisibility()
    }

    fun show() {
        resetEntry()
        overlayRoot.alpha = 1f
        overlayRoot.isVisible = true
        updateBiometricVisibility()
        updateLockoutState()
        if (canUseBiometric()) {
            overlayRoot.post { showBiometricPrompt() }
        }
    }

    fun hide() {
        overlayRoot.isVisible = false
        resetEntry()
        lockoutRunnable?.let(handler::removeCallbacks)
        lockoutRunnable = null
    }

    fun isShowing(): Boolean = overlayRoot.isVisible

    fun handleBackPress(): Boolean {
        if (!overlayRoot.isVisible) return false
        activity.moveTaskToBack(true)
        return true
    }

    private fun bindDigitKey(id: Int, digit: String) {
        overlayRoot.findViewById<MaterialButton>(id).setOnClickListener {
            if (appLockManager.isLockedOut()) {
                updateLockoutState()
                return@setOnClickListener
            }
            if (enteredDigits.length >= PinHasher.PIN_LENGTH) return@setOnClickListener
            enteredDigits.append(digit)
            updateDots(animateLatest = true)
            clearError()
            if (enteredDigits.length == PinHasher.PIN_LENGTH) {
                submitPin(enteredDigits.toString())
            }
        }
    }

    private fun submitPin(pin: String) {
        when (val result = appLockManager.verifyPin(pin)) {
            PinVerifyResult.Success -> completeUnlock()
            PinVerifyResult.InvalidPin -> {
                showError(activity.getString(R.string.app_lock_wrong_pin))
                shakePinSection()
                resetEntry()
            }
            is PinVerifyResult.LockedOut -> {
                showLockout(result.remainingMillis)
                shakePinSection()
                resetEntry()
            }
        }
    }

    private fun completeUnlock() {
        overlayRoot.animate()
            .alpha(0f)
            .setDuration(UNLOCK_FADE_MS)
            .withEndAction {
                hide()
                onUnlocked()
            }
            .start()
    }

    private fun showBiometricPrompt() {
        if (!canUseBiometric()) return
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_biometric_title))
            .setSubtitle(activity.getString(R.string.app_lock_biometric_subtitle))
            .setNegativeButtonText(activity.getString(R.string.app_lock_use_pin))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun canUseBiometric(): Boolean {
        if (!appLockManager.biometricEnabled) return false
        val manager = BiometricManager.from(activity)
        val status = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun updateBiometricVisibility() {
        biometricButton.visibility = if (canUseBiometric()) View.VISIBLE else View.INVISIBLE
    }

    private fun updateDots(animateLatest: Boolean = false) {
        val latestIndex = enteredDigits.length - 1
        dots.forEachIndexed { index, dot ->
            val filled = index < enteredDigits.length
            dot.setBackgroundResource(
                if (filled) R.drawable.bg_pin_dot_filled else R.drawable.bg_pin_dot_empty
            )
            if (animateLatest && filled && index == latestIndex) {
                dot.animate()
                    .scaleX(1.18f)
                    .scaleY(1.18f)
                    .setDuration(DOT_POP_MS)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        dot.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(DOT_SETTLE_MS)
                            .start()
                    }
                    .start()
            }
        }
    }

    private fun resetEntry() {
        enteredDigits.clear()
        dots.forEach { dot ->
            dot.animate().cancel()
            dot.scaleX = 1f
            dot.scaleY = 1f
        }
        updateDots()
    }

    private fun clearError() {
        errorView.text = ""
        errorView.visibility = View.INVISIBLE
    }

    private fun showError(message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun shakePinSection() {
        pinSection.animate().cancel()
        pinSection.translationX = 0f
        pinSection.animate()
            .translationX(-14f)
            .setDuration(SHAKE_STEP_MS)
            .withEndAction {
                pinSection.animate()
                    .translationX(14f)
                    .setDuration(SHAKE_STEP_MS)
                    .withEndAction {
                        pinSection.animate()
                            .translationX(-8f)
                            .setDuration(SHAKE_STEP_MS)
                            .withEndAction {
                                pinSection.animate()
                                    .translationX(0f)
                                    .setDuration(SHAKE_STEP_MS)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun showLockout(remainingMillis: Long) {
        updateLockoutState(remainingMillis)
    }

    private fun updateLockoutState(initialRemaining: Long = appLockManager.lockoutRemainingMillis()) {
        lockoutRunnable?.let(handler::removeCallbacks)
        var remaining = initialRemaining
        if (remaining <= 0L) {
            clearError()
            return
        }
        val runnable = object : Runnable {
            override fun run() {
                remaining = appLockManager.lockoutRemainingMillis()
                if (remaining <= 0L) {
                    clearError()
                    lockoutRunnable = null
                    return
                }
                val seconds = ((remaining + 999) / 1000).toInt()
                showError(
                    activity.getString(
                        R.string.app_lock_locked_out,
                        seconds
                    )
                )
                handler.postDelayed(this, 500L)
            }
        }
        lockoutRunnable = runnable
        runnable.run()
    }

    companion object {
        private const val DOT_POP_MS = 90L
        private const val DOT_SETTLE_MS = 70L
        private const val SHAKE_STEP_MS = 45L
        private const val UNLOCK_FADE_MS = 180L
    }
}
