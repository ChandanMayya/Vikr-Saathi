package com.loctell.vikrsaathi.ui.bill

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.loctell.vikrsaathi.R
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.data.entity.Customer
import com.loctell.vikrsaathi.data.entity.Item
import com.loctell.vikrsaathi.databinding.DialogCustomerFormBinding
import com.loctell.vikrsaathi.databinding.FragmentBillBinding
import com.loctell.vikrsaathi.ui.scanner.BarcodeScannerActivity
import com.loctell.vikrsaathi.ui.scanner.BarcodeScanBus
import com.loctell.vikrsaathi.util.PdfGenerator
import com.loctell.vikrsaathi.util.PriceCalculator
import com.loctell.vikrsaathi.util.ViewModelFactory

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
        if (billId > 0) {
            viewModel.loadBill(billId)
        } else if (savedInstanceState == null) {
            viewModel.clearBill()
        }

        setupCustomerAutocomplete()
        setupItemAutocomplete()

        viewModel.selectedCustomer.observe(viewLifecycleOwner) { customer ->
            suppressCustomerSearch = true
            binding.autoCompleteCustomer.setText(customer?.name.orEmpty(), false)
            binding.editBuyerAddress.setText(customer?.formattedAddress().orEmpty())
            binding.editBuyerPhone.setText(customer?.phone.orEmpty())
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
        binding.buttonGeneratePdf.setOnClickListener { generatePdf(print = false) }
        binding.buttonPrint.setOnClickListener { generatePdf(print = true) }
    }

    private fun setupCustomerAutocomplete() {
        binding.autoCompleteCustomer.threshold = 1

        binding.autoCompleteCustomer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressCustomerSearch) return
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
                if (suppressItemSearch) return
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
        viewModel.addItemByBarcode(barcode) { item ->
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
            }
        }
    }

    private fun saveBill() {
        if (viewModel.lineItems.value.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.add_items_first, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.saveBill {
            Toast.makeText(requireContext(), R.string.bill_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun generatePdf(print: Boolean) {
        fun createPdf(billId: Long) {
            val app = requireActivity().application as VikrSaathiApp
            viewModel.getBillDetails(billId) { bill ->
                if (bill == null) return@getBillDetails
                val file = PdfGenerator.generateBillPdf(
                    requireContext(),
                    bill,
                    app.settingsRepository.shopName,
                    app.settingsRepository.currencySymbol,
                    app.settingsRepository.getHeaderImage(),
                    app.settingsRepository.getSignatureImage()
                )
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (print) {
                    val printIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(Intent.createChooser(printIntent, getString(R.string.print)))
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), R.string.pdf_saved, Toast.LENGTH_LONG).show()
                    }
                } else {
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), R.string.pdf_saved, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val existingId = viewModel.currentBillId ?: arguments?.getLong(ARG_BILL_ID, -1L)?.takeIf { it > 0 }
        if (existingId != null) {
            createPdf(existingId)
        } else {
            viewModel.saveBill { id -> createPdf(id) }
        }
    }

    override fun onDestroyView() {
        BarcodeScanBus.onBarcodeScanned = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
    }
}
