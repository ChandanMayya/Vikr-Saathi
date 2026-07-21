package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.databinding.FragmentInvoiceBuilderBinding
import com.kex.vikrsaathi.ui.help.HelpOverlay
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.navigation.BackNavigationGuard
import com.kex.vikrsaathi.util.ViewModelFactory

class InvoiceBuilderFragment : Fragment(), BackNavigationGuard {

    private var _binding: FragmentInvoiceBuilderBinding? = null
    private val binding get() = _binding!!

    private var versionSheet: TemplateVersionHistoryBottomSheet? = null
    private var suppressDrawerListeners = false

    private lateinit var drawerSwitchLivePreview: MaterialSwitch
    private lateinit var drawerSwitchSnapGrid: MaterialSwitch
    private lateinit var drawerSwitchShowGrid: MaterialSwitch
    private lateinit var drawerSwitchSnapGuides: MaterialSwitch
    private lateinit var drawerSwitchSnapObjects: MaterialSwitch
    private lateinit var drawerSwitchShowGuides: MaterialSwitch
    private lateinit var drawerButtonAddVerticalGuide: MaterialButton
    private lateinit var drawerButtonAddHorizontalGuide: MaterialButton
    private lateinit var drawerButtonDeleteGuide: MaterialButton
    private lateinit var drawerButtonExportJson: MaterialButton
    private lateinit var drawerButtonImportJson: MaterialButton
    private lateinit var drawerButtonVersionHistory: MaterialButton
    private lateinit var drawerTogglePageOrientation: MaterialButtonToggleGroup
    private lateinit var drawerTextPageSize: TextView

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
        setupBackNavigation()

        val templateId = arguments?.getLong(ARG_TEMPLATE_ID, -1L) ?: -1L
        if (templateId > 0) {
            viewModel.loadTemplate(templateId)
        }

        binding.templateCanvas.listener = object : TemplateCanvasView.Listener {
            override fun onElementSelected(elementId: String?) {
                viewModel.selectElementSingle(elementId)
            }

            override fun onToggleSelection(elementId: String) {
                viewModel.toggleElementInSelection(elementId)
            }

            override fun onClearSelection() {
                viewModel.clearSelection()
            }

            override fun onLockedElementTapped() {
                Toast.makeText(requireContext(), R.string.element_locked, Toast.LENGTH_SHORT).show()
            }

            override fun onElementBoundsChangeStarted(elementId: String, isResize: Boolean) {
                viewModel.onBoundsChangeStarted(isResize)
            }

            override fun onElementBoundsChangeFinished(
                elementId: String,
                bounds: com.kex.vikrsaathi.data.model.template.ElementBounds
            ) {
                viewModel.onBoundsChangeFinished(elementId, bounds)
                refreshCanvas()
            }

            override fun onElementRotationChangeFinished(deltaDegrees: Float) {
                viewModel.rotateSelectionBy(deltaDegrees)
                refreshCanvas()
            }

            override fun onGuideSelected(guideId: String) {
                viewModel.selectGuide(guideId)
                updateGuideUi()
            }

            override fun onGuideDragStarted(guideId: String) {
                viewModel.onGuideDragStarted(guideId)
            }

            override fun onGuidePositionChanged(guideId: String, positionPt: Float) {
                viewModel.updateGuidePosition(guideId, positionPt)
            }

            override fun onGuideDragFinished(guideId: String, positionPt: Float) {
                viewModel.onGuideDragFinished(guideId, positionPt)
                refreshCanvas()
                updateGuideUi()
            }
        }

        viewModel.template.observe(viewLifecycleOwner) {
            if (binding.templateCanvas.isGestureActive) return@observe
            refreshCanvas()
            binding.templateCanvas.setRenderContext(viewModel.previewRenderContext(requireContext()))
            syncDrawerPageLayout()
        }
        viewModel.selectedElementIds.observe(viewLifecycleOwner) {
            if (!binding.templateCanvas.isGestureActive) {
                refreshCanvas()
                updateSelectionUi()
            }
        }
        viewModel.multiSelectMode.observe(viewLifecycleOwner) {
            if (!binding.templateCanvas.isGestureActive) {
                refreshCanvas()
            }
            updateMultiSelectButton()
        }
        viewModel.lockedTapEvent.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), R.string.element_locked, Toast.LENGTH_SHORT).show()
        }
        viewModel.saveResult.observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                Toast.makeText(requireContext(), R.string.template_saved, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.canUndo.observe(viewLifecycleOwner) { canUndo ->
            binding.buttonUndo.isEnabled = canUndo == true
        }
        viewModel.canRedo.observe(viewLifecycleOwner) { canRedo ->
            binding.buttonRedo.isEnabled = canRedo == true
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
        viewModel.snapToGuides.observe(viewLifecycleOwner) { enabled ->
            binding.templateCanvas.snapToGuides = enabled == true
            syncDrawerToggles()
        }
        viewModel.showGuides.observe(viewLifecycleOwner) { enabled ->
            binding.templateCanvas.showGuides = enabled == true
            syncDrawerToggles()
        }
        viewModel.snapToObjects.observe(viewLifecycleOwner) { enabled ->
            binding.templateCanvas.snapToObjects = enabled == true
            syncDrawerToggles()
        }
        viewModel.selectedGuideId.observe(viewLifecycleOwner) {
            if (!binding.templateCanvas.isGestureActive) {
                refreshCanvas()
            }
            updateGuideUi()
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
            val sheet = versionSheet
            if (sheet != null && sheet.isAdded) {
                sheet.submitVersions(versions)
            }
        }

        binding.buttonUndo.setOnClickListener { viewModel.undo() }
        binding.buttonRedo.setOnClickListener { viewModel.redo() }
        binding.buttonInspectElement.setOnClickListener { openInspector() }
        binding.buttonEditTableColumns.setOnClickListener { openTableColumnEditor() }
        binding.buttonMultiSelect.setOnClickListener {
            viewModel.setMultiSelectMode(viewModel.multiSelectMode.value != true)
        }
        binding.buttonDelete.setOnClickListener { viewModel.removeSelectedElements() }
        binding.buttonGroup.setOnClickListener { viewModel.groupSelection() }
        binding.buttonUngroup.setOnClickListener { viewModel.ungroupSelection() }
        binding.buttonToggleLock.setOnClickListener { viewModel.toggleLockSelection() }
        binding.buttonDuplicate.setOnClickListener { viewModel.duplicateSelectedElement() }
        binding.buttonRotateLeft.setOnClickListener { viewModel.rotateSelectionCounterClockwise() }
        binding.buttonRotateRight.setOnClickListener { viewModel.rotateSelectionClockwise() }
        binding.buttonBringForward.setOnClickListener { viewModel.bringForward() }
        binding.buttonSendBackward.setOnClickListener { viewModel.sendBackward() }
        binding.buttonBringToFront.setOnClickListener { viewModel.bringToFront() }
        binding.buttonSendToBack.setOnClickListener { viewModel.sendToBack() }
        binding.buttonDeleteGuide.setOnClickListener { viewModel.removeSelectedGuide() }
        binding.buttonSaveTemplate.setOnClickListener { viewModel.saveTemplate() }
        binding.buttonPreviewTemplate.setOnClickListener { previewPdf() }
        binding.fabAddElement.setOnClickListener { openAddElementSheet() }

        updateMultiSelectButton()
        updateSelectionUi()
        updateGuideUi()
    }

    override fun interceptBackNavigation(navigate: () -> Unit): Boolean {
        if (!viewModel.hasUnsavedChanges()) return false
        showLeaveWithoutSavingDialog(navigate)
        return true
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!interceptBackNavigation { findNavController().navigateUp() }) {
                        findNavController().navigateUp()
                    }
                }
            }
        )
    }

    private fun showLeaveWithoutSavingDialog(onLeave: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.leave_invoice_builder_title)
            .setMessage(R.string.leave_invoice_builder_message)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.saveTemplate { onLeave() }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.leave_without_saving) { _, _ -> onLeave() }
            .show()
    }

    private fun refreshCanvas() {
        binding.templateCanvas.setTemplate(
            viewModel.template.value,
            viewModel.selectedElementIds.value.orEmpty(),
            viewModel.multiSelectMode.value == true,
            viewModel.selectedGuideId.value
        )
    }

    private fun updateGuideUi() {
        val hasGuide = viewModel.selectedGuideId.value != null
        setActionEnabled(binding.buttonDeleteGuide, hasGuide)
        drawerButtonDeleteGuide.isEnabled = hasGuide
    }

    private fun updateSelectionUi() {
        val ids = viewModel.selectedElementIds.value.orEmpty()
        val hasSelection = ids.isNotEmpty()
        val isLocked = viewModel.isSelectionLocked()
        val isSingleTable = ids.size == 1 && viewModel.getSelectedElement()?.kind == ElementKind.TABLE
        val isGrouped = viewModel.isSelectionGrouped()

        setActionEnabled(binding.buttonInspectElement, hasSelection)
        setActionEnabled(binding.buttonEditTableColumns, isSingleTable)
        setActionEnabled(binding.buttonDelete, hasSelection)
        setActionEnabled(binding.buttonDuplicate, hasSelection)
        setActionEnabled(binding.buttonRotateLeft, hasSelection && !isLocked)
        setActionEnabled(binding.buttonRotateRight, hasSelection && !isLocked)
        setActionEnabled(binding.buttonToggleLock, hasSelection)
        setActionEnabled(binding.buttonBringForward, hasSelection)
        setActionEnabled(binding.buttonSendBackward, hasSelection)
        setActionEnabled(binding.buttonBringToFront, hasSelection)
        setActionEnabled(binding.buttonSendToBack, hasSelection)
        setActionEnabled(binding.buttonGroup, ids.size >= 2)
        setActionEnabled(binding.buttonUngroup, hasSelection && isGrouped)

        binding.buttonToggleLock.text = getString(
            if (viewModel.isSelectionLocked()) R.string.unlock_elements else R.string.lock_elements
        )
        binding.textSelectionCount.isVisible = ids.size > 1
        if (ids.size > 1) {
            binding.textSelectionCount.text = getString(R.string.n_elements_selected, ids.size)
        }
    }

    private fun setActionEnabled(button: MaterialButton, enabled: Boolean) {
        button.isEnabled = enabled
    }

    private fun updateMultiSelectButton() {
        val enabled = viewModel.multiSelectMode.value == true
        binding.buttonMultiSelect.isChecked = enabled
    }

    private fun bindDrawerViews(view: View) {
        drawerSwitchLivePreview = view.findViewById(R.id.drawerSwitchLivePreview)
        drawerSwitchSnapGrid = view.findViewById(R.id.drawerSwitchSnapGrid)
        drawerSwitchShowGrid = view.findViewById(R.id.drawerSwitchShowGrid)
        drawerSwitchSnapGuides = view.findViewById(R.id.drawerSwitchSnapGuides)
        drawerSwitchSnapObjects = view.findViewById(R.id.drawerSwitchSnapObjects)
        drawerSwitchShowGuides = view.findViewById(R.id.drawerSwitchShowGuides)
        drawerButtonAddVerticalGuide = view.findViewById(R.id.drawerButtonAddVerticalGuide)
        drawerButtonAddHorizontalGuide = view.findViewById(R.id.drawerButtonAddHorizontalGuide)
        drawerButtonDeleteGuide = view.findViewById(R.id.drawerButtonDeleteGuide)
        drawerButtonExportJson = view.findViewById(R.id.drawerButtonExportJson)
        drawerButtonImportJson = view.findViewById(R.id.drawerButtonImportJson)
        drawerButtonVersionHistory = view.findViewById(R.id.drawerButtonVersionHistory)
        drawerTogglePageOrientation = view.findViewById(R.id.drawerTogglePageOrientation)
        drawerTextPageSize = view.findViewById(R.id.drawerTextPageSize)
    }

    private fun setupToolbarMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_invoice_builder, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.action_help -> {
                        HelpOverlay.show(requireActivity(), HelpScreen.INVOICE_BUILDER)
                        return true
                    }
                    R.id.action_builder_options -> {
                        syncDrawerToggles()
                        binding.drawerLayout.openDrawer(GravityCompat.END)
                        return true
                    }
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

        drawerTogglePageOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppressDrawerListeners || !isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.drawerButtonPortrait -> viewModel.setPageOrientation(false)
                R.id.drawerButtonLandscape -> viewModel.setPageOrientation(true)
            }
        }

        drawerSwitchSnapGrid.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setSnapToGrid(checked)
        }
        drawerSwitchShowGrid.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setShowGrid(checked)
        }
        drawerSwitchSnapGuides.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setSnapToGuides(checked)
        }
        drawerSwitchSnapObjects.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setSnapToObjects(checked)
        }
        drawerSwitchShowGuides.setOnCheckedChangeListener { _, checked ->
            if (!suppressDrawerListeners) viewModel.setShowGuides(checked)
        }

        drawerButtonAddVerticalGuide.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            viewModel.addVerticalGuide()
        }
        drawerButtonAddHorizontalGuide.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            viewModel.addHorizontalGuide()
        }
        drawerButtonDeleteGuide.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            viewModel.removeSelectedGuide()
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
        drawerSwitchSnapGuides.isChecked = viewModel.snapToGuides.value == true
        drawerSwitchSnapObjects.isChecked = viewModel.snapToObjects.value == true
        drawerSwitchShowGuides.isChecked = viewModel.showGuides.value == true
        syncDrawerPageLayout()
        suppressDrawerListeners = false
        updateGuideUi()
    }

    private fun syncDrawerPageLayout() {
        val template = viewModel.template.value ?: return
        drawerTextPageSize.text = getString(
            R.string.page_size_format,
            template.pageWidthPt,
            template.pageHeightPt
        )
        val orientationButtonId = if (viewModel.isPageLandscape()) {
            R.id.drawerButtonLandscape
        } else {
            R.id.drawerButtonPortrait
        }
        if (drawerTogglePageOrientation.checkedButtonId != orientationButtonId) {
            drawerTogglePageOrientation.check(orientationButtonId)
        }
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
        val selected = viewModel.getSelectedElements()
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.select_elements_first, Toast.LENGTH_SHORT).show()
            return
        }

        val callback = object : ElementInspectorBottomSheet.Callback {
            override fun onApply(
                element: com.kex.vikrsaathi.data.model.template.TemplateElement,
                shiftLayoutBelow: Boolean
            ) {
                viewModel.applyElementWithOptionalLayoutShift(element, shiftLayoutBelow)
            }

            override fun onApplyBulk(
                elementIds: Set<String>,
                updates: ElementInspectorBottomSheet.BulkInspectorUpdates
            ) {
                viewModel.applyBulkUpdates(elementIds, updates)
                offerImageBoundsAdjustForElements(elementIds)
            }

            override fun onDelete(elementIds: Set<String>) {
                viewModel.removeElements(elementIds)
            }
        }

        val sheet = if (selected.size > 1) {
            ElementInspectorBottomSheet.newInstanceForBulk(selected)
        } else {
            ElementInspectorBottomSheet.newInstance(
                templateId = viewModel.template.value?.id ?: 0L,
                element = selected.first()
            )
        }
        sheet.callback = callback
        sheet.show(parentFragmentManager, "element_inspector")
    }

    private fun offerImageBoundsAdjustForElements(elementIds: Set<String>) {
        val adjustments = viewModel.previewImageBoundsAdjustments(requireContext(), elementIds)
        if (adjustments.isEmpty()) return
        ImageBoundsAdjustDialog.confirmBulkImageBoundsIfNeeded(
            fragment = this,
            adjustmentCount = adjustments.size,
            sampleBounds = adjustments.first().second
        ) { adjust ->
            if (adjust) {
                viewModel.applyImageBoundsAdjustments(adjustments)
            }
        }
    }

    private fun openTableColumnEditor() {
        val element = viewModel.getSelectedElement()
        if (element == null || element.kind != ElementKind.TABLE) {
            Toast.makeText(requireContext(), R.string.select_table_first, Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = TableColumnEditorBottomSheet.newInstance(element)
        sheet.callback = object : TableColumnEditorBottomSheet.Callback {
            override fun onApply(
                elementId: String,
                columns: List<com.kex.vikrsaathi.data.model.template.TableColumn>,
                borderWidthDp: Float,
                showTotalRow: Boolean,
                totalRowLabel: String
            ) {
                viewModel.updateTableColumns(
                    elementId,
                    columns,
                    borderWidthDp,
                    showTotalRow,
                    totalRowLabel
                )
            }
        }
        sheet.show(parentFragmentManager, "table_columns")
    }

    private fun openVersionHistory() {
        val sheet = TemplateVersionHistoryBottomSheet.newInstance()
        sheet.callback = object : TemplateVersionHistoryBottomSheet.Callback {
            override fun onRestore(versionId: Long) {
                viewModel.restoreVersion(versionId)
            }
        }
        versionSheet = sheet
        sheet.show(parentFragmentManager, "template_versions")
        viewModel.loadVersionHistory()
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
