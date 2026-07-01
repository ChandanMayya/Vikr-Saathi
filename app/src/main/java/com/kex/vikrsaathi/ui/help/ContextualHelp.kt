package com.kex.vikrsaathi.ui.help

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

fun Fragment.showContextualHelp(topic: HelpTopic) {
    HelpOverlay.showTopic(requireActivity(), topic)
}

fun View.bindContextualHelp(topic: HelpTopic) {
    setOnClickListener {
        val activity = context as? FragmentActivity ?: return@setOnClickListener
        HelpOverlay.showTopic(activity, topic)
    }
}
