package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.repository.DeleteTemplateResult
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

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.importTemplateJson(requireContext(), uri) { success ->
            val message = if (success) R.string.template_imported else R.string.template_import_failed
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

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
        setupOptionsMenu()

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
            },
            onDelete = { template -> confirmDelete(template) }
        )
        binding.recyclerTemplates.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTemplates.adapter = adapter

        viewModel.templates.observe(viewLifecycleOwner) { templates ->
            adapter.canDeleteTemplates = templates.size > 1
            adapter.submitList(templates)
            binding.textEmptyTemplates.visibility =
                if (templates.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabNewTemplate.setOnClickListener { showNewTemplateDialog() }
        binding.buttonImportTemplate.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }

    private fun setupOptionsMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_list_screen_options, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_list_options) {
                    binding.drawerLayout.openDrawer(GravityCompat.END)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun confirmDelete(template: InvoiceTemplate) {
        if (template.isDefault) {
            Toast.makeText(requireContext(), R.string.delete_template_default_error, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_template_title)
            .setMessage(getString(R.string.delete_template_confirm, template.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTemplate(template.id, requireContext().applicationContext) { result ->
                    val messageRes = when (result) {
                        DeleteTemplateResult.SUCCESS -> R.string.template_deleted
                        DeleteTemplateResult.IS_DEFAULT -> R.string.delete_template_default_error
                        DeleteTemplateResult.LAST_TEMPLATE -> R.string.delete_template_last_error
                        DeleteTemplateResult.NOT_FOUND -> R.string.delete_template_not_found
                    }
                    Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
