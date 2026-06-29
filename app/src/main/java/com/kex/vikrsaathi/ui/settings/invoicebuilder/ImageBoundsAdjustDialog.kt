package com.kex.vikrsaathi.ui.settings.invoicebuilder

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.domain.template.TemplateImageBoundsHelper

object ImageBoundsAdjustDialog {

    fun confirmElementBoundsIfNeeded(
        fragment: Fragment,
        element: TemplateElement,
        imageWidth: Int,
        imageHeight: Int,
        onApply: (TemplateElement, Boolean) -> Unit
    ) {
        if (element.kind != ElementKind.IMAGE) {
            onApply(element, false)
            return
        }
        val suggested = TemplateImageBoundsHelper.suggestedBoundsForImage(
            bounds = element.bounds,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            scaleMode = element.style.imageScaleMode
        )
        if (suggested == null) {
            onApply(element, false)
            return
        }
        showElementDialog(fragment, suggested) { adjust ->
            if (adjust) {
                onApply(element.copy(bounds = suggested), true)
            } else {
                onApply(element, false)
            }
        }
    }

    fun confirmBulkImageBoundsIfNeeded(
        fragment: Fragment,
        adjustmentCount: Int,
        sampleBounds: ElementBounds?,
        onConfirm: (Boolean) -> Unit
    ) {
        if (adjustmentCount <= 0) {
            onConfirm(false)
            return
        }
        val message = if (sampleBounds != null) {
            fragment.getString(
                R.string.image_bounds_adjust_bulk_detail,
                adjustmentCount,
                sampleBounds.width,
                sampleBounds.height
            )
        } else {
            fragment.getString(R.string.image_bounds_adjust_bulk_message, adjustmentCount)
        }
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.image_bounds_adjust_title)
            .setMessage(message)
            .setPositiveButton(R.string.adjust_layout) { _, _ -> onConfirm(true) }
            .setNegativeButton(R.string.keep_layout) { _, _ -> onConfirm(false) }
            .show()
    }

    fun confirmSettingsSyncIfNeeded(
        fragment: Fragment,
        affectedTemplateCount: Int,
        sampleBounds: ElementBounds?,
        onConfirm: (Boolean) -> Unit
    ) {
        if (affectedTemplateCount <= 0) {
            onConfirm(false)
            return
        }
        val message = if (sampleBounds != null) {
            fragment.getString(
                R.string.image_bounds_adjust_settings_detail,
                affectedTemplateCount,
                sampleBounds.width,
                sampleBounds.height
            )
        } else {
            fragment.getString(R.string.image_bounds_adjust_settings_message, affectedTemplateCount)
        }
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.image_bounds_adjust_title)
            .setMessage(message)
            .setPositiveButton(R.string.adjust_layout) { _, _ -> onConfirm(true) }
            .setNegativeButton(R.string.keep_layout) { _, _ -> onConfirm(false) }
            .show()
    }

    private fun showElementDialog(
        fragment: Fragment,
        suggested: ElementBounds,
        onChoice: (adjust: Boolean) -> Unit
    ) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.image_bounds_adjust_title)
            .setMessage(
                fragment.getString(
                    R.string.image_bounds_adjust_message,
                    suggested.width,
                    suggested.height
                )
            )
            .setPositiveButton(R.string.adjust_layout) { _, _ -> onChoice(true) }
            .setNegativeButton(R.string.keep_layout) { _, _ -> onChoice(false) }
            .show()
    }
}
