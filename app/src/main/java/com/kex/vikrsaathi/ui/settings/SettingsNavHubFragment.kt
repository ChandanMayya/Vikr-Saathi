package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.kex.vikrsaathi.databinding.FragmentSettingsNavHubBinding

open class SettingsNavHubFragment : Fragment() {

    private var _binding: FragmentSettingsNavHubBinding? = null
    protected val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsNavHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    protected fun bindNavHub(
        entries: List<SettingsNavEntry>,
        subtitle: String? = null,
        footer: String? = null,
        showLogout: Boolean = false,
        onLogout: (() -> Unit)? = null
    ) {
        binding.recyclerSettingsNav.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSettingsNav.adapter = SettingsNavAdapter(entries)
        binding.textHubTitle.isVisible = !subtitle.isNullOrBlank()
        binding.textHubTitle.text = subtitle.orEmpty()
        binding.textHubFooter.isVisible = !footer.isNullOrBlank()
        binding.textHubFooter.text = footer.orEmpty()
        binding.buttonLogout.isVisible = showLogout
        binding.buttonLogout.setOnClickListener { onLogout?.invoke() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
