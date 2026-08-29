package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.security.AppLockManager
import com.kex.vikrsaathi.data.security.AppLockTimeout
import com.kex.vikrsaathi.data.security.PinHasher
import com.kex.vikrsaathi.data.security.PinSetupResult
import com.kex.vikrsaathi.data.security.PinVerifyResult
import com.kex.vikrsaathi.databinding.FragmentSecuritySettingsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class SecuritySettingsFragment : Fragment() {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appLockManager: AppLockManager
    private var suppressLockSwitch = false
    private var suppressBiometricSwitch = false
    private var suppressTimeoutSelection = false
    private var accessGranted = false
    private var uiBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessGranted = savedInstanceState?.getBoolean(KEY_ACCESS_GRANTED) == true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecuritySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appLockManager = (requireActivity().application as VikrSaathiApp).appLockManager

        if (accessGranted || !appLockManager.isLockEnabled) {
            grantAccessAndShow()
        } else {
            binding.root.isVisible = false
            requestSecurityAccess(
                onGranted = { grantAccessAndShow() },
                onDenied = {
                    if (isAdded) findNavController().popBackStack()
                }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ACCESS_GRANTED, accessGranted)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        uiBound = false
    }

    private fun grantAccessAndShow() {
        if (!isAdded || _binding == null) return
        accessGranted = true
        binding.root.isVisible = true
        if (!uiBound) {
            uiBound = true
            installHelpMenu(HelpScreen.SECURITY_SETTINGS)
            bindState()
            bindListeners()
        }
    }

    private fun bindListeners() {
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressLockSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                enableAppLock()
            } else {
                disableAppLock()
            }
        }

        binding.buttonChangePin.setOnClickListener { showChangePinDialog() }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (suppressBiometricSwitch) return@setOnCheckedChangeListener
            appLockManager.setBiometricEnabled(isChecked)
        }

        binding.radioLockTimeout.setOnCheckedChangeListener { _, checkedId ->
            if (suppressTimeoutSelection) return@setOnCheckedChangeListener
            timeoutFor(checkedId)?.let(appLockManager::setTimeout)
        }
    }

    private fun requestSecurityAccess(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (appLockManager.biometricEnabled && canUseBiometric()) {
            showAccessBiometricPrompt(onGranted, onDenied)
        } else {
            promptCurrentPin(
                title = getString(R.string.settings_security_unlock_title),
                onVerified = { onGranted() },
                onCancel = onDenied
            )
        }
    }

    private fun showAccessBiometricPrompt(onGranted: () -> Unit, onDenied: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(requireContext())
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onGranted()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            promptCurrentPin(
                                title = getString(R.string.settings_security_unlock_title),
                                onVerified = { onGranted() },
                                onCancel = onDenied
                            )
                        }
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onDenied()
                        else -> {
                            promptCurrentPin(
                                title = getString(R.string.settings_security_unlock_title),
                                onVerified = { onGranted() },
                                onCancel = onDenied
                            )
                        }
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.settings_security_unlock_title))
            .setSubtitle(getString(R.string.settings_security_unlock_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.app_lock_use_pin))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun bindState() {
        val enabled = appLockManager.isLockEnabled
        suppressLockSwitch = true
        binding.switchAppLock.isChecked = enabled
        suppressLockSwitch = false

        binding.buttonChangePin.isEnabled = enabled || appLockManager.hasPin()

        val biometricAvailable = canUseBiometric()
        suppressBiometricSwitch = true
        binding.switchBiometric.isEnabled = enabled && biometricAvailable
        binding.switchBiometric.isChecked = enabled && appLockManager.biometricEnabled && biometricAvailable
        suppressBiometricSwitch = false

        bindTimeout(appLockManager.getTimeout())
    }

    private fun enableAppLock() {
        if (appLockManager.hasPin()) {
            appLockManager.enableLock()
            bindState()
            return
        }
        showSetupPinDialog(
            title = getString(R.string.settings_security_setup_pin_title),
            onSuccess = {
                appLockManager.enableLock()
                bindState()
            },
            onCancel = { bindState() }
        )
    }

    private fun disableAppLock() {
        promptCurrentPin(
            title = getString(R.string.settings_security_disable_pin_title),
            onVerified = {
                when (val result = appLockManager.disableLock(it)) {
                    PinVerifyResult.Success -> bindState()
                    PinVerifyResult.InvalidPin -> {
                        showMessage(getString(R.string.app_lock_wrong_pin))
                        bindState()
                    }
                    is PinVerifyResult.LockedOut -> {
                        showMessage(formatLockout(result.remainingMillis))
                        bindState()
                    }
                }
            },
            onCancel = { bindState() }
        )
    }

    private fun showChangePinDialog() {
        promptCurrentPin(
            title = getString(R.string.settings_security_current_pin_title),
            onVerified = { currentPin ->
                showSetupPinDialog(
                    title = getString(R.string.settings_security_new_pin_title),
                    onSuccess = { newPin ->
                        when (val result = appLockManager.changePin(currentPin, newPin)) {
                            PinVerifyResult.Success -> {
                                showMessage(getString(R.string.settings_security_pin_changed))
                                bindState()
                            }
                            PinVerifyResult.InvalidPin -> showMessage(getString(R.string.app_lock_pin_invalid))
                            is PinVerifyResult.LockedOut -> showMessage(formatLockout(result.remainingMillis))
                        }
                    },
                    onCancel = { bindState() }
                )
            },
            onCancel = { bindState() }
        )
    }

    private fun showSetupPinDialog(
        title: String,
        onSuccess: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_setup, null)
        val layoutPin = dialogView.findViewById<TextInputLayout>(R.id.layoutPin)
        val layoutConfirm = dialogView.findViewById<TextInputLayout>(R.id.layoutPinConfirm)
        val editPin = dialogView.findViewById<EditText>(R.id.editPin)
        val editConfirm = dialogView.findViewById<EditText>(R.id.editPinConfirm)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
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
                                layoutPin.error = getString(R.string.app_lock_pin_invalid)
                            }
                            pin != confirm -> {
                                layoutConfirm.error = getString(R.string.app_lock_pin_mismatch)
                            }
                            else -> {
                                when (val result = appLockManager.setupPin(pin)) {
                                    is PinSetupResult.Success -> {
                                        dialog.dismiss()
                                        result.recoveryCode?.let { showRecoveryCodeDialog(it) }
                                        onSuccess(pin)
                                    }
                                    PinSetupResult.InvalidFormat -> {
                                        layoutPin.error = getString(R.string.app_lock_pin_invalid)
                                    }
                                }
                            }
                        }
                    }
                }
                dialog.setOnCancelListener { onCancel() }
                dialog.show()
            }
    }

    private fun promptCurrentPin(
        title: String,
        onVerified: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_setup, null)
        dialogView.findViewById<TextInputLayout>(R.id.layoutPinConfirm).visibility = View.GONE
        val layoutPin = dialogView.findViewById<TextInputLayout>(R.id.layoutPin)
        val editPin = dialogView.findViewById<EditText>(R.id.editPin)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.continue_label, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        layoutPin.error = null
                        val pin = editPin.text?.toString().orEmpty()
                        if (!PinHasher.isValidPinFormat(pin)) {
                            layoutPin.error = getString(R.string.app_lock_pin_invalid)
                            return@setOnClickListener
                        }
                        when (val result = appLockManager.verifyPinForSettings(pin)) {
                            PinVerifyResult.Success -> {
                                dialog.dismiss()
                                onVerified(pin)
                            }
                            PinVerifyResult.InvalidPin -> {
                                layoutPin.error = getString(R.string.app_lock_wrong_pin)
                            }
                            is PinVerifyResult.LockedOut -> {
                                layoutPin.error = formatLockout(result.remainingMillis)
                            }
                        }
                    }
                }
                dialog.setOnCancelListener { onCancel() }
                dialog.show()
            }
    }

    private fun bindTimeout(timeout: AppLockTimeout) {
        suppressTimeoutSelection = true
        binding.radioLockTimeout.check(
            when (timeout) {
                AppLockTimeout.IMMEDIATE -> R.id.radioTimeoutImmediate
                AppLockTimeout.SECONDS_30 -> R.id.radioTimeout30Sec
                AppLockTimeout.MINUTE_1 -> R.id.radioTimeout1Min
                AppLockTimeout.MINUTES_5 -> R.id.radioTimeout5Min
                AppLockTimeout.MINUTES_15 -> R.id.radioTimeout15Min
            }
        )
        suppressTimeoutSelection = false
    }

    private fun timeoutFor(checkedId: Int): AppLockTimeout? = when (checkedId) {
        R.id.radioTimeoutImmediate -> AppLockTimeout.IMMEDIATE
        R.id.radioTimeout30Sec -> AppLockTimeout.SECONDS_30
        R.id.radioTimeout1Min -> AppLockTimeout.MINUTE_1
        R.id.radioTimeout5Min -> AppLockTimeout.MINUTES_5
        R.id.radioTimeout15Min -> AppLockTimeout.MINUTES_15
        else -> null
    }

    private fun canUseBiometric(): Boolean {
        val status = BiometricManager.from(requireContext()).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showMessage(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showRecoveryCodeDialog(code: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.app_lock_recovery_code_title)
            .setMessage(
                getString(R.string.app_lock_recovery_code_warning) + "\n\n" +
                    getString(R.string.app_lock_recovery_code_message, code)
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun formatLockout(remainingMillis: Long): String {
        val seconds = ((remainingMillis + 999) / 1000).toInt()
        return getString(R.string.app_lock_locked_out, seconds)
    }

    companion object {
        private const val KEY_ACCESS_GRANTED = "security_settings_access_granted"
    }
}
