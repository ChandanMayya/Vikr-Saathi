package com.kex.vikrsaathi.ui.security

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EditText
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.security.AppLockManager
import com.kex.vikrsaathi.data.security.PinHasher
import com.kex.vikrsaathi.data.security.PinVerifyResult
import com.kex.vikrsaathi.ui.help.HelpFooterAction
import com.kex.vikrsaathi.ui.help.HelpOverlay
import com.kex.vikrsaathi.ui.help.HelpScreen
import java.util.concurrent.Executor

class AppLockGateController(
    private val activity: AppCompatActivity,
    private val overlayRoot: View,
    private val appLockManager: AppLockManager,
    private val onUnlocked: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val lockContent: View = overlayRoot.findViewById(R.id.lockContent)
    private val forgotPinContent: View = overlayRoot.findViewById(R.id.forgotPinContent)
    private val pinSection: View = overlayRoot.findViewById(R.id.layoutPinSection)
    private val dots: List<View>
    private val errorView: TextView
    private val biometricButton: View
    private val forgotPinErrorView: TextView
    private val recoveryCodeLayout: View
    private val recoveryCodeInputLayout: TextInputLayout
    private val recoveryCodeInput: EditText
    private val submitRecoveryButton: View
    private val executor: Executor = ContextCompat.getMainExecutor(activity)
    private val enteredDigits = StringBuilder()
    private var lockoutRunnable: Runnable? = null
    private var showingForgotPin = false
    private var showingRecoveryInput = false
    private var biometricMode = BiometricMode.NONE

    private val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                when (biometricMode) {
                    BiometricMode.UNLOCK -> {
                        biometricMode = BiometricMode.NONE
                        completeUnlock()
                    }
                    BiometricMode.DEVICE_RESET -> {
                        biometricMode = BiometricMode.NONE
                        showResetPinDialog()
                    }
                    BiometricMode.NONE -> Unit
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val mode = biometricMode
                biometricMode = BiometricMode.NONE
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    return
                }
                when (mode) {
                    BiometricMode.UNLOCK -> showError(errString.toString())
                    BiometricMode.DEVICE_RESET -> showForgotPinError(errString.toString())
                    BiometricMode.NONE -> Unit
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
        forgotPinErrorView = overlayRoot.findViewById(R.id.textForgotPinError)
        recoveryCodeLayout = overlayRoot.findViewById(R.id.layoutRecoveryCode)
        recoveryCodeInputLayout = recoveryCodeLayout as TextInputLayout
        recoveryCodeInput = overlayRoot.findViewById(R.id.editRecoveryCode)
        submitRecoveryButton = overlayRoot.findViewById(R.id.buttonSubmitRecoveryCode)

        ViewCompat.setOnApplyWindowInsetsListener(lockContent) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = insets.top + view.resources.getDimensionPixelSize(R.dimen.pin_header_top_padding),
                bottom = insets.bottom
            )
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(forgotPinContent) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = insets.top + view.resources.getDimensionPixelSize(R.dimen.pin_header_top_padding),
                bottom = insets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(lockContent)
        ViewCompat.requestApplyInsets(forgotPinContent)

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
        overlayRoot.findViewById<View>(R.id.buttonLockHelp).setOnClickListener { showLockHelp() }
        overlayRoot.findViewById<View>(R.id.buttonForgotPinBack).setOnClickListener { showUnlockPanel() }
        overlayRoot.findViewById<View>(R.id.buttonBackToUnlock).setOnClickListener { showUnlockPanel() }
        overlayRoot.findViewById<MaterialButton>(R.id.buttonDevicePasswordReset).setOnClickListener {
            showDeviceCredentialPrompt()
        }
        overlayRoot.findViewById<MaterialButton>(R.id.buttonRecoveryCodeReset).setOnClickListener {
            toggleRecoveryCodeInput()
        }
        submitRecoveryButton.setOnClickListener { submitRecoveryCode() }
        updateBiometricVisibility()
    }

    fun show() {
        showUnlockPanel()
        resetEntry()
        overlayRoot.alpha = 1f
        overlayRoot.isVisible = true
        updateBiometricVisibility()
        updateLockoutState()
        updateForgotPinOptions()
        if (canUseBiometric()) {
            overlayRoot.post { showBiometricPrompt() }
        }
    }

    fun hide() {
        cancelBiometric()
        overlayRoot.isVisible = false
        showingForgotPin = false
        showingRecoveryInput = false
        resetEntry()
        lockoutRunnable?.let(handler::removeCallbacks)
        lockoutRunnable = null
    }

    fun isShowing(): Boolean = overlayRoot.isVisible

    fun handleBackPress(): Boolean {
        if (!overlayRoot.isVisible) return false
        if (showingForgotPin) {
            if (showingRecoveryInput) {
                showingRecoveryInput = false
                recoveryCodeLayout.isVisible = false
                submitRecoveryButton.isVisible = false
                clearForgotPinError()
                return true
            }
            showUnlockPanel()
            return true
        }
        activity.moveTaskToBack(true)
        return true
    }

    private fun showLockHelp() {
        HelpOverlay.show(
            activity = activity,
            screen = HelpScreen.APP_LOCK,
            footerAction = HelpFooterAction(
                label = activity.getString(R.string.app_lock_forgot_pin_action)
            ) {
                showForgotPinPanel()
            }
        )
    }

    private fun showUnlockPanel() {
        cancelBiometric()
        showingForgotPin = false
        showingRecoveryInput = false
        lockContent.isVisible = true
        forgotPinContent.isVisible = false
        recoveryCodeLayout.isVisible = false
        submitRecoveryButton.isVisible = false
        clearForgotPinError()
    }

    private fun showForgotPinPanel() {
        cancelBiometric()
        showingForgotPin = true
        showingRecoveryInput = false
        lockContent.isVisible = false
        forgotPinContent.isVisible = true
        recoveryCodeLayout.isVisible = false
        submitRecoveryButton.isVisible = false
        clearForgotPinError()
        updateForgotPinOptions()
    }

    private fun cancelBiometric() {
        biometricMode = BiometricMode.NONE
        try {
            biometricPrompt.cancelAuthentication()
        } catch (_: Exception) {
        }
    }

    private fun updateForgotPinOptions() {
        val deviceButton = overlayRoot.findViewById<MaterialButton>(R.id.buttonDevicePasswordReset)
        val recoveryButton = overlayRoot.findViewById<MaterialButton>(R.id.buttonRecoveryCodeReset)
        val deviceAvailable = appLockManager.canUseDeviceCredential(activity)
        val recoveryAvailable = appLockManager.hasRecoveryCode()

        deviceButton.isEnabled = deviceAvailable
        overlayRoot.findViewById<TextView>(R.id.textDevicePasswordHint).text =
            if (deviceAvailable) {
                activity.getString(R.string.app_lock_device_password_hint)
            } else {
                activity.getString(R.string.app_lock_device_password_unavailable)
            }

        recoveryButton.isEnabled = recoveryAvailable
        overlayRoot.findViewById<TextView>(R.id.textRecoveryCodeHint).text =
            if (recoveryAvailable) {
                activity.getString(R.string.app_lock_recovery_code_hint)
            } else {
                activity.getString(R.string.app_lock_recovery_code_unavailable)
            }
    }

    private fun toggleRecoveryCodeInput() {
        if (!appLockManager.hasRecoveryCode()) {
            showForgotPinError(activity.getString(R.string.app_lock_recovery_code_unavailable))
            return
        }
        showingRecoveryInput = true
        recoveryCodeLayout.isVisible = true
        submitRecoveryButton.isVisible = true
        recoveryCodeInput.text = null
        recoveryCodeInputLayout.error = null
        clearForgotPinError()
        recoveryCodeInput.requestFocus()
    }

    private fun submitRecoveryCode() {
        recoveryCodeInputLayout.error = null
        val code = recoveryCodeInput.text?.toString().orEmpty()
        if (code.isBlank()) {
            recoveryCodeInputLayout.error = activity.getString(R.string.app_lock_recovery_code_required)
            return
        }
        if (appLockManager.verifyRecoveryCode(code)) {
            showResetPinDialog()
        } else {
            showForgotPinError(activity.getString(R.string.app_lock_recovery_code_invalid))
        }
    }

    private fun showDeviceCredentialPrompt() {
        if (!appLockManager.canUseDeviceCredential(activity)) {
            showForgotPinError(activity.getString(R.string.app_lock_device_password_unavailable))
            return
        }
        biometricMode = BiometricMode.DEVICE_RESET
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_device_credential_title))
            .setSubtitle(activity.getString(R.string.app_lock_device_credential_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun showResetPinDialog() {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_pin_setup, null)
        val layoutPin = dialogView.findViewById<TextInputLayout>(R.id.layoutPin)
        val layoutConfirm = dialogView.findViewById<TextInputLayout>(R.id.layoutPinConfirm)
        val editPin = dialogView.findViewById<EditText>(R.id.editPin)
        val editConfirm = dialogView.findViewById<EditText>(R.id.editPinConfirm)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_lock_reset_pin_title)
            .setMessage(R.string.app_lock_reset_pin_message)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        layoutPin.error = null
                        layoutConfirm.error = null
                        val pin = editPin.text?.toString().orEmpty()
                        val confirm = editConfirm.text?.toString().orEmpty()
                        when {
                            !PinHasher.isValidPinFormat(pin) -> {
                                layoutPin.error = activity.getString(R.string.app_lock_pin_invalid)
                            }
                            pin != confirm -> {
                                layoutConfirm.error = activity.getString(R.string.app_lock_pin_mismatch)
                            }
                            appLockManager.resetPin(pin) -> {
                                dialog.dismiss()
                                showUnlockPanel()
                                completeUnlock()
                            }
                            else -> {
                                layoutPin.error = activity.getString(R.string.app_lock_pin_invalid)
                            }
                        }
                    }
                }
                dialog.show()
            }
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
        appLockManager.unlock()
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
        biometricMode = BiometricMode.UNLOCK
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

    private fun clearForgotPinError() {
        forgotPinErrorView.text = ""
        forgotPinErrorView.isVisible = false
    }

    private fun showForgotPinError(message: String) {
        forgotPinErrorView.text = message
        forgotPinErrorView.isVisible = true
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

    private enum class BiometricMode {
        NONE,
        UNLOCK,
        DEVICE_RESET
    }
}
