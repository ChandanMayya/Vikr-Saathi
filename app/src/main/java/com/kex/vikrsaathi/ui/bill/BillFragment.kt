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
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
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
import com.kex.vikrsaathi.databinding.FragmentBillBinding
import com.kex.vikrsaathi.ui.scanner.BarcodeScannerActivity
import com.kex.vikrsaathi.ui.scanner.BarcodeScanBus
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class BillFragment : Fragment() {

    private var _binding: FragmentBillBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var lineAdapter: BillLineItemAdapter
    private var customerSuggestions: List<Customer> = emptyList()
    private var itemSuggestions: List<Item> = emptyList()
    private var pendingNewItemBarcode: String? = null
    private val customerFlagCheck = Runnable { updateCustomerFlag() }
    private val newItemCheck = Runnable { updateNewItemSection() }
    private var suppressCustomerSearch = false
    private var suppressItemSearch = false
    private var suppressBillRoundOffUpdates = false
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
            onRoundOffChange = { index, roundOff -> viewModel.updateLineRoundOff(index, roundOff) },
            onRateChange = { index, rate -> viewModel.updateLineRate(index, rate) },
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
        setupNewItemSection()
        setupDrawer()
        setupToolbarMenu()
        installHelpMenu {
            if (isReadOnly) HelpScreen.BILL_VIEW else HelpScreen.NEW_BILL
        }
        setupBackNavigation()
        applyReadOnlyState()

        viewModel.selectedCustomer.observe(viewLifecycleOwner) { customer ->
            if (customer == null) {
                scheduleCustomerFlagCheck()
                return@observe
            }
            suppressCustomerSearch = true
            binding.autoCompleteCustomer.setText(customer.name, false)
            if (binding.editBuyerAddress.text.isNullOrBlank()) {
                binding.editBuyerAddress.setText(customer.formattedAddress())
            }
            if (binding.editBuyerPhone.text.isNullOrBlank()) {
                binding.editBuyerPhone.setText(customer.phone)
            }
            binding.layoutNewCustomerFlag.isVisible = false
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
        viewModel.lineRoundOffTotal.observe(viewLifecycleOwner) { lineRoundOff ->
            val visible = kotlin.math.abs(lineRoundOff) >= 0.005
            binding.textLineRoundOffTotal.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) {
                binding.textLineRoundOffTotal.text = getString(
                    R.string.line_round_off,
                    PriceCalculator.formatSignedAmount(lineRoundOff, viewModel.currencySymbol)
                )
            }
        }
        viewModel.subtotal.observe(viewLifecycleOwner) { subtotal ->
            val billRoundOff = viewModel.billRoundOff.value ?: 0.0
            val visible = kotlin.math.abs(billRoundOff) >= 0.005
            binding.textSubtotal.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) {
                binding.textSubtotal.text = getString(
                    R.string.subtotal,
                    PriceCalculator.formatAmount(subtotal, viewModel.currencySymbol)
                )
            }
        }
        viewModel.billRoundOff.observe(viewLifecycleOwner) { roundOff ->
            if (!isReadOnly) {
                if (!binding.editBillRoundOff.hasFocus()) {
                    suppressBillRoundOffUpdates = true
                    binding.editBillRoundOff.setText(String.format("%.2f", roundOff))
                    suppressBillRoundOffUpdates = false
                }
            } else {
                val visible = kotlin.math.abs(roundOff) >= 0.005
                binding.textBillRoundOffReadOnly.visibility = if (visible) View.VISIBLE else View.GONE
                if (visible) {
                    binding.textBillRoundOffReadOnly.text = getString(
                        R.string.bill_round_off_label,
                        PriceCalculator.formatSignedAmount(roundOff, viewModel.currencySymbol)
                    )
                }
            }
            viewModel.subtotal.value?.let { subtotal ->
                val visible = kotlin.math.abs(roundOff) >= 0.005
                binding.textSubtotal.visibility = if (visible) View.VISIBLE else View.GONE
                if (visible) {
                    binding.textSubtotal.text = getString(
                        R.string.subtotal,
                        PriceCalculator.formatAmount(subtotal, viewModel.currencySymbol)
                    )
                }
            }
        }
        viewModel.totalInWords.observe(viewLifecycleOwner) { words ->
            binding.textTotalInWords.text = getString(R.string.total_in_words, words)
        }

        binding.editBillRoundOff.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressBillRoundOffUpdates || isReadOnly) return
                val text = s?.toString()?.trim().orEmpty()
                val roundOff = if (text.isEmpty() || text == "-" || text == "+") {
                    0.0
                } else {
                    text.toDoubleOrNull() ?: return
                }
                if (roundOff != (viewModel.billRoundOff.value ?: 0.0)) {
                    viewModel.updateBillRoundOff(roundOff)
                }
            }
        })

        binding.editBillRoundOff.setOnFocusChangeListener { _, hasFocus ->
            if (suppressBillRoundOffUpdates || isReadOnly || hasFocus) return@setOnFocusChangeListener
            val roundOff = binding.editBillRoundOff.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            suppressBillRoundOffUpdates = true
            binding.editBillRoundOff.setText(String.format("%.2f", roundOff))
            binding.editBillRoundOff.setSelection(binding.editBillRoundOff.text?.length ?: 0)
            suppressBillRoundOffUpdates = false
        }

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
                scheduleCustomerFlagCheck()
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
        binding.layoutAddItem.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        binding.buttonSaveBill.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        binding.layoutBillRoundOff.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        binding.layoutViewBillActions.visibility = if (isReadOnly) View.VISIBLE else View.GONE
        if (isReadOnly) {
            binding.layoutNewCustomerFlag.isVisible = false
            hideNewItemSection(clearInputs = true)
        } else {
            scheduleCustomerFlagCheck()
        }
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
                val selected = viewModel.selectedCustomer.value
                if (selected != null && !selected.name.trim().equals(query, ignoreCase = true)) {
                    viewModel.setCustomer(null)
                }
                scheduleCustomerFlagCheck()
                if (query.isEmpty()) {
                    binding.autoCompleteCustomer.dismissDropDown()
                    return
                }
                viewModel.searchCustomers(query) { results ->
                    if (suppressCustomerSearch || !binding.autoCompleteCustomer.isAttachedToWindow) return@searchCustomers
                    customerSuggestions = results
                    if (results.isEmpty()) {
                        binding.autoCompleteCustomer.dismissDropDown()
                        return@searchCustomers
                    }
                    binding.autoCompleteCustomer.setAdapter(
                        ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            results.map { it.name }
                        )
                    )
                    if (binding.autoCompleteCustomer.hasFocus() && !suppressCustomerSearch) {
                        binding.autoCompleteCustomer.showDropDown()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.autoCompleteCustomer.setOnItemClickListener { parent, _, position, _ ->
            if (isReadOnly) return@setOnItemClickListener
            suppressCustomerSearch = true
            binding.autoCompleteCustomer.dismissDropDown()
            val selectedName = parent.getItemAtPosition(position) as? String
            val customer = customerSuggestions.find { it.name == selectedName }
            if (customer != null) {
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
                scheduleNewItemCheck()
                if (query.isEmpty()) {
                    binding.autoCompleteItem.dismissDropDown()
                    return
                }
                viewModel.searchItems(query) { results ->
                    if (suppressItemSearch || !binding.autoCompleteItem.isAttachedToWindow) return@searchItems
                    itemSuggestions = results
                    if (results.isEmpty()) {
                        binding.autoCompleteItem.dismissDropDown()
                        return@searchItems
                    }
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

        binding.autoCompleteItem.setOnItemClickListener { parent, _, position, _ ->
            if (isReadOnly) return@setOnItemClickListener
            suppressItemSearch = true
            binding.autoCompleteItem.dismissDropDown()
            val selectedName = parent.getItemAtPosition(position) as? String
            itemSuggestions.find { it.name == selectedName }?.let { viewModel.addItemFromMaster(it) }
            pendingNewItemBarcode = null
            binding.autoCompleteItem.setText("", false)
            hideNewItemSection(clearInputs = true)
            binding.autoCompleteItem.clearFocus()
            binding.autoCompleteItem.post { suppressItemSearch = false }
        }

        binding.autoCompleteItem.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.cardNewItem.isVisible) {
                binding.autoCompleteItem.dismissDropDown()
                binding.editNewItemRate.requestFocus()
                true
            } else {
                false
            }
        }

        binding.autoCompleteItem.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) binding.autoCompleteItem.dismissDropDown()
        }
    }

    private fun setupNewItemSection() {
        val refreshWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.inputNewItemRate.error = null
                binding.inputNewItemStock.error = null
                refreshAddNewItemButton()
            }
        }
        binding.editNewItemRate.addTextChangedListener(refreshWatcher)
        binding.editNewItemStock.addTextChangedListener(refreshWatcher)
        binding.inputNewItemRate.prefixText = viewModel.currencySymbol

        binding.editNewItemRate.setOnEditorActionListener { _, _, _ ->
            if (binding.inputNewItemStock.isVisible) {
                binding.editNewItemStock.requestFocus()
            } else {
                addNewItemFromSection()
            }
            true
        }
        binding.editNewItemStock.setOnEditorActionListener { _, _, _ ->
            addNewItemFromSection()
            true
        }
        binding.buttonAddNewItem.setOnClickListener { addNewItemFromSection() }
        binding.buttonClearNewItem.setOnClickListener { clearNewItemSection() }
    }

    private fun clearNewItemSection() {
        pendingNewItemBarcode = null
        binding.root.removeCallbacks(newItemCheck)
        suppressItemSearch = true
        binding.autoCompleteItem.setText("", false)
        binding.autoCompleteItem.post { suppressItemSearch = false }
        binding.root.clearFocus()
        hideNewItemSection(clearInputs = true)
    }

    private fun scheduleCustomerFlagCheck() {
        binding.root.removeCallbacks(customerFlagCheck)
        binding.root.postDelayed(customerFlagCheck, NAME_MATCH_DEBOUNCE_MS)
    }

    private fun updateCustomerFlag() {
        val b = _binding ?: return
        val name = b.autoCompleteCustomer.text.toString().trim()
        val selected = viewModel.selectedCustomer.value
        if (isReadOnly || name.isEmpty() ||
            (selected != null && selected.name.trim().equals(name, ignoreCase = true))
        ) {
            b.layoutNewCustomerFlag.isVisible = false
            return
        }
        viewModel.findCustomerByExactName(name) { match ->
            val current = _binding ?: return@findCustomerByExactName
            if (current.autoCompleteCustomer.text.toString().trim() != name) return@findCustomerByExactName
            current.layoutNewCustomerFlag.isVisible = match == null
        }
    }

    private fun scheduleNewItemCheck() {
        binding.root.removeCallbacks(newItemCheck)
        binding.root.postDelayed(newItemCheck, NAME_MATCH_DEBOUNCE_MS)
    }

    private fun updateNewItemSection() {
        val b = _binding ?: return
        if (isReadOnly) {
            hideNewItemSection(clearInputs = false)
            return
        }
        val name = b.autoCompleteItem.text.toString().trim()
        if (name.isEmpty()) {
            if (pendingNewItemBarcode != null) {
                showNewItemSection(name)
            } else {
                hideNewItemSection(clearInputs = true)
            }
            return
        }
        viewModel.findItemByExactName(name) { match ->
            val current = _binding ?: return@findItemByExactName
            if (current.autoCompleteItem.text.toString().trim() != name) return@findItemByExactName
            if (match == null) showNewItemSection(name) else hideNewItemSection(clearInputs = false)
        }
    }

    private fun showNewItemSection(name: String) {
        val barcode = pendingNewItemBarcode
        binding.textNewItemMessage.text = when {
            barcode != null && name.isEmpty() -> getString(R.string.new_item_barcode_message, barcode)
            barcode != null -> getString(R.string.new_item_barcode_named_message, name, barcode)
            else -> getString(R.string.new_item_message, name)
        }
        val mode = viewModel.inventoryMode
        binding.inputNewItemStock.isVisible = mode != InventoryMode.OFF
        if (mode == InventoryMode.BLOCK) {
            binding.inputNewItemStock.hint = getString(R.string.new_item_opening_stock_required)
            binding.inputNewItemStock.helperText = getString(R.string.new_item_stock_block_helper)
        } else {
            binding.inputNewItemStock.hint = getString(R.string.new_item_opening_stock)
            binding.inputNewItemStock.helperText = null
        }
        binding.cardNewItem.isVisible = true
        refreshAddNewItemButton()
    }

    private fun hideNewItemSection(clearInputs: Boolean) {
        val b = _binding ?: return
        b.cardNewItem.isVisible = false
        if (clearInputs) {
            b.editNewItemRate.setText("")
            b.editNewItemStock.setText("")
            b.inputNewItemRate.error = null
            b.inputNewItemStock.error = null
        }
    }

    private fun refreshAddNewItemButton() {
        binding.buttonAddNewItem.isEnabled = readNewItemInput(showErrors = false) != null
    }

    private data class NewItemInput(val name: String, val rate: Double, val openingStock: Int?)

    private fun readNewItemInput(showErrors: Boolean): NewItemInput? {
        val name = binding.autoCompleteItem.text.toString().trim()
        val rate = binding.editNewItemRate.text?.toString()?.trim()?.toDoubleOrNull()
        val stock = if (binding.inputNewItemStock.isVisible) {
            binding.editNewItemStock.text?.toString()?.trim()?.toIntOrNull()
        } else {
            null
        }
        var valid = true
        if (name.isEmpty()) {
            valid = false
            if (showErrors) {
                Toast.makeText(requireContext(), R.string.new_item_name_required, Toast.LENGTH_SHORT).show()
                binding.autoCompleteItem.requestFocus()
            }
        }
        if (rate == null || rate <= 0.0) {
            valid = false
            if (showErrors) binding.inputNewItemRate.error = getString(R.string.new_item_rate_invalid)
        }
        if (viewModel.inventoryMode == InventoryMode.BLOCK && (stock == null || stock < 1)) {
            valid = false
            if (showErrors) binding.inputNewItemStock.error = getString(R.string.new_item_stock_required)
        }
        return if (valid && rate != null) NewItemInput(name, rate, stock) else null
    }

    private fun addNewItemFromSection() {
        if (isReadOnly) return
        val input = readNewItemInput(showErrors = true) ?: return
        viewModel.addNewItem(
            name = input.name,
            rate = input.rate,
            openingStock = input.openingStock,
            barcode = pendingNewItemBarcode
        )
        clearNewItemSection()
    }

    private fun scrollToView(target: View) {
        binding.scrollBill.post {
            val offset = (16 * resources.displayMetrics.density).toInt()
            binding.scrollBill.smoothScrollTo(0, (target.top - offset).coerceAtLeast(0))
        }
    }

    private fun handleBarcodeResult(barcode: String) {
        if (isReadOnly) return
        viewModel.findItemByBarcode(barcode) { item ->
            if (_binding == null) return@findItemByBarcode
            if (item == null) {
                pendingNewItemBarcode = barcode
                updateNewItemSection()
                binding.autoCompleteItem.requestFocus()
                scrollToView(binding.layoutAddItem)
            } else {
                viewModel.addItemFromMaster(item)
            }
        }
    }

    private fun saveBill() {
        if (isReadOnly) return
        binding.root.clearFocus()
        if (binding.cardNewItem.isVisible) {
            scrollToView(binding.layoutAddItem)
            Toast.makeText(requireContext(), R.string.new_item_pending_on_save, Toast.LENGTH_LONG).show()
            return
        }
        val missingRateIndex = viewModel.firstNewLineWithoutRate()
        if (missingRateIndex >= 0) {
            val name = viewModel.lineItems.value?.getOrNull(missingRateIndex)?.name.orEmpty()
            scrollToView(binding.recyclerBillItems)
            Toast.makeText(
                requireContext(),
                getString(R.string.new_item_rate_missing_on_save, name),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (viewModel.lineItems.value.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.add_items_first, Toast.LENGTH_SHORT).show()
            return
        }
        val buyerName = binding.autoCompleteCustomer.text.toString().trim()
        if (buyerName.isBlank()) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.walk_in_customer_confirm)
                .setPositiveButton(R.string.yes) { _, _ -> checkStockAndSave(buyerName) }
                .setNegativeButton(R.string.no, null)
                .show()
            return
        }
        checkStockAndSave(buyerName)
    }

    private fun checkStockAndSave(buyerName: String) {
        if (viewModel.inventoryMode == com.kex.vikrsaathi.util.InventoryMode.OFF) {
            proceedSaveBill(buyerName)
            return
        }
        val address = binding.editBuyerAddress.text.toString().trim()
        val phone = binding.editBuyerPhone.text.toString().trim()
        viewModel.prepareSaveBill(buyerName, address, phone) { shortfalls ->
            if (shortfalls.isEmpty()) {
                proceedSaveBill(buyerName)
                return@prepareSaveBill
            }
            val details = shortfalls.joinToString("\n") {
                getString(
                    R.string.stock_shortfall_line,
                    it.itemName,
                    it.available,
                    it.required
                )
            }
            when (viewModel.inventoryMode) {
                com.kex.vikrsaathi.util.InventoryMode.BLOCK -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.insufficient_stock_title)
                        .setMessage(
                            getString(R.string.insufficient_stock_block_message) + "\n\n" + details
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                com.kex.vikrsaathi.util.InventoryMode.WARN -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.insufficient_stock_title)
                        .setMessage(
                            getString(R.string.insufficient_stock_warn_message) + "\n\n" + details
                        )
                        .setPositiveButton(R.string.yes) { _, _ -> proceedSaveBill(buyerName) }
                        .setNegativeButton(R.string.no, null)
                        .show()
                }
                com.kex.vikrsaathi.util.InventoryMode.OFF -> proceedSaveBill(buyerName)
            }
        }
    }

    private fun proceedSaveBill(buyerName: String) {
        val isNewBillSave = viewModel.isNewBillSession
        viewModel.saveBill(
            buyerName = buyerName,
            buyerAddress = binding.editBuyerAddress.text.toString().trim(),
            buyerPhone = binding.editBuyerPhone.text.toString().trim(),
            onError = { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: getString(R.string.save_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        ) { id, autoRegister ->
            if (autoRegister.hasChanges) {
                val message = buildList {
                    if (autoRegister.customerCreated) add(getString(R.string.auto_saved_customer))
                    if (autoRegister.itemCreatedCount > 0) {
                        add(
                            resources.getQuantityString(
                                R.plurals.auto_saved_items,
                                autoRegister.itemCreatedCount,
                                autoRegister.itemCreatedCount
                            )
                        )
                    }
                }.joinToString(" • ")
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
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
        binding.root.removeCallbacks(customerFlagCheck)
        binding.root.removeCallbacks(newItemCheck)
        BarcodeScanBus.onBarcodeScanned = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
        const val ARG_READ_ONLY = "readOnly"
        const val ARG_HELD_DRAFT_ID = "heldDraftId"
        private const val NAME_MATCH_DEBOUNCE_MS = 250L
    }
}
