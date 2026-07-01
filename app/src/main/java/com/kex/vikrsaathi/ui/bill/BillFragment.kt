package com.kex.vikrsaathi.ui.bill

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.DialogCustomerFormBinding
import com.kex.vikrsaathi.databinding.FragmentBillBinding
import com.kex.vikrsaathi.ui.scanner.BarcodeScannerActivity
import com.kex.vikrsaathi.ui.scanner.BarcodeScanBus
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory

class BillFragment : Fragment() {

    private var _binding: FragmentBillBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var lineAdapter: BillLineItemAdapter
    private var customerSuggestions: List<Customer> = emptyList()
    private var itemSuggestions: List<Item> = emptyList()
    private var suppressCustomerSearch = false
    private var suppressItemSearch = false
    private var isReadOnly = false

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val barcode = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE)
        if (!barcode.isNullOrBlank()) {
            handleBarcodeResult(barcode)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        val settings = app.settingsRepository

        isReadOnly = arguments?.getBoolean(ARG_READ_ONLY, false) == true

        binding.imageHeader.setImageBitmap(settings.getHeaderImage())
        binding.imageSignature.setImageBitmap(settings.getSignatureImage())

        lineAdapter = BillLineItemAdapter(
            currencySymbol = viewModel.currencySymbol,
            onQuantityChange = { index, qty -> viewModel.updateLineQuantity(index, qty) },
            onDiscountChange = { index, discount -> viewModel.updateLineDiscount(index, discount) },
            onRemove = { index -> viewModel.removeLine(index) }
        )
        binding.recyclerBillItems.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBillItems.adapter = lineAdapter

        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        val heldDraftId = arguments?.getLong(ARG_HELD_DRAFT_ID, -1L) ?: -1L
        if (billId > 0) {
            viewModel.loadBill(billId)
        } else if (heldDraftId > 0) {
            resumeHeldBill(heldDraftId)
        } else if (savedInstanceState == null) {
            viewModel.clearBill()
        }

        setupCustomerAutocomplete()
        setupItemAutocomplete()
        setupDrawer()
        setupToolbarMenu()
        setupBackNavigation()
        applyReadOnlyState()

        viewModel.selectedCustomer.observe(viewLifecycleOwner) { customer ->
            suppressCustomerSearch = true
            if (customer != null) {
                binding.autoCompleteCustomer.setText(customer.name, false)
                if (binding.editBuyerAddress.text.isNullOrBlank()) {
                    binding.editBuyerAddress.setText(customer.formattedAddress())
                }
                if (binding.editBuyerPhone.text.isNullOrBlank()) {
                    binding.editBuyerPhone.setText(customer.phone)
                }
            }
            binding.autoCompleteCustomer.dismissDropDown()
            binding.autoCompleteCustomer.post { suppressCustomerSearch = false }
        }
        viewModel.lineItems.observe(viewLifecycleOwner) { lineAdapter.submitList(it) }
        viewModel.grandTotal.observe(viewLifecycleOwner) { total ->
            binding.textGrandTotal.text = getString(
                R.string.grand_total,
                PriceCalculator.formatAmount(total, viewModel.currencySymbol)
            )
        }
        viewModel.totalDiscount.observe(viewLifecycleOwner) { discount ->
            binding.textTotalDiscount.text = getString(
                R.string.total_discount,
                PriceCalculator.formatAmount(discount, viewModel.currencySymbol)
            )
        }
        viewModel.totalInWords.observe(viewLifecycleOwner) { words ->
            binding.textTotalInWords.text = getString(R.string.total_in_words, words)
        }

        binding.buttonAddNewCustomer.setOnClickListener { showAddCustomerDialog() }
        binding.buttonScanBarcode.setOnClickListener {
            BarcodeScanBus.onBarcodeScanned = { barcode -> handleBarcodeResult(barcode) }
            scannerLauncher.launch(
                Intent(requireContext(), BarcodeScannerActivity::class.java).apply {
                    putExtra(BarcodeScannerActivity.EXTRA_CONTINUOUS, true)
                }
            )
        }
        binding.buttonSaveBill.setOnClickListener { saveBill() }

        binding.buttonViewBillDownloadPdf.setOnClickListener { exportBillPdf(print = false) }
        binding.buttonViewBillPrint.setOnClickListener { exportBillPdf(print = true) }
        binding.billDrawer.buttonDrawerDownloadPdf.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            exportBillPdf(print = false)
        }
        binding.billDrawer.buttonDrawerPrintBill.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            exportBillPdf(print = true)
        }
    }

    private fun setupToolbarMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                when {
                    isReadOnly -> menuInflater.inflate(R.menu.menu_bill_view, menu)
                    isNewBillDrawerEnabled() -> menuInflater.inflate(R.menu.menu_bill_edit, menu)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_bill_options -> {
                        binding.drawerLayout.openDrawer(GravityCompat.END)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun isNewBillDrawerEnabled(): Boolean {
        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        return !isReadOnly && billId <= 0 && viewModel.isNewBillSession
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!handleLeaveNewBill()) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
    }

    private fun handleLeaveNewBill(): Boolean {
        if (!isNewBillDrawerEnabled()) return false
        if (!viewModel.hasUnsavedNewBillContent(
                binding.autoCompleteCustomer.text.toString().trim(),
                binding.editBuyerAddress.text.toString().trim(),
                binding.editBuyerPhone.text.toString().trim()
            )
        ) {
            return false
        }
        showLeaveNewBillDialog(navigateAwayOnComplete = true)
        return true
    }

    private fun showLeaveNewBillDialog(navigateAwayOnComplete: Boolean) {
        val canHold = viewModel.canHoldCurrentBill()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.leave_new_bill_title)
            .setMessage(R.string.leave_new_bill_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.discard_bill) { _, _ ->
                viewModel.clearBill()
                if (navigateAwayOnComplete) {
                    findNavController().navigateUp()
                }
            }

        if (canHold) {
            dialog.setNeutralButton(R.string.hold_bill) { _, _ ->
                holdCurrentBill(
                    navigateAwayOnComplete = navigateAwayOnComplete,
                    closeDrawer = false
                )
            }
        }
        dialog.show()
    }

    private fun holdCurrentBill(
        navigateAwayOnComplete: Boolean = false,
        closeDrawer: Boolean = true
    ) {
        if (!isNewBillDrawerEnabled()) return
        binding.root.clearFocus()
        viewModel.holdBill(
            buyerName = binding.autoCompleteCustomer.text.toString().trim(),
            buyerAddress = binding.editBuyerAddress.text.toString().trim(),
            buyerPhone = binding.editBuyerPhone.text.toString().trim(),
            onHeld = { summary ->
                if (closeDrawer) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                }
                binding.autoCompleteCustomer.setText("", false)
                binding.editBuyerAddress.setText("")
                binding.editBuyerPhone.setText("")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.hold_bill_success, summary.customerName, summary.itemCount),
                    Toast.LENGTH_LONG
                ).show()
                if (navigateAwayOnComplete) {
                    findNavController().navigateUp()
                }
            },
            onEmpty = {
                Toast.makeText(requireContext(), R.string.hold_bill_empty, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun resumeHeldBill(draftId: Long) {
        viewModel.resumeHeldBill(
            draftId = draftId,
            onRestored = { restored ->
                suppressCustomerSearch = true
                binding.autoCompleteCustomer.setText(restored.customerName, false)
                binding.editBuyerAddress.setText(restored.buyerAddress)
                binding.editBuyerPhone.setText(restored.buyerPhone)
                binding.autoCompleteCustomer.post { suppressCustomerSearch = false }
                requireActivity().invalidateOptionsMenu()
                updateTitle()
            },
            onMissing = {
                Toast.makeText(requireContext(), R.string.held_bill_missing, Toast.LENGTH_SHORT).show()
                viewModel.clearBill()
            }
        )
    }

    private fun openHeldBills() {
        binding.drawerLayout.closeDrawer(GravityCompat.END)
        findNavController().navigate(R.id.action_bill_to_held_bills)
    }

    private fun setupDrawer() {
        binding.billDrawer.buttonHoldBill.setOnClickListener {
            holdCurrentBill(closeDrawer = true)
        }
        binding.billDrawer.buttonRestoreBill.setOnClickListener {
            openHeldBills()
        }
        binding.billDrawer.buttonEnableEditBill.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            isReadOnly = false
            applyReadOnlyState()
            requireActivity().invalidateOptionsMenu()
        }
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                if (drawerView.id == binding.billDrawer.root.id && isReadOnly) {
                    requireActivity().invalidateOptionsMenu()
                }
            }
        })
    }

    private fun applyReadOnlyState() {
        lineAdapter.readOnly = isReadOnly
        binding.autoCompleteCustomer.isEnabled = !isReadOnly
        binding.editBuyerAddress.isEnabled = !isReadOnly
        binding.editBuyerPhone.isEnabled = !isReadOnly
        binding.buttonAddNewCustomer.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        binding.layoutAddItem.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        binding.buttonSaveBill.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        binding.layoutViewBillActions.visibility = if (isReadOnly) View.VISIBLE else View.GONE
        updateDrawerVisibility()
        updateTitle()
    }

    private fun updateDrawerVisibility() {
        val showNewBillOptions = isNewBillDrawerEnabled()
        binding.billDrawer.layoutNewBillOptions.visibility =
            if (showNewBillOptions) View.VISIBLE else View.GONE
        binding.billDrawer.buttonEnableEditBill.visibility =
            if (isReadOnly) View.VISIBLE else View.GONE
        binding.billDrawer.layoutViewBillOptions.visibility =
            if (isReadOnly) View.VISIBLE else View.GONE
    }

    private fun updateTitle() {
        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = when {
            isReadOnly -> getString(R.string.view_bill)
            billId > 0 || viewModel.currentBillId != null -> getString(R.string.edit_bill)
            else -> getString(R.string.new_bill)
        }
    }

    private fun setupCustomerAutocomplete() {
        binding.autoCompleteCustomer.threshold = 1

        binding.autoCompleteCustomer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressCustomerSearch || isReadOnly) return
                val query = s?.toString().orEmpty().trim()
                if (query.isEmpty()) {
                    binding.autoCompleteCustomer.dismissDropDown()
                    return
                }
                viewModel.searchCustomers(query) { results ->
                    if (suppressCustomerSearch || !binding.autoCompleteCustomer.isAttachedToWindow) return@searchCustomers
                    customerSuggestions = results
                    val names = results.map { it.name }.toMutableList()
                    names.add(getString(R.string.add_new_customer))
                    binding.autoCompleteCustomer.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                    )
                    if (binding.autoCompleteCustomer.hasFocus() && !suppressCustomerSearch) {
                        binding.autoCompleteCustomer.showDropDown()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.autoCompleteCustomer.setOnItemClickListener { _, _, position, _ ->
            if (isReadOnly) return@setOnItemClickListener
            suppressCustomerSearch = true
            binding.autoCompleteCustomer.dismissDropDown()

            val names = customerSuggestions.map { it.name }.toMutableList()
            names.add(getString(R.string.add_new_customer))
            val selected = names.getOrNull(position) ?: run {
                suppressCustomerSearch = false
                return@setOnItemClickListener
            }

            if (selected == getString(R.string.add_new_customer)) {
                binding.autoCompleteCustomer.setText("", false)
                showAddCustomerDialog()
            } else {
                val customer = customerSuggestions.find { it.name == selected }
                viewModel.setCustomer(customer)
                binding.autoCompleteCustomer.clearFocus()
            }
            binding.autoCompleteCustomer.post { suppressCustomerSearch = false }
        }

        binding.autoCompleteCustomer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) binding.autoCompleteCustomer.dismissDropDown()
        }
    }

    private fun setupItemAutocomplete() {
        binding.autoCompleteItem.threshold = 1

        binding.autoCompleteItem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressItemSearch || isReadOnly) return
                val query = s?.toString().orEmpty().trim()
                if (query.isEmpty()) {
                    binding.autoCompleteItem.dismissDropDown()
                    return
                }
                viewModel.searchItems(query) { results ->
                    if (suppressItemSearch || !binding.autoCompleteItem.isAttachedToWindow) return@searchItems
                    itemSuggestions = results
                    binding.autoCompleteItem.setAdapter(
                        ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            results.map { it.name }
                        )
                    )
                    if (binding.autoCompleteItem.hasFocus() && !suppressItemSearch) {
                        binding.autoCompleteItem.showDropDown()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.autoCompleteItem.setOnItemClickListener { _, _, position, _ ->
            if (isReadOnly) return@setOnItemClickListener
            suppressItemSearch = true
            binding.autoCompleteItem.dismissDropDown()

            val item = itemSuggestions.getOrNull(position) ?: run {
                suppressItemSearch = false
                return@setOnItemClickListener
            }
            viewModel.addItemFromMaster(item)
            binding.autoCompleteItem.setText("", false)
            binding.autoCompleteItem.clearFocus()
            binding.autoCompleteItem.post { suppressItemSearch = false }
        }

        binding.autoCompleteItem.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) binding.autoCompleteItem.dismissDropDown()
        }
    }

    private fun showAddCustomerDialog() {
        if (isReadOnly) return
        val formBinding = DialogCustomerFormBinding.inflate(layoutInflater)
        formBinding.editCustomerName.setText(binding.autoCompleteCustomer.text)
        formBinding.editCustomerAddress.setText(binding.editBuyerAddress.text)
        formBinding.editCustomerPhone.setText(binding.editBuyerPhone.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_new_customer)
            .setView(formBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = formBinding.editCustomerName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val customer = Customer(
                    name = name,
                    address1 = formBinding.editCustomerAddress.text.toString().trim(),
                    phone = formBinding.editCustomerPhone.text.toString().trim()
                )
                viewModel.saveCustomer(customer) {
                    Toast.makeText(requireContext(), R.string.customer_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleBarcodeResult(barcode: String) {
        if (isReadOnly) return
        viewModel.findItemByBarcode(barcode) { item ->
            if (item == null) {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.item_not_found_add_new)
                    .setPositiveButton(R.string.add_item) { _, _ ->
                        findNavController().navigate(
                            R.id.action_bill_to_items,
                            bundleOf("barcode" to barcode)
                        )
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                viewModel.addItemFromMaster(item)
            }
        }
    }

    private fun saveBill() {
        if (isReadOnly) return
        binding.root.clearFocus()
        if (viewModel.lineItems.value.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.add_items_first, Toast.LENGTH_SHORT).show()
            return
        }
        val isNewBillSave = viewModel.isNewBillSession
        viewModel.saveBill { id ->
            if (isNewBillSave) {
                findNavController().navigate(
                    R.id.action_bill_to_preview,
                    bundleOf(BillPreviewFragment.ARG_BILL_ID to id)
                )
            } else {
                updateTitle()
                requireActivity().invalidateOptionsMenu()
                Toast.makeText(requireContext(), R.string.bill_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportBillPdf(print: Boolean) {
        if (!isReadOnly) return
        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        if (billId <= 0) return

        setViewBillPdfActionsEnabled(false)
        viewModel.exportBillPdf(requireContext(), billId) { file ->
            setViewBillPdfActionsEnabled(true)
            if (file == null) {
                Toast.makeText(requireContext(), R.string.pdf_generation_failed, Toast.LENGTH_SHORT).show()
                return@exportBillPdf
            }
            if (print) {
                FileShareHelper.shareFile(
                    requireContext(),
                    file,
                    "application/pdf",
                    getString(R.string.print)
                )
            } else {
                try {
                    FileShareHelper.openFile(requireContext(), file, "application/pdf")
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), R.string.pdf_saved, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setViewBillPdfActionsEnabled(enabled: Boolean) {
        binding.buttonViewBillDownloadPdf.isEnabled = enabled
        binding.buttonViewBillPrint.isEnabled = enabled
        binding.billDrawer.buttonDrawerDownloadPdf.isEnabled = enabled
        binding.billDrawer.buttonDrawerPrintBill.isEnabled = enabled
    }

    override fun onDestroyView() {
        BarcodeScanBus.onBarcodeScanned = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
        const val ARG_READ_ONLY = "readOnly"
        const val ARG_HELD_DRAFT_ID = "heldDraftId"
    }
}
