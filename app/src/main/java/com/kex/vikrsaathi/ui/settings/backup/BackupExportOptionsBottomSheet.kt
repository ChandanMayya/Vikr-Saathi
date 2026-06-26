package com.kex.vikrsaathi.ui.settings.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.backup.BackupExportOptions

class BackupExportOptionsBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onExport(options: BackupExportOptions)
    }

    var callback: Callback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_backup_export_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val checkSales = view.findViewById<MaterialCheckBox>(R.id.checkExportSales)
        val checkSettings = view.findViewById<MaterialCheckBox>(R.id.checkExportSettings)
        val checkTemplates = view.findViewById<MaterialCheckBox>(R.id.checkExportTemplates)
        val checkItems = view.findViewById<MaterialCheckBox>(R.id.checkExportItems)
        val checkCustomers = view.findViewById<MaterialCheckBox>(R.id.checkExportCustomers)

        checkSales.isChecked = true
        checkSettings.isChecked = true
        checkTemplates.isChecked = true
        checkItems.isChecked = true
        checkCustomers.isChecked = true

        view.findViewById<View>(R.id.buttonStartExport).setOnClickListener {
            val options = BackupExportOptions(
                includeSales = checkSales.isChecked,
                includeSettings = checkSettings.isChecked,
                includeTemplates = checkTemplates.isChecked,
                includeItems = checkItems.isChecked,
                includeCustomers = checkCustomers.isChecked
            )
            callback?.onExport(options)
            dismiss()
        }
    }

    companion object {
        fun newInstance() = BackupExportOptionsBottomSheet()
    }
}
