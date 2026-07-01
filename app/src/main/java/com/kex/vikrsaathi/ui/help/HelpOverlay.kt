package com.kex.vikrsaathi.ui.help

import android.app.Dialog
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.databinding.BottomSheetHelpBinding
import com.kex.vikrsaathi.databinding.ItemHelpSectionBinding

object HelpOverlay {

    private var helpDialog: Dialog? = null
    private var helpBinding: BottomSheetHelpBinding? = null
    private var backCallback: OnBackPressedCallback? = null

    fun isShowing(@Suppress("UNUSED_PARAMETER") activity: FragmentActivity): Boolean {
        return helpDialog?.isShowing == true
    }

    fun show(activity: FragmentActivity, screen: HelpScreen) {
        showGuide(activity, HelpContentProvider.get(activity, screen))
    }

    fun showTopic(activity: FragmentActivity, topic: HelpTopic) {
        showGuide(activity, HelpContentProvider.getTopic(activity, topic))
    }

    private fun showGuide(activity: FragmentActivity, guide: HelpGuide) {
        dismiss(activity, animate = false)

        val binding = BottomSheetHelpBinding.inflate(LayoutInflater.from(activity))
        helpBinding = binding

        binding.textHelpTitle.text = guide.title
        binding.textHelpOverview.text = guide.overview
        binding.layoutHelpSections.removeAllViews()

        val inflater = LayoutInflater.from(activity)
        guide.sections.forEach { section ->
            val sectionBinding = ItemHelpSectionBinding.inflate(inflater, binding.layoutHelpSections, false)
            sectionBinding.textSectionTitle.text = section.title
            sectionBinding.textSectionBody.text = section.items.joinToString("\n") { "• $it" }
            binding.layoutHelpSections.addView(sectionBinding.root)
        }

        binding.buttonCloseHelp.setOnClickListener { dismiss(activity) }
        binding.helpRoot.setOnClickListener { dismiss(activity) }
        binding.helpPanelContainer.setOnClickListener { /* keep panel open */ }

        val dialog = Dialog(activity, R.style.Theme_VikrSaathi_HelpOverlay)
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.setOnCancelListener { dismiss(activity) }
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.apply {
                    blurBehindRadius = 48
                }
            }
        }

        configurePanelScroll(activity, binding)
        registerBackHandler(activity)

        binding.helpPanelShell.post {
            setupBackdropBlur(activity, binding.blurHelpBackdrop)
            setupPanelBlur(activity, binding)
        }

        helpDialog = dialog
        dialog.show()
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(180L).start()
    }

    fun dismissIfShowing(activity: FragmentActivity): Boolean {
        if (!isShowing(activity)) return false
        dismiss(activity)
        return true
    }

    fun dismiss(activity: FragmentActivity, animate: Boolean = true) {
        backCallback?.remove()
        backCallback = null

        val dialog = helpDialog ?: return
        if (!dialog.isShowing) {
            helpDialog = null
            helpBinding = null
            return
        }

        val binding = helpBinding
        if (!animate || binding == null) {
            dialog.dismiss()
            helpDialog = null
            helpBinding = null
            return
        }

        binding.root.animate()
            .alpha(0f)
            .setDuration(140L)
            .withEndAction {
                dialog.dismiss()
                helpDialog = null
                helpBinding = null
            }
            .start()
    }

    private fun configurePanelScroll(activity: FragmentActivity, binding: BottomSheetHelpBinding) {
        val resources = activity.resources
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels
        val maxPanelFraction = resources.getFloat(R.dimen.help_panel_max_height_fraction)
        val minScrollHeight = resources.getDimensionPixelSize(R.dimen.help_panel_min_scroll_height)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.helpPanelContainer.setPadding(
                binding.helpPanelContainer.paddingLeft,
                binding.helpPanelContainer.paddingTop,
                binding.helpPanelContainer.paddingRight,
                (28 * density).toInt() + navBar
            )
            insets
        }

        binding.helpPanelShell.post {
            val insets = ViewCompat.getRootWindowInsets(binding.root)
            val navBar = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val containerPadding = binding.helpPanelContainer.paddingTop +
                binding.helpPanelContainer.paddingBottom
            val maxPanelHeight = ((screenHeight - navBar) * maxPanelFraction).toInt() - containerPadding

            val headerHeight = binding.helpHeader.height
            val chromePadding = ((20 + 14) * density).toInt()
            val scrollMaxHeight = (maxPanelHeight - headerHeight - chromePadding)
                .coerceAtLeast(minScrollHeight)
            binding.scrollHelpContent.layoutParams = binding.scrollHelpContent.layoutParams.apply {
                height = scrollMaxHeight
            }
            binding.scrollHelpContent.requestLayout()
        }
    }

    private fun registerBackHandler(activity: FragmentActivity) {
        backCallback?.remove()
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss(activity)
            }
        }.also { activity.onBackPressedDispatcher.addCallback(activity, it) }
    }

    private fun setupBackdropBlur(activity: FragmentActivity, blurView: BlurView) {
        blurView.setBackgroundColor(Color.TRANSPARENT)
        bindBlur(
            activity = activity,
            blurView = blurView,
            blurRadiusRes = R.dimen.help_backdrop_blur_radius
        )
    }

    private fun setupPanelBlur(activity: FragmentActivity, binding: BottomSheetHelpBinding) {
        val cornerRadius = activity.resources.getDimension(R.dimen.help_glass_corner_radius)
        val roundedOutline = roundedOutlineProvider(cornerRadius)

        binding.blurHelpPanel.apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipToOutline = false
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        binding.helpGlassContent.apply {
            clipToOutline = true
            outlineProvider = roundedOutline
        }
        binding.helpPanelShell.apply {
            clipToOutline = true
            outlineProvider = roundedOutline
        }

        bindBlur(
            activity = activity,
            blurView = binding.blurHelpPanel,
            blurRadiusRes = R.dimen.help_glass_blur_radius
        )
    }

    private fun bindBlur(activity: FragmentActivity, blurView: BlurView, blurRadiusRes: Int) {
        val blurTarget = activity.findViewById<BlurTarget>(R.id.blurTarget) ?: return
        val windowBackground = activity.window.decorView.background

        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(activity.resources.getDimension(blurRadiusRes))
            .setBlurAutoUpdate(true)
    }

    private fun roundedOutlineProvider(cornerRadius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }
    }
}
