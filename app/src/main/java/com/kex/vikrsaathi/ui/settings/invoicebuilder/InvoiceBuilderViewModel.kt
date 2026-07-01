package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.ElementStyle
import com.kex.vikrsaathi.data.model.template.GuideOrientation
import com.kex.vikrsaathi.data.model.template.ImageScaleMode
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.InvoiceTemplateVersion
import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TextAlign
import com.kex.vikrsaathi.data.model.template.TemplateGuide
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.domain.template.ElementBoundsHelper
import com.kex.vikrsaathi.domain.template.ElementSelectionHelper
import com.kex.vikrsaathi.domain.template.ElementZOrder
import com.kex.vikrsaathi.domain.template.GridSnapper
import com.kex.vikrsaathi.domain.template.GuideSnapper
import com.kex.vikrsaathi.domain.template.ObjectAlignmentSnapper
import com.kex.vikrsaathi.domain.template.SampleBillFactory
import com.kex.vikrsaathi.domain.template.TemplateImageBitmapResolver
import com.kex.vikrsaathi.domain.template.TemplateImageBoundsHelper
import com.kex.vikrsaathi.domain.template.TemplateLayoutValidator
import com.kex.vikrsaathi.domain.template.TemplateContextFactory
import com.kex.vikrsaathi.domain.template.TemplateRenderContext
import com.kex.vikrsaathi.domain.template.TemplateValidationIssue
import com.kex.vikrsaathi.domain.template.TableBorderSettings
import com.kex.vikrsaathi.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class InvoiceBuilderViewModel(
    private val templateRepository: InvoiceTemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val editorPreferences: com.kex.vikrsaathi.util.InvoiceBuilderPreferences
) : ViewModel() {

    private val history = TemplateHistory()
    private var dragSnapshot: InvoiceTemplate? = null
    private var dragIsResize = false
    private var dragStartBounds: Map<String, ElementBounds> = emptyMap()
    private var dragStartUnion: ElementBounds? = null
    private var guideDragSnapshot: InvoiceTemplate? = null

    private val _template = MutableLiveData<InvoiceTemplate>()
    val template: LiveData<InvoiceTemplate> = _template

    private val _selectedElementIds = MutableLiveData<Set<String>>(emptySet())
    val selectedElementIds: LiveData<Set<String>> = _selectedElementIds

    private val _multiSelectMode = MutableLiveData(false)
    val multiSelectMode: LiveData<Boolean> = _multiSelectMode

    private val _lockedTapEvent = MutableLiveData<Unit>()
    val lockedTapEvent: LiveData<Unit> = _lockedTapEvent

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> = _saveResult

    private val _validationIssues = MutableLiveData<List<TemplateValidationIssue>>(emptyList())
    val validationIssues: LiveData<List<TemplateValidationIssue>> = _validationIssues

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> = _canRedo

    private val _livePreview = MutableLiveData(false)
    val livePreview: LiveData<Boolean> = _livePreview

    private val _snapToGrid = MutableLiveData(true)
    val snapToGrid: LiveData<Boolean> = _snapToGrid

    private val _showGrid = MutableLiveData(false)
    val showGrid: LiveData<Boolean> = _showGrid

    private val _snapToGuides = MutableLiveData(true)
    val snapToGuides: LiveData<Boolean> = _snapToGuides

    private val _showGuides = MutableLiveData(true)
    val showGuides: LiveData<Boolean> = _showGuides

    private val _snapToObjects = MutableLiveData(true)
    val snapToObjects: LiveData<Boolean> = _snapToObjects

    private val _selectedGuideId = MutableLiveData<String?>(null)
    val selectedGuideId: LiveData<String?> = _selectedGuideId

    private val _versionHistory = MutableLiveData<List<InvoiceTemplateVersion>>(emptyList())
    val versionHistory: LiveData<List<InvoiceTemplateVersion>> = _versionHistory

    private val _restoreResult = MutableLiveData<Boolean>()
    val restoreResult: LiveData<Boolean> = _restoreResult

    private var savedSnapshot: String? = null

    init {
        _livePreview.value = editorPreferences.livePreview
        _snapToGrid.value = editorPreferences.snapToGrid
        _showGrid.value = editorPreferences.showGrid
        _snapToGuides.value = editorPreferences.snapToGuides
        _showGuides.value = editorPreferences.showGuides
        _snapToObjects.value = editorPreferences.snapToObjects
    }

    fun previewRenderContext(context: android.content.Context): TemplateRenderContext {
        val template = _template.value ?: return TemplateRenderContext(
            bill = SampleBillFactory.createSample(),
            shopName = settingsRepository.shopName,
            currencySymbol = settingsRepository.currencySymbol,
            headerImage = settingsRepository.getHeaderImage(),
            signatureImage = settingsRepository.getSignatureImage(),
            shopLogoImage = settingsRepository.getShopLogoImage()
        )
        return TemplateContextFactory.create(
            context = context,
            template = template,
            bill = SampleBillFactory.createSample(),
            shopName = settingsRepository.shopName,
            currencySymbol = settingsRepository.currencySymbol,
            headerImage = settingsRepository.getHeaderImage(),
            signatureImage = settingsRepository.getSignatureImage(),
            shopLogoImage = settingsRepository.getShopLogoImage()
        )
    }

    fun loadTemplate(templateId: Long) {
        viewModelScope.launch {
            val loaded = templateRepository.getById(templateId)
                ?: templateRepository.getDefaultTemplate()
            history.clear()
            setTemplateInternal(loaded, recordHistory = false)
            markSaved(loaded)
            _selectedElementIds.value = emptySet()
            _selectedGuideId.value = null
            refreshHistoryState()
        }
    }

    fun setMultiSelectMode(enabled: Boolean) {
        _multiSelectMode.value = enabled
        if (!enabled) {
            val ids = _selectedElementIds.value.orEmpty()
            if (ids.size > 1) {
                _selectedElementIds.value = ids.take(1).toSet()
            }
        }
    }

    fun selectElementSingle(elementId: String?) {
        if (elementId == null) {
            if (_selectedElementIds.value.orEmpty().isNotEmpty()) {
                _selectedElementIds.value = emptySet()
            }
            return
        }
        val elements = _template.value?.elements.orEmpty()
        val element = elements.find { it.id == elementId } ?: return
        if (element.locked) {
            _lockedTapEvent.value = Unit
            return
        }
        val expanded = ElementSelectionHelper.expandWithGroup(elements, elementId)
        if (_selectedElementIds.value == expanded) return
        _selectedGuideId.value = null
        _selectedElementIds.value = expanded
    }

    fun toggleElementInSelection(elementId: String) {
        val elements = _template.value?.elements.orEmpty()
        val element = elements.find { it.id == elementId } ?: return
        val groupIds = ElementSelectionHelper.expandWithGroup(elements, elementId)
        val current = _selectedElementIds.value.orEmpty().toMutableSet()
        if (groupIds.any { it in current }) {
            current.removeAll(groupIds)
        } else {
            current.addAll(groupIds)
        }
        _selectedElementIds.value = current
    }

    fun clearSelection() {
        _selectedElementIds.value = emptySet()
        _selectedGuideId.value = null
    }

    fun selectGuide(guideId: String?) {
        if (_selectedGuideId.value == guideId) return
        _selectedGuideId.value = guideId
        if (guideId != null) {
            _selectedElementIds.value = emptySet()
        }
    }

    fun addVerticalGuide() {
        mutate { current ->
            val guide = TemplateGuide(
                id = UUID.randomUUID().toString(),
                orientation = GuideOrientation.VERTICAL,
                positionPt = current.pageWidthPt / 2f
            )
            _selectedGuideId.value = guide.id
            _selectedElementIds.value = emptySet()
            current.copy(guides = current.guides + guide)
        }
    }

    fun addHorizontalGuide() {
        mutate { current ->
            val guide = TemplateGuide(
                id = UUID.randomUUID().toString(),
                orientation = GuideOrientation.HORIZONTAL,
                positionPt = current.pageHeightPt / 2f
            )
            _selectedGuideId.value = guide.id
            _selectedElementIds.value = emptySet()
            current.copy(guides = current.guides + guide)
        }
    }

    fun removeSelectedGuide() {
        val guideId = _selectedGuideId.value ?: return
        mutate { current ->
            current.copy(guides = current.guides.filter { it.id != guideId })
        }
        _selectedGuideId.value = null
    }

    fun onGuideDragStarted(guideId: String) {
        guideDragSnapshot = _template.value?.copy()
        selectGuide(guideId)
    }

    fun updateGuidePosition(guideId: String, positionPt: Float) {
        val current = _template.value ?: return
        setTemplateInternal(
            current.copy(
                guides = current.guides.map { guide ->
                    if (guide.id == guideId) guide.copy(positionPt = positionPt) else guide
                }
            ),
            recordHistory = false
        )
    }

    fun onGuideDragFinished(guideId: String, positionPt: Float) {
        val snapshot = guideDragSnapshot
        guideDragSnapshot = null
        val current = _template.value ?: return
        val originalPosition = snapshot?.guides?.find { it.id == guideId }?.positionPt
        updateGuidePosition(guideId, positionPt)
        if (snapshot != null && originalPosition != positionPt) {
            history.push(snapshot)
            refreshHistoryState()
        }
    }

    /** @deprecated Use selectedElementIds */
    fun selectElement(elementId: String?) = selectElementSingle(elementId)

    fun onBoundsChangeStarted(isResize: Boolean) {
        dragSnapshot = _template.value?.copy()
        dragIsResize = isResize
        val ids = _selectedElementIds.value.orEmpty()
        val elements = _template.value?.elements.orEmpty()
        dragStartBounds = ids.associateWith { id ->
            elements.first { it.id == id }.bounds
        }
        dragStartUnion = ElementBoundsHelper.unionBounds(elements, ids)
    }

    fun updateElementBounds(anchorId: String, bounds: ElementBounds) {
        val current = _template.value ?: return
        val ids = _selectedElementIds.value.orEmpty()
        val updatedElements = if (ids.size <= 1) {
            current.elements.map {
                if (it.id == anchorId) it.copy(bounds = bounds) else it
            }
        } else if (dragIsResize) {
            val startUnion = dragStartUnion ?: return
            ElementBoundsHelper.scaleSelection(
                current.elements,
                ids,
                startUnion,
                bounds,
                current.pageWidthPt,
                current.pageHeightPt
            )
        } else {
            val startUnion = dragStartUnion ?: return
            val dx = bounds.x - startUnion.x
            val dy = bounds.y - startUnion.y
            ElementBoundsHelper.moveByDelta(
                current.elements,
                ids,
                dx,
                dy,
                current.pageWidthPt,
                current.pageHeightPt
            )
        }
        setTemplateInternal(current.copy(elements = updatedElements), recordHistory = false)
    }

    fun onBoundsChangeFinished(anchorId: String, bounds: ElementBounds) {
        val snapshot = dragSnapshot
        val wasResize = dragIsResize
        val startBounds = dragStartBounds
        val startUnion = dragStartUnion
        dragSnapshot = null
        dragIsResize = false
        dragStartBounds = emptyMap()
        dragStartUnion = null
        if (snapshot == null) return

        val ids = _selectedElementIds.value.orEmpty()
        val current = _template.value ?: return
        val snapToGrid = _snapToGrid.value == true
        val guides = current.guides
        val snapGuides = _snapToGuides.value == true
        val snapObjects = _snapToObjects.value == true
        val referenceBounds = current.elements
            .filter { it.visible && it.id !in ids }
            .map { it.bounds }

        fun snap(bounds: ElementBounds): ElementBounds {
            var snapped = GridSnapper.snapBounds(bounds, snapToGrid)
            if (snapObjects) {
                snapped = ObjectAlignmentSnapper.snapBounds(snapped, referenceBounds, enabled = true).bounds
            }
            return GuideSnapper.snapBounds(snapped, guides, snapGuides)
        }

        val newElements = if (ids.size <= 1) {
            val snapped = snap(bounds)
            val original = snapshot.elements.find { it.id == anchorId }?.bounds ?: return
            if (original == snapped) return
            current.elements.map {
                if (it.id == anchorId) it.copy(bounds = snapped) else it
            }
        } else if (wasResize) {
            val union = startUnion ?: return
            val snappedUnion = snap(bounds)
            if (union == snappedUnion) return
            ElementBoundsHelper.scaleSelection(
                snapshot.elements,
                ids,
                union,
                snappedUnion,
                current.pageWidthPt,
                current.pageHeightPt
            ).map { element ->
                if (ids.contains(element.id)) {
                    element.copy(bounds = snap(element.bounds))
                } else {
                    element
                }
            }
        } else {
            val union = startUnion ?: return
            val snappedUnion = snap(bounds)
            val dx = snappedUnion.x - union.x
            val dy = snappedUnion.y - union.y
            if (dx == 0f && dy == 0f) return
            ElementBoundsHelper.moveByDelta(
                snapshot.elements,
                ids,
                dx,
                dy,
                current.pageWidthPt,
                current.pageHeightPt
            ).map { element ->
                if (ids.contains(element.id)) {
                    element.copy(bounds = snap(element.bounds))
                } else {
                    element
                }
            }
        }

        history.push(snapshot)
        setTemplateInternal(current.copy(elements = newElements), recordHistory = false)
        refreshHistoryState()
    }

    fun updateElement(updated: TemplateElement) {
        mutate { current ->
            current.copy(
                elements = current.elements.map {
                    if (it.id == updated.id) updated else it
                }
            )
        }
    }

    fun previewImageBoundsAdjustments(
        context: Context,
        elementIds: Set<String>
    ): List<Pair<TemplateElement, ElementBounds>> {
        val template = _template.value ?: return emptyList()
        return elementIds.mapNotNull { id ->
            val element = template.elements.find { it.id == id } ?: return@mapNotNull null
            if (element.kind != ElementKind.IMAGE) return@mapNotNull null
            val bitmap = TemplateImageBitmapResolver.resolve(
                context = context,
                element = element,
                settingsRepository = settingsRepository,
                templateId = template.id
            ) ?: return@mapNotNull null
            val suggested = TemplateImageBoundsHelper.suggestedBoundsForImage(
                bounds = element.bounds,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                scaleMode = element.style.imageScaleMode
            ) ?: return@mapNotNull null
            element to suggested
        }
    }

    fun applyImageBoundsAdjustments(adjustments: List<Pair<TemplateElement, ElementBounds>>) {
        if (adjustments.isEmpty()) return
        mutate { current ->
            adjustments.fold(current) { template, (element, bounds) ->
                TemplateImageBoundsHelper.resizeElementAndShiftBelow(
                    template,
                    element.id,
                    bounds
                )
            }
        }
    }

    fun applyElementWithOptionalLayoutShift(updated: TemplateElement, shiftLayoutBelow: Boolean) {
        mutate { current ->
            if (shiftLayoutBelow) {
                TemplateImageBoundsHelper.resizeElementAndShiftBelow(current, updated.id, updated.bounds)
            } else {
                current.copy(
                    elements = current.elements.map {
                        if (it.id == updated.id) updated else it
                    }
                )
            }
        }
    }

    fun updateTableColumns(elementId: String, columns: List<TableColumn>, borderWidthDp: Float) {
        mutate { current ->
            current.copy(
                elements = current.elements.map { element ->
                    if (element.id != elementId) return@map element
                    val content = element.content.toMutableMap()
                    content["columns"] = TemplateJsonCodec.tableColumnsToJson(columns)
                    content[TableBorderSettings.CONTENT_KEY] =
                        TableBorderSettings.formatBorderWidthDp(borderWidthDp)
                    element.copy(content = content)
                }
            )
        }
    }

    fun addElement(kind: ElementKind) {
        mutate { current ->
            val maxZ = current.elements.maxOfOrNull { it.zIndex } ?: 0
            val element = createDefaultElement(kind, maxZ + 1)
            _selectedElementIds.value = setOf(element.id)
            current.copy(elements = current.elements + element)
        }
    }

    fun removeElement(elementId: String) {
        mutate { current ->
            current.copy(elements = current.elements.filter { it.id != elementId })
        }
        _selectedElementIds.value = _selectedElementIds.value.orEmpty() - elementId
    }

    fun removeElements(elementIds: Set<String>) {
        if (elementIds.isEmpty()) return
        mutate { current ->
            current.copy(elements = current.elements.filter { it.id !in elementIds })
        }
        _selectedElementIds.value = _selectedElementIds.value.orEmpty() - elementIds
    }

    fun removeSelectedElements() {
        removeElements(_selectedElementIds.value.orEmpty())
        _selectedElementIds.value = emptySet()
    }

    fun groupSelection() {
        val ids = _selectedElementIds.value.orEmpty()
        if (ids.size < 2) return
        val groupId = UUID.randomUUID().toString()
        mutate { current ->
            current.copy(
                elements = current.elements.map { element ->
                    if (element.id in ids) element.copy(groupId = groupId) else element
                }
            )
        }
    }

    fun ungroupSelection() {
        val ids = _selectedElementIds.value.orEmpty()
        if (ids.isEmpty()) return
        mutate { current ->
            current.copy(
                elements = current.elements.map { element ->
                    if (element.id in ids) element.copy(groupId = null) else element
                }
            )
        }
    }

    fun toggleLockSelection() {
        val ids = _selectedElementIds.value.orEmpty()
        if (ids.isEmpty()) return
        val elements = _template.value?.elements.orEmpty()
        val lock = !ElementSelectionHelper.allSelectedLocked(elements, ids)
        mutate { current ->
            current.copy(
                elements = current.elements.map { element ->
                    if (element.id in ids) element.copy(locked = lock) else element
                }
            )
        }
    }

    fun applyBulkUpdates(
        elementIds: Set<String>,
        updates: ElementInspectorBottomSheet.BulkInspectorUpdates
    ) {
        if (elementIds.isEmpty()) return
        mutate { current ->
            current.copy(
                elements = current.elements.map { element ->
                    if (element.id !in elementIds) return@map element
                    var updated = element
                    if (updates.x != null || updates.y != null || updates.width != null || updates.height != null) {
                        updated = updated.copy(
                            bounds = updated.bounds.copy(
                                x = updates.x ?: updated.bounds.x,
                                y = updates.y ?: updated.bounds.y,
                                width = updates.width ?: updated.bounds.width,
                                height = updates.height ?: updated.bounds.height
                            )
                        )
                    }
                    if (element.kind == ElementKind.TEXT) {
                        updated = updated.copy(
                            style = updated.style.copy(
                                fontSize = updates.fontSize ?: updated.style.fontSize,
                                bold = updates.bold ?: updated.style.bold,
                                italic = updates.italic ?: updated.style.italic,
                                underline = updates.underline ?: updated.style.underline,
                                color = updates.color ?: updated.style.color,
                                fontFamily = updates.fontFamily ?: updated.style.fontFamily,
                                textAlign = updates.textAlign ?: updated.style.textAlign,
                                verticalAlign = updates.verticalAlign ?: updated.style.verticalAlign
                            )
                        )
                    }
                    if (element.kind == ElementKind.IMAGE) {
                        updated = updated.copy(
                            style = updated.style.copy(
                                textAlign = updates.textAlign ?: updated.style.textAlign,
                                verticalAlign = updates.verticalAlign ?: updated.style.verticalAlign,
                                imageScaleMode = updates.imageScaleMode ?: updated.style.imageScaleMode
                            )
                        )
                    }
                    if (updates.locked != null) {
                        updated = updated.copy(locked = updates.locked)
                    }
                    updated
                }
            )
        }
    }

    fun duplicateSelectedElement() {
        val selected = getSelectedElement() ?: return
        mutate { current ->
            val maxZ = current.elements.maxOfOrNull { it.zIndex } ?: 0
            val copy = selected.copy(
                id = UUID.randomUUID().toString(),
                groupId = null,
                bounds = selected.bounds.copy(
                    x = (selected.bounds.x + 16f).coerceAtMost(
                        current.pageWidthPt - selected.bounds.width
                    ),
                    y = (selected.bounds.y + 16f).coerceAtMost(
                        current.pageHeightPt - selected.bounds.height
                    )
                ),
                zIndex = maxZ + 1
            )
            _selectedElementIds.value = setOf(copy.id)
            current.copy(elements = current.elements + copy)
        }
    }

    fun bringForward() = moveLayer(ElementZOrder.Direction.FORWARD)

    fun sendBackward() = moveLayer(ElementZOrder.Direction.BACKWARD)

    fun bringToFront() = moveLayer(ElementZOrder.Direction.TO_FRONT)

    fun sendToBack() = moveLayer(ElementZOrder.Direction.TO_BACK)

    private fun moveLayer(direction: ElementZOrder.Direction) {
        val ids = _selectedElementIds.value.orEmpty()
        if (ids.isEmpty()) return
        mutate { current ->
            var elements = current.elements
            val orderedIds = elements
                .filter { it.id in ids }
                .sortedBy { it.zIndex }
                .map { it.id }
            orderedIds.forEach { elementId ->
                elements = ElementZOrder.reorder(elements, elementId, direction)
            }
            current.copy(elements = elements)
        }
    }

    fun loadVersionHistory() {
        val templateId = _template.value?.id ?: return
        if (templateId <= 0L) return
        viewModelScope.launch {
            _versionHistory.value = templateRepository.getVersions(templateId)
        }
    }

    fun restoreVersion(versionId: Long) {
        viewModelScope.launch {
            val restored = templateRepository.restoreFromVersion(versionId) ?: run {
                _restoreResult.value = false
                return@launch
            }
            val current = _template.value
            if (current != null) {
                history.push(current)
            }
            setTemplateInternal(restored, recordHistory = false)
            refreshHistoryState()
            _restoreResult.value = true
        }
    }

    fun undo() {
        val current = _template.value ?: return
        val previous = history.undo(current) ?: return
        setTemplateInternal(previous, recordHistory = false)
        refreshHistoryState()
    }

    fun redo() {
        val current = _template.value ?: return
        val next = history.redo(current) ?: return
        setTemplateInternal(next, recordHistory = false)
        refreshHistoryState()
    }

    fun setLivePreview(enabled: Boolean) {
        _livePreview.value = enabled
        editorPreferences.livePreview = enabled
    }

    fun setSnapToGrid(enabled: Boolean) {
        _snapToGrid.value = enabled
        editorPreferences.snapToGrid = enabled
    }

    fun setShowGrid(enabled: Boolean) {
        _showGrid.value = enabled
        editorPreferences.showGrid = enabled
    }

    fun setSnapToGuides(enabled: Boolean) {
        _snapToGuides.value = enabled
        editorPreferences.snapToGuides = enabled
    }

    fun setShowGuides(enabled: Boolean) {
        _showGuides.value = enabled
        editorPreferences.showGuides = enabled
    }

    fun setSnapToObjects(enabled: Boolean) {
        _snapToObjects.value = enabled
        editorPreferences.snapToObjects = enabled
    }

    fun getSelectedElement(): TemplateElement? {
        val id = _selectedElementIds.value?.firstOrNull() ?: return null
        return _template.value?.elements?.find { it.id == id }
    }

    fun getSelectedElements(): List<TemplateElement> {
        val ids = _selectedElementIds.value.orEmpty()
        val elements = _template.value?.elements.orEmpty()
        return elements.filter { it.id in ids }
    }

    fun isSelectionLocked(): Boolean {
        return ElementSelectionHelper.allSelectedLocked(
            _template.value?.elements.orEmpty(),
            _selectedElementIds.value.orEmpty()
        )
    }

    fun isSelectionGrouped(): Boolean {
        return ElementSelectionHelper.isGroupSelected(
            _template.value?.elements.orEmpty(),
            _selectedElementIds.value.orEmpty()
        )
    }

    fun validationElementIds(): Set<String> {
        return _validationIssues.value.orEmpty().mapNotNull { it.elementId }.toSet()
    }

    fun hasUnsavedChanges(): Boolean {
        val current = _template.value ?: return false
        val saved = savedSnapshot ?: return false
        return templateSnapshot(current) != saved
    }

    fun saveTemplate(onSaved: (() -> Unit)? = null) {
        val current = _template.value ?: return
        viewModelScope.launch {
            templateRepository.update(current)
            val saved = current.copy(
                version = current.version + 1,
                updatedAt = System.currentTimeMillis()
            )
            _template.value = saved
            markSaved(saved)
            loadVersionHistory()
            _saveResult.value = true
            onSaved?.invoke()
        }
    }

    fun exportPreviewPdf(context: Context, onResult: (File?) -> Unit) {
        val current = _template.value ?: run {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val file = PdfGenerator.generateBillPdf(
                context = context,
                template = current,
                bill = SampleBillFactory.createSample(),
                shopName = settingsRepository.shopName,
                currencySymbol = settingsRepository.currencySymbol,
                headerImage = settingsRepository.getHeaderImage(),
                signatureImage = settingsRepository.getSignatureImage(),
                shopLogoImage = settingsRepository.getShopLogoImage()
            )
            onResult(file)
        }
    }

    fun exportTemplateJson(context: Context, onResult: (File?) -> Unit) {
        val current = _template.value ?: run {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
                val safeName = current.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val out = File(dir, "template_${safeName}.json")
                out.writeText(TemplateJsonCodec.toJson(current))
                out
            }
            onResult(file)
        }
    }

    fun importTemplateJson(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: return@withContext null
                    TemplateJsonCodec.fromJson(json)
                } catch (_: Exception) {
                    null
                }
            }
            if (imported == null) {
                onResult(false)
                return@launch
            }
            mutate { imported.copy(id = _template.value?.id ?: imported.id) }
            onResult(true)
        }
    }

    private fun mutate(transform: (InvoiceTemplate) -> InvoiceTemplate) {
        val current = _template.value ?: return
        history.push(current)
        setTemplateInternal(transform(current), recordHistory = false)
        refreshHistoryState()
    }

    private fun setTemplateInternal(updated: InvoiceTemplate, recordHistory: Boolean) {
        if (recordHistory) {
            _template.value?.let { history.push(it) }
        }
        _template.value = updated
        _validationIssues.value = TemplateLayoutValidator.validate(updated)
    }

    private fun refreshHistoryState() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    private fun templateSnapshot(template: InvoiceTemplate): String {
        return TemplateJsonCodec.toJson(
            template.copy(id = 0, version = 0, updatedAt = 0)
        )
    }

    private fun markSaved(template: InvoiceTemplate) {
        savedSnapshot = templateSnapshot(template)
    }

    private fun createDefaultElement(kind: ElementKind, zIndex: Int): TemplateElement {
        val id = UUID.randomUUID().toString()
        return when (kind) {
            ElementKind.TEXT -> TemplateElement(
                id = id,
                kind = ElementKind.TEXT,
                binding = ElementBinding.STATIC,
                bounds = ElementBounds(40f, 40f + zIndex * 8, 220f, 24f),
                zIndex = zIndex,
                style = ElementStyle(fontSize = 12f),
                content = mapOf("text" to "New text")
            )
            ElementKind.IMAGE -> TemplateElement(
                id = id,
                kind = ElementKind.IMAGE,
                binding = ElementBinding.DYNAMIC,
                bounds = ElementBounds(40f, 40f + zIndex * 8, 150f, 60f),
                zIndex = zIndex,
                style = ElementStyle(imageScaleMode = ImageScaleMode.FIT_WIDTH),
                content = mapOf("bindingKey" to DataBindingKey.HEADER_IMAGE.name)
            )
            ElementKind.LINE -> TemplateElement(
                id = id,
                kind = ElementKind.LINE,
                binding = ElementBinding.STATIC,
                bounds = ElementBounds(40f, 40f + zIndex * 8, 515f, 1f),
                zIndex = zIndex
            )
            ElementKind.RECT -> TemplateElement(
                id = id,
                kind = ElementKind.RECT,
                binding = ElementBinding.STATIC,
                bounds = ElementBounds(40f, 40f + zIndex * 8, 200f, 80f),
                zIndex = zIndex
            )
            ElementKind.TABLE -> TemplateElement(
                id = id,
                kind = ElementKind.TABLE,
                binding = ElementBinding.DYNAMIC,
                bounds = ElementBounds(40f, 300f, 515f, 240f),
                zIndex = zIndex,
                content = mapOf(
                    "bindingKey" to DataBindingKey.BILL_ITEMS.name,
                    "showHeader" to "true",
                    "columns" to TemplateJsonCodec.tableColumnsToJson(defaultTableColumns()),
                    TableBorderSettings.CONTENT_KEY to TableBorderSettings.formatBorderWidthDp(
                        TableBorderSettings.DEFAULT_DP
                    )
                )
            )
            ElementKind.SPACER -> TemplateElement(
                id = id,
                kind = ElementKind.SPACER,
                binding = ElementBinding.STATIC,
                bounds = ElementBounds(40f, 40f + zIndex * 8, 100f, 24f),
                zIndex = zIndex
            )
        }
    }

    private fun defaultTableColumns(): List<TableColumn> {
        return listOf(
            TableColumn("sl", "Sl", 7f, TextAlign.CENTER),
            TableColumn("name", "Particulars", 43f, TextAlign.LEFT),
            TableColumn("quantity", "Qty", 11f, TextAlign.CENTER),
            TableColumn("mrp", "MRP", 11f, TextAlign.CENTER),
            TableColumn("discount", "Disc%", 11f, TextAlign.CENTER),
            TableColumn("lineTotal", "Price", 17f, TextAlign.CENTER)
        )
    }
}
