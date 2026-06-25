package com.loctell.vikrsaathi.ui.settings.invoicebuilder

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loctell.vikrsaathi.data.model.template.DataBindingKey
import com.loctell.vikrsaathi.data.model.template.ElementBinding
import com.loctell.vikrsaathi.data.model.template.ElementBounds
import com.loctell.vikrsaathi.data.model.template.ElementKind
import com.loctell.vikrsaathi.data.model.template.ElementStyle
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplateVersion
import com.loctell.vikrsaathi.data.model.template.TableColumn
import com.loctell.vikrsaathi.data.model.template.TemplateElement
import com.loctell.vikrsaathi.data.model.template.TemplateJsonCodec
import com.loctell.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.loctell.vikrsaathi.data.repository.SettingsRepository
import com.loctell.vikrsaathi.domain.template.ElementZOrder
import com.loctell.vikrsaathi.domain.template.GridSnapper
import com.loctell.vikrsaathi.domain.template.SampleBillFactory
import com.loctell.vikrsaathi.domain.template.TemplateLayoutValidator
import com.loctell.vikrsaathi.domain.template.TemplateContextFactory
import com.loctell.vikrsaathi.domain.template.TemplateRenderContext
import com.loctell.vikrsaathi.domain.template.TemplateValidationIssue
import com.loctell.vikrsaathi.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class InvoiceBuilderViewModel(
    private val templateRepository: InvoiceTemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val editorPreferences: com.loctell.vikrsaathi.util.InvoiceBuilderPreferences
) : ViewModel() {

    private val history = TemplateHistory()
    private var dragSnapshot: InvoiceTemplate? = null

    private val _template = MutableLiveData<InvoiceTemplate>()
    val template: LiveData<InvoiceTemplate> = _template

    private val _selectedElementId = MutableLiveData<String?>()
    val selectedElementId: LiveData<String?> = _selectedElementId

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

    private val _versionHistory = MutableLiveData<List<InvoiceTemplateVersion>>(emptyList())
    val versionHistory: LiveData<List<InvoiceTemplateVersion>> = _versionHistory

    private val _restoreResult = MutableLiveData<Boolean>()
    val restoreResult: LiveData<Boolean> = _restoreResult

    init {
        _livePreview.value = editorPreferences.livePreview
        _snapToGrid.value = editorPreferences.snapToGrid
        _showGrid.value = editorPreferences.showGrid
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
            _selectedElementId.value = null
            refreshHistoryState()
        }
    }

    fun selectElement(elementId: String?) {
        _selectedElementId.value = elementId
    }

    fun onBoundsChangeStarted() {
        dragSnapshot = _template.value?.copy()
    }

    fun updateElementBounds(elementId: String, bounds: ElementBounds) {
        val current = _template.value ?: return
        setTemplateInternal(
            current.copy(
                elements = current.elements.map {
                    if (it.id == elementId) it.copy(bounds = bounds) else it
                }
            ),
            recordHistory = false
        )
    }

    fun onBoundsChangeFinished(elementId: String, bounds: ElementBounds) {
        val snapshot = dragSnapshot
        dragSnapshot = null
        if (snapshot == null) return

        val snapped = GridSnapper.snapBounds(bounds, _snapToGrid.value == true)
        val originalBounds = snapshot.elements.find { it.id == elementId }?.bounds
        if (originalBounds == snapped) {
            return
        }

        history.push(snapshot)
        val current = _template.value ?: return
        setTemplateInternal(
            current.copy(
                elements = current.elements.map {
                    if (it.id == elementId) it.copy(bounds = snapped) else it
                }
            ),
            recordHistory = false
        )
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

    fun updateTableColumns(elementId: String, columns: List<TableColumn>) {
        mutate { current ->
            current.copy(
                elements = current.elements.map { element ->
                    if (element.id != elementId) return@map element
                    val content = element.content.toMutableMap()
                    content["columns"] = TemplateJsonCodec.tableColumnsToJson(columns)
                    element.copy(content = content)
                }
            )
        }
    }

    fun addElement(kind: ElementKind) {
        mutate { current ->
            val maxZ = current.elements.maxOfOrNull { it.zIndex } ?: 0
            val element = createDefaultElement(kind, maxZ + 1)
            _selectedElementId.value = element.id
            current.copy(elements = current.elements + element)
        }
    }

    fun removeElement(elementId: String) {
        mutate { current ->
            current.copy(elements = current.elements.filter { it.id != elementId })
        }
        if (_selectedElementId.value == elementId) {
            _selectedElementId.value = null
        }
    }

    fun removeSelectedElement() {
        val id = _selectedElementId.value ?: return
        removeElement(id)
    }

    fun duplicateSelectedElement() {
        val selected = getSelectedElement() ?: return
        mutate { current ->
            val maxZ = current.elements.maxOfOrNull { it.zIndex } ?: 0
            val copy = selected.copy(
                id = UUID.randomUUID().toString(),
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
            _selectedElementId.value = copy.id
            current.copy(elements = current.elements + copy)
        }
    }

    fun bringForward() = moveLayer(ElementZOrder.Direction.FORWARD)

    fun sendBackward() = moveLayer(ElementZOrder.Direction.BACKWARD)

    fun bringToFront() = moveLayer(ElementZOrder.Direction.TO_FRONT)

    fun sendToBack() = moveLayer(ElementZOrder.Direction.TO_BACK)

    private fun moveLayer(direction: ElementZOrder.Direction) {
        val elementId = _selectedElementId.value ?: return
        mutate { current ->
            current.copy(
                elements = ElementZOrder.reorder(current.elements, elementId, direction)
            )
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

    fun getSelectedElement(): TemplateElement? {
        val id = _selectedElementId.value ?: return null
        return _template.value?.elements?.find { it.id == id }
    }

    fun validationElementIds(): Set<String> {
        return _validationIssues.value.orEmpty().mapNotNull { it.elementId }.toSet()
    }

    fun saveTemplate() {
        val current = _template.value ?: return
        viewModelScope.launch {
            templateRepository.update(current)
            _template.value = current.copy(
                version = current.version + 1,
                updatedAt = System.currentTimeMillis()
            )
            loadVersionHistory()
            _saveResult.value = true
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
                    "columns" to TemplateJsonCodec.tableColumnsToJson(defaultTableColumns())
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
            TableColumn("sl", "Sl", 8f),
            TableColumn("name", "Particulars", 40f),
            TableColumn("mrp", "MRP", 15f),
            TableColumn("discount", "Disc%", 12f),
            TableColumn("lineTotal", "Price", 25f)
        )
    }
}
