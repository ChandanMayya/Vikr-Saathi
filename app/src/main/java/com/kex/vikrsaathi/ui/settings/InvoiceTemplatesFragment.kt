package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentInvoiceTemplatesBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.ui.settings.invoicebuilder.InvoiceBuilderFragment
import com.kex.vikrsaathi.util.ViewModelFactory

class InvoiceTemplatesFragment : Fragment() {

    private var _binding: FragmentInvoiceTemplatesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InvoiceTemplatesViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: InvoiceTemplateAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.INVOICE_TEMPLATES)

        adapter = InvoiceTemplateAdapter(
            onSetDefault = { template ->
                if (template.isDefault) return@InvoiceTemplateAdapter
                viewModel.setAsDefault(template.id) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.template_set_default, template.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onEdit = { template ->
                findNavController().navigate(
                    R.id.action_invoice_templates_to_builder,
                    bundleOf(InvoiceBuilderFragment.ARG_TEMPLATE_ID to template.id)
                )
            }
        )
        binding.recyclerTemplates.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTemplates.adapter = adapter

        viewModel.templates.observe(viewLifecycleOwner) { templates ->
            adapter.submitList(templates)
            binding.textEmptyTemplates.visibility =
                if (templates.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabNewTemplate.setOnClickListener { showNewTemplateDialog() }
    }

    private fun showNewTemplateDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = getString(R.string.template_name_hint)
            setText(getString(R.string.new_template_default_name))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_invoice_template)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim().ifEmpty {
                    getString(R.string.new_template_default_name)
                }
                viewModel.createNewTemplate(name) { id ->
                    findNavController().navigate(
                        R.id.action_invoice_templates_to_builder,
                        bundleOf(InvoiceBuilderFragment.ARG_TEMPLATE_ID to id)
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
