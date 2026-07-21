package com.kex.vikrsaathi.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.databinding.DialogCustomerFormBinding
import com.kex.vikrsaathi.databinding.FragmentCustomersBinding
import com.kex.vikrsaathi.ui.common.applyListViewMode
import com.kex.vikrsaathi.ui.common.installListViewOptionsDrawer
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.ListViewScreen
import com.kex.vikrsaathi.util.ViewModelFactory

class CustomerFragment : Fragment() {

    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomerViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: CustomerAdapter
    private lateinit var listViewPrefs: com.kex.vikrsaathi.util.ListViewPreferences
    private var viewMode: ListViewMode = ListViewMode.COMFORTABLE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        listViewPrefs = app.listViewPreferences
        viewMode = listViewPrefs.getMode(ListViewScreen.CUSTOMERS)

        installHelpMenu(HelpScreen.CUSTOMERS)
        installListViewOptionsDrawer(
            drawerLayout = binding.drawerLayout,
            optionsTitleView = binding.optionsDrawer.textOptionsTitle,
            titleRes = R.string.customers_options,
            radioViewMode = binding.optionsDrawer.radioViewMode,
            currentMode = { viewMode },
            onModeSelected = ::applyViewMode
        )

        adapter = CustomerAdapter(
            onEdit = { showCustomerDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.recyclerCustomers.adapter = adapter
        applyViewMode(viewMode, persist = false)

        viewModel.customers.observe(viewLifecycleOwner) { adapter.submitList(it) }
        binding.fabAddCustomer.setOnClickListener { showCustomerDialog(null) }
    }

    private fun applyViewMode(mode: ListViewMode, persist: Boolean = true) {
        viewMode = mode
        if (persist) listViewPrefs.setMode(ListViewScreen.CUSTOMERS, mode)
        adapter.viewMode = mode
        binding.recyclerCustomers.applyListViewMode(mode)
        binding.detailsHeader.root.isVisible = mode == ListViewMode.DETAILS
    }

    private fun showCustomerDialog(existing: Customer?) {
        val formBinding = DialogCustomerFormBinding.inflate(layoutInflater)
        existing?.let {
            formBinding.editCustomerName.setText(it.name)
            formBinding.editCustomerAddress.setText(it.formattedAddress())
            formBinding.editCustomerPhone.setText(it.phone)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_customer else R.string.edit_customer)
            .setView(formBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = formBinding.editCustomerName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val customer = Customer(
                    id = existing?.id ?: 0,
                    name = name,
                    address1 = formBinding.editCustomerAddress.text.toString().trim(),
                    phone = formBinding.editCustomerPhone.text.toString().trim()
                )
                viewModel.saveCustomer(customer)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(customer: Customer) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_customer)
            .setMessage(R.string.delete_customer_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteCustomer(customer) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
