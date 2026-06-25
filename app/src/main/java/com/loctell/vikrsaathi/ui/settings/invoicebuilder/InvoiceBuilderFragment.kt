package com.loctell.vikrsaathi.ui.settings.invoicebuilder

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.loctell.vikrsaathi.R
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.data.model.template.ElementKind
import com.loctell.vikrsaathi.databinding.FragmentInvoiceBuilderBinding
import com.loctell.vikrsaathi.util.ViewModelFactory

class InvoiceBuilderFragment : Fragment() {

    private var _binding: FragmentInvoiceBuilderBinding? = null
    private val binding get() = _binding!!

    private var versionSheet: TemplateVersionHistoryBottomSheet? = null
    private var suppressDrawerListeners = false

    private lateinit var drawerSwitchLivePreview: MaterialSwitch
    private lateinit var drawerSwitchSnapGrid: MaterialSwitch
    private lateinit var drawerSwitchShowGrid: MaterialSwitch
    private lateinit var drawerButtonExportJson: MaterialButton
    private lateinit var drawerButtonImportJson: MaterialButton
    private lateinit var drawerButtonVersionHistory: MaterialButton

    private val viewModel: InvoiceBuilderViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        binding.drawerLayout.closeDrawer(GravityCompat.END)
        viewModel.importTemplateJson(requireContext(), uri) { success ->
            val message = if (success) R.string.template_imported else R.string.template_import_failed
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindDrawerViews(view)
        setupToolbarMenu()
        setupDrawer()

        val templateId = arguments?.getLong(ARG_TEMPLATE_ID, -1L) ?: -1L
        if (templateId > 0) {
            viewModel.loadTemplate(templateId)
        }

        binding.templateCanvas.listener = object : TemplateCanvasView.Listener {
            override fun onElementSelected(elementId: String?) {
                viewModel.selectElement(elementId)
            }

            override fun onElementBoundsChangeStarted(elementId: String) {
                viewModel.onBoundsChangeStarted()
            }

            override fun onElementBoundsChanged(
                elementId: String,
                bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds
            ) {
                viewModel.updateElementBounds(elementId, bounds)
            }

            override fun onElementBoundsChangeFinished(
                elementId: String,
                bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds
            ) {
                viewModel.onBoundsChangeFinished(elementId, bounds)
            }
        }

        viewModel.template.observe(viewLifecycleOwner) { template ->
            binding.templateCanvas.setTemplate(template, viewModel.selectedElementId.value)
            binding.templateCanvas.setRenderContext(viewModel.previewRenderContext(requireContext()))
        }
        viewModel.selectedElementId.observe(viewLifecycleOwner) { selectedId ->
            binding.templateCanvas.setTemplate(viewModel.template.value, selectedId)
            val element = viewModel.getSelectedElement()
            binding.buttonEditTableColumns.isVisible = element?.kind == ElementKind.TABLE
            binding.scrollElementActions.isVisible = selectedId != null
        }
        viewModel.saveResult.observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                Toast.makeText(requireContext(), R.string.template_saved, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.canUndo.observe(viewLifecycleOwner) { canUndo ->
            binding.buttonUndo.isEnabled = canUndo == true
            binding.buttonUndo.alpha = if (canUndo == true) 1f else 0.4f
        }
        viewModel.canRedo.observe(viewLifecycleOwner) { canRedo ->
            binding.buttonRedo.isEnabled = canRedo == true
            binding.buttonRedo.alpha = if (canRedo == true) 1f else 0.4f
        }
        viewModel.livePreview.observe(viewLifecycleOwner) { enabled ->
            binding.templateCanvas.showPreview = enabled == true
            syncDrawerToggles()
        }
        viewModel.snapToGrid.observe(viewLifecycleOwner) { enabled ->
            binding.templateCanvas.snapToGrid = enabled == true
            syncDrawerToggles()
        }
        viewModel.showGrid.observe(viewLifecycleOwner) { enabled ->
            binding.templateCanvas.showGrid = enabled == true
            syncDrawerToggles()
        }
        viewModel.validationIssues.observe(viewLifecycleOwner) { issues ->
            binding.templateCanvas.validationElementIds = viewModel.validationElementIds()
            if (issues.isEmpty()) {
                binding.textValidation.isVisible = false
            } else {
                binding.textValidation.isVisible = true
                binding.textValidation.text = getString(R.string.layout_issues, issues.size)
            }
        }
        viewModel.restoreResult.observe(viewLifecycleOwner) { restored ->
            if (restored == null) return@observe
            val message = if (restored) R.string.version_restored else R.string.version_restore_failed
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        viewModel.versionHistory.observe(viewLifecycleOwner) { versions ->
            versionSheet?.submitVersions(versions)
        }

        binding.buttonUndo.setOnClickListener { viewModel.undo() }
        binding.buttonRedo.setOnClickListener { viewModel.redo() }
        binding.buttonInspectElement.setOnClickListener { openInspector() }
        binding.buttonEditTableColumns.setOnClickListener { openTableColumnEditor() }
        binding.buttonDuplicate.setOnClickListener { viewModel.duplicateSelectedElement() }
        binding.buttonBringForward.setOnClickListener { viewModel.bringForward() }
        binding.buttonSendBackward.setOnClickListener { viewModel.sendBackward() }
        binding.buttonBringToFront.setOnClickListener { viewModel.bringToFront() }
        binding.buttonSendToBack.setOnClickListener { viewModel.sendToBack() }
        binding.buttonSaveTemplate.setOnClickListener { viewModel.saveTemplate() }
        binding.buttonPreviewTemplate.setOnClickListener { previewPdf() }
        binding.fabAddElement.setOnClickListener { openAddElementSheet() }
    }

    private fun bindDrawerViews(view: View) {
        drawerSwitchLivePreview = view.findViewById(R.id.drawerSwitchLivePreview)
        drawerSwitchSnapGrid = view.findViewById(R.id.drawerSwitchSnapGrid)
        drawerSwitchShowGrid = view.findViewById(R.id.drawerSwitchShowGrid)
        drawerButtonExportJson = view.findViewById(R.id.drawerButtonExportJson)
        drawerButtonImportJson = view.findViewById(R.id.drawerButtonImportJson)
        drawerButtonVersionHistory = view.findViewById(R.id.drawerButtonVersionHistory)
    }

    private fun setupToolbarMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_invoice_builder, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_builder_options) {
                    syncDrawerToggles()
                    binding.drawerLayout.openDrawer(GravityCompat.END)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupDrawer() {
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                syncDrawerToggles()
            }
        })

        drawerSwitchLivePreview.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setLivePreview(checked)
        }
        drawerSwitchSnapGrid.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setSnapToGrid(checked)
        }
        drawerSwitchShowGrid.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setShowGrid(checked)
        }

        drawerButtonExportJson.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            exportJson()
        }
        drawerButtonImportJson.setOnClickListener {
            importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
        }
        drawerButtonVersionHistory.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            openVersionHistory()
        }
    }

    private fun syncDrawerToggles() {
        suppressDrawerListeners = true
        drawerSwitchLivePreview.isChecked = viewModel.livePreview.value == true
        drawerSwitchSnapGrid.isChecked = viewModel.snapToGrid.value == true
        drawerSwitchShowGrid.isChecked = viewModel.showGrid.value == true
        suppressDrawerListeners = false
    }

    private fun openAddElementSheet() {
        val sheet = AddElementBottomSheet.newInstance()
        sheet.callback = object : AddElementBottomSheet.Callback {
            override fun onAddElement(kind: ElementKind) {
                viewModel.addElement(kind)
            }
        }
        sheet.show(parentFragmentManager, "add_element")
    }

    private fun openInspector() {
        val element = viewModel.getSelectedElement()
        if (element == null) {
            Toast.makeText(requireContext(), R.string.select_element_first, Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = ElementInspectorBottomSheet.newInstance(
            templateId = viewModel.template.value?.id ?: 0L,
            element = element
        )
        sheet.callback = object : ElementInspectorBottomSheet.Callback {
            override fun onApply(element: com.loctell.vikrsaathi.data.model.template.TemplateElement) {
                viewModel.updateElement(element)
            }

            override fun onDelete(elementId: String) {
                viewModel.removeElement(elementId)
            }
        }
        sheet.show(parentFragmentManager, "element_inspector")
    }

    private fun openTableColumnEditor() {
        val element = viewModel.getSelectedElement()
        if (element == null || element.kind != ElementKind.TABLE) {
            Toast.makeText(requireContext(), R.string.select_table_first, Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = TableColumnEditorBottomSheet.newInstance(element)
        sheet.callback = object : TableColumnEditorBottomSheet.Callback {
            override fun onApply(elementId: String, columns: List<com.loctell.vikrsaathi.data.model.template.TableColumn>) {
                viewModel.updateTableColumns(elementId, columns)
            }
        }
        sheet.show(parentFragmentManager, "table_columns")
    }

    private fun openVersionHistory() {
        viewModel.loadVersionHistory()
        val sheet = TemplateVersionHistoryBottomSheet.newInstance()
        sheet.callback = object : TemplateVersionHistoryBottomSheet.Callback {
            override fun onRestore(versionId: Long) {
                viewModel.restoreVersion(versionId)
            }
        }
        versionSheet = sheet
        sheet.show(parentFragmentManager, "template_versions")
    }

    private fun previewPdf() {
        viewModel.exportPreviewPdf(requireContext()) { file ->
            if (file == null) {
                Toast.makeText(requireContext(), R.string.pdf_generation_failed, Toast.LENGTH_SHORT).show()
                return@exportPreviewPdf
            }
            openPdf(file)
        }
    }

    private fun exportJson() {
        viewModel.exportTemplateJson(requireContext()) { file ->
            if (file == null) {
                Toast.makeText(requireContext(), R.string.template_export_failed, Toast.LENGTH_SHORT).show()
                return@exportTemplateJson
            }
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_template)))
        }
    }

    private fun openPdf(file: java.io.File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.pdf_saved, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        versionSheet = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_TEMPLATE_ID = "templateId"
    }
}
