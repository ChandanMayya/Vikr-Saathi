package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.ElementStyle
import com.kex.vikrsaathi.data.model.template.FontFamily
import com.kex.vikrsaathi.data.model.template.ImageScaleMode
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TextAlign
import com.kex.vikrsaathi.data.model.template.VerticalAlign
import com.kex.vikrsaathi.databinding.BottomSheetElementInspectorBinding
import com.kex.vikrsaathi.domain.template.TemplateImageBitmapResolver
import com.kex.vikrsaathi.domain.template.TemplateImageBoundsHelper
import com.kex.vikrsaathi.domain.template.TableBorderSettings
import com.kex.vikrsaathi.util.TemplateImageStore

class ElementInspectorBottomSheet : BottomSheetDialogFragment() {

    data class BulkInspectorUpdates(
        val x: Float? = null,
        val y: Float? = null,
        val width: Float? = null,
        val height: Float? = null,
        val fontSize: Float? = null,
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val color: String? = null,
        val fontFamily: FontFamily? = null,
        val textAlign: TextAlign? = null,
        val verticalAlign: VerticalAlign? = null,
        val imageScaleMode: ImageScaleMode? = null,
        val locked: Boolean? = null
    )

    interface Callback {
        fun onApply(element: TemplateElement, shiftLayoutBelow: Boolean = false)
        fun onApplyBulk(elementIds: Set<String>, updates: BulkInspectorUpdates)
        fun onDelete(elementIds: Set<String>)
    }

    private var _binding: BottomSheetElementInspectorBinding? = null
    private val binding get() = _binding!!

    private var templateId: Long = 0L
    private var element: TemplateElement? = null
    private var bulkElements: List<TemplateElement>? = null
    var callback: Callback? = null

    private var pendingStaticBitmap: Bitmap? = null
    private var selectedBindingType = ElementBinding.STATIC.name
    private var selectedDataField: String? = null
    private var selectedHorizontalAlign = TextAlign.LEFT.name
    private var selectedVerticalAlign = VerticalAlign.TOP.name
    private var selectedImageScaleMode = ImageScaleMode.FIT.name
    private var selectedFontFamily = FontFamily.DEFAULT.name

    private val isBulkMode get() = bulkElements.orEmpty().size > 1

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (isBulkMode) return@registerForActivityResult
        uri ?: return@registerForActivityResult
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return@registerForActivityResult
        pendingStaticBitmap = bitmap
        binding.imageStaticPreview.setImageBitmap(bitmap)
        binding.textStaticImageHint.isVisible = false
        element?.let { current ->
            if (current.kind == ElementKind.IMAGE) {
                offerStaticImageFieldAdjust(current, bitmap)
            }
        }
    }

    private fun offerStaticImageFieldAdjust(current: TemplateElement, bitmap: Bitmap) {
        ImageBoundsAdjustDialog.confirmElementBoundsIfNeeded(
            fragment = this,
            element = current,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        ) { adjusted, _ ->
            binding.editWidth.setText(adjusted.bounds.width.toString())
            binding.editHeight.setText(adjusted.bounds.height.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetElementInspectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = element
        val bulk = bulkElements
        if (current == null && bulk.isNullOrEmpty()) return

        if (isBulkMode) {
            setupBulkMode(bulk!!)
        } else if (current != null) {
            setupSingleMode(current)
        }

        binding.buttonApplyInspector.setOnClickListener {
            if (isBulkMode) {
                applyBulkChanges(bulk!!)
            } else if (current != null) {
                applyChanges(current)
            }
        }
        binding.buttonDeleteElement.setOnClickListener {
            val ids = if (isBulkMode) bulk!!.map { it.id }.toSet() else setOf(current!!.id)
            callback?.onDelete(ids)
            dismiss()
        }
    }

    private fun setupBulkMode(elements: List<TemplateElement>) {
        binding.textElementKind.text = getString(R.string.n_elements_selected, elements.size)
        binding.textBulkHint.isVisible = true
        binding.switchLocked.isVisible = true
        binding.switchLocked.isChecked = elements.all { it.locked }
        binding.buttonDeleteElement.text = getString(R.string.delete_selected)

        hideKindSpecificFields()

        val allText = elements.all { it.kind == ElementKind.TEXT }
        val allImage = elements.all { it.kind == ElementKind.IMAGE }
        binding.layoutTextStyle.isVisible = allText
        binding.layoutAlignment.isVisible = allText || allImage
        binding.layoutImageScale.isVisible = allImage

        if (allImage) {
            setupAlignmentDropdowns(elements.first())
            if (elements.map { it.style.textAlign }.distinct().size != 1) {
                binding.spinnerHorizontalAlign.setText("", false)
                selectedHorizontalAlign = elements.first().style.textAlign.name
            }
            if (elements.map { it.style.verticalAlign }.distinct().size != 1) {
                binding.spinnerVerticalAlign.setText("", false)
                selectedVerticalAlign = elements.first().style.verticalAlign.name
            }
            setupImageScaleDropdown(elements.first())
            if (elements.map { it.style.imageScaleMode }.distinct().size != 1) {
                binding.spinnerImageScale.setText("", false)
                selectedImageScaleMode = elements.first().style.imageScaleMode.name
            }
        } else {
            binding.layoutAlignment.isVisible = false
        }

        binding.editPosX.setText(mixedFloatText(elements) { it.bounds.x })
        binding.editPosY.setText(mixedFloatText(elements) { it.bounds.y })
        binding.editWidth.setText(mixedFloatText(elements) { it.bounds.width })
        binding.editHeight.setText(mixedFloatText(elements) { it.bounds.height })

        if (allText) {
            binding.editFontSize.setText(mixedFloatText(elements) { it.style.fontSize })
            binding.switchBold.isChecked = elements.all { it.style.bold }
            binding.switchItalic.isChecked = elements.all { it.style.italic }
            binding.switchUnderline.isChecked = elements.all { it.style.underline }
            binding.editFontColor.setText(mixedStringText(elements) { it.style.color })
        }
    }

    private fun setupSingleMode(current: TemplateElement) {
        binding.textElementKind.text = current.kind.name
        binding.textBulkHint.isVisible = false
        binding.switchLocked.isVisible = true
        binding.switchLocked.isChecked = current.locked

        setupBindingTypeDropdown(current)
        setupDataFieldDropdown(current)
        setupAlignmentDropdowns(current)
        setupImageScaleDropdown(current)
        setupFontFamilyDropdown(current)
        loadCurrentValues(current)
    }

    private fun hideKindSpecificFields() {
        binding.spinnerBindingType.isVisible = false
        (binding.spinnerBindingType.parent as? View)?.isVisible = false
        binding.layoutStaticText.isVisible = false
        binding.layoutBindingKey.isVisible = false
        binding.layoutStaticImage.isVisible = false
        binding.layoutPrefix.isVisible = false
    }

    private fun mixedFloatText(elements: List<TemplateElement>, selector: (TemplateElement) -> Float): String {
        val values = elements.map(selector).distinct()
        return if (values.size == 1) values.first().toString() else ""
    }

    private fun mixedStringText(elements: List<TemplateElement>, selector: (TemplateElement) -> String): String {
        val values = elements.map(selector).distinct()
        return if (values.size == 1) values.first() else ""
    }

    private fun setupBindingTypeDropdown(current: TemplateElement) {
        val options = BindingOptionLabels.bindingTypes()
        val labels = options.map { it.label }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        binding.spinnerBindingType.setAdapter(adapter)
        binding.spinnerBindingType.setText(
            BindingOptionLabels.labelForBindingType(current.binding.name),
            false
        )
        selectedBindingType = current.binding.name

        binding.spinnerBindingType.setOnItemClickListener { _, _, position, _ ->
            selectedBindingType = options[position].value
            updateFieldVisibility(current.kind)
        }
        binding.spinnerBindingType.setOnClickListener { binding.spinnerBindingType.showDropDown() }
    }

    private fun setupDataFieldDropdown(current: TemplateElement) {
        val options = if (current.kind == ElementKind.IMAGE) {
            BindingOptionLabels.imageDataFields()
        } else {
            BindingOptionLabels.textDataFields()
        }
        val labels = options.map { it.label }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        binding.spinnerBindingKey.setAdapter(adapter)

        val currentKey = current.content["bindingKey"].orEmpty()
        val initialIndex = options.indexOfFirst { it.value == currentKey }.coerceAtLeast(0)
        binding.spinnerBindingKey.setText(labels[initialIndex], false)
        selectedDataField = options[initialIndex].value

        binding.spinnerBindingKey.setOnItemClickListener { _, _, position, _ ->
            selectedDataField = options[position].value
        }
        binding.spinnerBindingKey.setOnClickListener { binding.spinnerBindingKey.showDropDown() }
    }

    private fun setupAlignmentDropdowns(current: TemplateElement) {
        val horizontal = StyleOptionLabels.horizontalAligns()
        val horizontalLabels = horizontal.map { it.label }
        binding.spinnerHorizontalAlign.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, horizontalLabels)
        )
        selectedHorizontalAlign = current.style.textAlign.name
        binding.spinnerHorizontalAlign.setText(
            StyleOptionLabels.labelForHorizontal(selectedHorizontalAlign),
            false
        )
        binding.spinnerHorizontalAlign.setOnItemClickListener { _, _, position, _ ->
            selectedHorizontalAlign = horizontal[position].value
        }
        binding.spinnerHorizontalAlign.setOnClickListener { binding.spinnerHorizontalAlign.showDropDown() }

        val vertical = StyleOptionLabels.verticalAligns()
        val verticalLabels = vertical.map { it.label }
        binding.spinnerVerticalAlign.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, verticalLabels)
        )
        selectedVerticalAlign = current.style.verticalAlign.name
        binding.spinnerVerticalAlign.setText(
            StyleOptionLabels.labelForVertical(selectedVerticalAlign),
            false
        )
        binding.spinnerVerticalAlign.setOnItemClickListener { _, _, position, _ ->
            selectedVerticalAlign = vertical[position].value
        }
        binding.spinnerVerticalAlign.setOnClickListener { binding.spinnerVerticalAlign.showDropDown() }
    }

    private fun setupImageScaleDropdown(current: TemplateElement) {
        val options = StyleOptionLabels.imageScaleModes()
        val labels = options.map { it.label }
        binding.spinnerImageScale.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        selectedImageScaleMode = current.style.imageScaleMode.name
        binding.spinnerImageScale.setText(
            StyleOptionLabels.labelForImageScaleMode(selectedImageScaleMode),
            false
        )
        binding.spinnerImageScale.setOnItemClickListener { _, _, position, _ ->
            selectedImageScaleMode = options[position].value
        }
        binding.spinnerImageScale.setOnClickListener { binding.spinnerImageScale.showDropDown() }
    }

    private fun setupFontFamilyDropdown(current: TemplateElement) {
        val options = StyleOptionLabels.fontFamilies()
        val labels = options.map { it.label }
        binding.spinnerFontFamily.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        selectedFontFamily = current.style.fontFamily.name
        binding.spinnerFontFamily.setText(
            StyleOptionLabels.labelForFontFamily(selectedFontFamily),
            false
        )
        binding.spinnerFontFamily.setOnItemClickListener { _, _, position, _ ->
            selectedFontFamily = options[position].value
        }
        binding.spinnerFontFamily.setOnClickListener { binding.spinnerFontFamily.showDropDown() }
    }

    private fun loadCurrentValues(current: TemplateElement) {
        binding.editStaticText.setText(current.content["text"].orEmpty())
        binding.editPrefix.setText(current.content["prefix"].orEmpty())
        binding.editFontSize.setText(current.style.fontSize.toString())
        binding.switchBold.isChecked = current.style.bold
        binding.switchItalic.isChecked = current.style.italic
        binding.switchUnderline.isChecked = current.style.underline
        binding.editFontColor.setText(current.style.color)

        binding.editPosX.setText(current.bounds.x.toString())
        binding.editPosY.setText(current.bounds.y.toString())
        binding.editWidth.setText(current.bounds.width.toString())
        binding.editHeight.setText(current.bounds.height.toString())

        if (current.kind == ElementKind.TABLE) {
            binding.editTableBorderWidth.setText(
                TableBorderSettings.parseBorderWidthDp(current.content).toString()
            )
        }

        if (current.kind == ElementKind.IMAGE && current.binding == ElementBinding.STATIC) {
            val path = current.content["imagePath"]
            if (!path.isNullOrBlank()) {
                TemplateImageStore.load(requireContext(), path)?.let { bitmap ->
                    binding.imageStaticPreview.setImageBitmap(bitmap)
                    binding.textStaticImageHint.isVisible = false
                }
            }
        }

        updateFieldVisibility(current.kind)
    }

    private fun updateFieldVisibility(kind: ElementKind) {
        val isText = kind == ElementKind.TEXT
        val isImage = kind == ElementKind.IMAGE
        val isTable = kind == ElementKind.TABLE
        val isDynamic = selectedBindingType == ElementBinding.DYNAMIC.name

        binding.layoutStaticText.isVisible = isText && !isDynamic
        binding.layoutBindingKey.isVisible = isDynamic && (isText || isImage)
        binding.layoutStaticImage.isVisible = isImage && !isDynamic
        binding.layoutPrefix.isVisible = isText && isDynamic
        binding.layoutTextStyle.isVisible = isText
        binding.layoutAlignment.isVisible = isText || isImage
        binding.layoutImageScale.isVisible = isImage
        binding.layoutTableBorderWidth.isVisible = isTable
        (binding.spinnerBindingType.parent as? View)?.isVisible = !isTable
    }

    private fun applyBulkChanges(elements: List<TemplateElement>) {
        val ids = elements.map { it.id }.toSet()
        val allText = elements.all { it.kind == ElementKind.TEXT }
        val allImage = elements.all { it.kind == ElementKind.IMAGE }
        val colorInput = binding.editFontColor.text.toString().trim()
        val resolvedColor = if (allText && colorInput.isNotBlank()) {
            parseColorOrDefault(colorInput, "#000000")
        } else {
            null
        }

        callback?.onApplyBulk(
            ids,
            BulkInspectorUpdates(
                x = binding.editPosX.text.toString().toFloatOrNull(),
                y = binding.editPosY.text.toString().toFloatOrNull(),
                width = binding.editWidth.text.toString().toFloatOrNull(),
                height = binding.editHeight.text.toString().toFloatOrNull(),
                fontSize = if (allText) binding.editFontSize.text.toString().toFloatOrNull() else null,
                bold = if (allText) binding.switchBold.isChecked else null,
                italic = if (allText) binding.switchItalic.isChecked else null,
                underline = if (allText) binding.switchUnderline.isChecked else null,
                color = resolvedColor,
                textAlign = if (allImage) {
                    runCatching { TextAlign.valueOf(selectedHorizontalAlign) }.getOrNull()
                } else {
                    null
                },
                verticalAlign = if (allImage) {
                    runCatching { VerticalAlign.valueOf(selectedVerticalAlign) }.getOrNull()
                } else {
                    null
                },
                imageScaleMode = if (allImage) {
                    runCatching { ImageScaleMode.valueOf(selectedImageScaleMode) }.getOrNull()
                } else {
                    null
                },
                locked = binding.switchLocked.isChecked
            )
        )
        dismiss()
    }

    private fun applyChanges(original: TemplateElement) {
        val bindingType = ElementBinding.valueOf(selectedBindingType)
        val content = original.content.toMutableMap()

        when (original.kind) {
            ElementKind.TEXT -> {
                if (bindingType == ElementBinding.STATIC) {
                    content["text"] = binding.editStaticText.text.toString()
                    content.remove("bindingKey")
                } else {
                    content["bindingKey"] = selectedDataField.orEmpty()
                    content["prefix"] = binding.editPrefix.text.toString()
                    content.remove("text")
                }
            }
            ElementKind.IMAGE -> {
                if (bindingType == ElementBinding.STATIC) {
                    content.remove("bindingKey")
                    pendingStaticBitmap?.let { bitmap ->
                        val path = TemplateImageStore.save(
                            requireContext(),
                            templateId,
                            original.id,
                            bitmap
                        )
                        content["imagePath"] = path
                    }
                } else {
                    content["bindingKey"] = selectedDataField.orEmpty()
                    content.remove("imagePath")
                }
            }
            ElementKind.TABLE -> {
                val borderWidthDp = binding.editTableBorderWidth.text.toString().toFloatOrNull()
                    ?.coerceIn(TableBorderSettings.MIN_DP, TableBorderSettings.MAX_DP)
                    ?: TableBorderSettings.DEFAULT_DP
                content[TableBorderSettings.CONTENT_KEY] =
                    TableBorderSettings.formatBorderWidthDp(borderWidthDp)
            }
            else -> Unit
        }

        val colorInput = binding.editFontColor.text.toString().trim()
        val resolvedColor = if (original.kind == ElementKind.TEXT) {
            parseColorOrDefault(colorInput, original.style.color)
        } else {
            original.style.color
        }

        val updated = original.copy(
            binding = bindingType,
            locked = binding.switchLocked.isChecked,
            bounds = ElementBounds(
                x = binding.editPosX.text.toString().toFloatOrNull() ?: original.bounds.x,
                y = binding.editPosY.text.toString().toFloatOrNull() ?: original.bounds.y,
                width = binding.editWidth.text.toString().toFloatOrNull() ?: original.bounds.width,
                height = binding.editHeight.text.toString().toFloatOrNull() ?: original.bounds.height
            ),
            style = original.style.copy(
                fontSize = binding.editFontSize.text.toString().toFloatOrNull() ?: original.style.fontSize,
                bold = binding.switchBold.isChecked,
                italic = binding.switchItalic.isChecked,
                underline = binding.switchUnderline.isChecked,
                textAlign = runCatching { TextAlign.valueOf(selectedHorizontalAlign) }
                    .getOrDefault(original.style.textAlign),
                verticalAlign = runCatching { VerticalAlign.valueOf(selectedVerticalAlign) }
                    .getOrDefault(original.style.verticalAlign),
                color = resolvedColor,
                fontFamily = runCatching { FontFamily.valueOf(selectedFontFamily) }
                    .getOrDefault(original.style.fontFamily),
                imageScaleMode = if (original.kind == ElementKind.IMAGE) {
                    runCatching { ImageScaleMode.valueOf(selectedImageScaleMode) }
                        .getOrDefault(original.style.imageScaleMode)
                } else {
                    original.style.imageScaleMode
                }
            ),
            content = content
        )
        confirmBoundsAndApply(updated)
    }

    private fun confirmBoundsAndApply(element: TemplateElement) {
        val bitmap = resolveElementBitmap(element)
        if (bitmap == null || element.kind != ElementKind.IMAGE) {
            callback?.onApply(element, false)
            dismiss()
            return
        }
        ImageBoundsAdjustDialog.confirmElementBoundsIfNeeded(
            fragment = this,
            element = element,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        ) { adjusted, shiftLayout ->
            callback?.onApply(adjusted, shiftLayout)
            dismiss()
        }
    }

    private fun resolveElementBitmap(element: TemplateElement): Bitmap? {
        if (element.kind != ElementKind.IMAGE) return null
        if (element.binding == ElementBinding.STATIC && pendingStaticBitmap != null) {
            return pendingStaticBitmap
        }
        val app = requireActivity().application as VikrSaathiApp
        return TemplateImageBitmapResolver.resolve(
            context = requireContext(),
            element = element,
            settingsRepository = app.settingsRepository,
            templateId = templateId
        )
    }

    private fun parseColorOrDefault(input: String, fallback: String): String {
        if (input.isBlank()) return fallback
        val normalized = if (input.startsWith("#")) input else "#$input"
        return runCatching {
            Color.parseColor(normalized)
            normalized.uppercase()
        }.getOrDefault(fallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(templateId: Long, element: TemplateElement): ElementInspectorBottomSheet {
            return ElementInspectorBottomSheet().apply {
                this.templateId = templateId
                this.element = element
            }
        }

        fun newInstanceForBulk(elements: List<TemplateElement>): ElementInspectorBottomSheet {
            return ElementInspectorBottomSheet().apply {
                this.bulkElements = elements
                this.element = elements.firstOrNull()
            }
        }
    }
}
