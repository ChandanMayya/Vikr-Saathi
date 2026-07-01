package com.kex.vikrsaathi.ui.help

import android.graphics.Color
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.databinding.BottomSheetHelpBinding
import com.kex.vikrsaathi.databinding.ItemHelpSectionBinding

object HelpOverlay {

    private var backCallback: OnBackPressedCallback? = null

    fun isShowing(activity: FragmentActivity): Boolean {
        return activity.findViewById<FrameLayout>(R.id.helpOverlayHost)?.isVisible == true
    }

    fun show(activity: FragmentActivity, screen: HelpScreen) {
        val host = activity.findViewById<FrameLayout>(R.id.helpOverlayHost) ?: return
        host.removeAllViews()

        val binding = BottomSheetHelpBinding.inflate(LayoutInflater.from(activity), host, false)
        val guide = HelpContentProvider.get(activity, screen)

        binding.textHelpTitle.text = guide.title
        binding.textHelpOverview.text = guide.overview

        val inflater = LayoutInflater.from(activity)
        guide.sections.forEach { section ->
            val sectionBinding = ItemHelpSectionBinding.inflate(inflater, binding.layoutHelpSections, false)
            sectionBinding.textSectionTitle.text = section.title
            sectionBinding.textSectionBody.text = section.items.joinToString("\n") { "• $it" }
            binding.layoutHelpSections.addView(sectionBinding.root)
        }

        binding.buttonCloseHelp.setOnClickListener { dismiss(activity) }
        binding.helpRoot.setOnClickListener { dismiss(activity) }

        host.addView(binding.root)
        host.isVisible = true
        host.alpha = 0f
        host.animate().alpha(1f).setDuration(180L).start()

        registerBackHandler(activity)

        ViewCompat.setOnApplyWindowInsetsListener(binding.helpPanelContainer) { _, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.helpPanelContainer.setPadding(
                binding.helpPanelContainer.paddingLeft,
                binding.helpPanelContainer.paddingTop,
                binding.helpPanelContainer.paddingRight,
                (28 * activity.resources.displayMetrics.density).toInt() + navBar
            )
            insets
        }

        binding.helpPanelShell.post {
            setupBackdropBlur(activity, binding.blurHelpBackdrop)
            setupPanelBlur(activity, binding)
        }
    }

    fun dismissIfShowing(activity: FragmentActivity): Boolean {
        if (!isShowing(activity)) return false
        dismiss(activity)
        return true
    }

    fun dismiss(activity: FragmentActivity) {
        backCallback?.remove()
        backCallback = null

        val host = activity.findViewById<FrameLayout>(R.id.helpOverlayHost) ?: return
        if (!host.isVisible) return

        host.animate()
            .alpha(0f)
            .setDuration(140L)
            .withEndAction {
                host.removeAllViews()
                host.isVisible = false
                host.alpha = 1f
            }
            .start()
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
