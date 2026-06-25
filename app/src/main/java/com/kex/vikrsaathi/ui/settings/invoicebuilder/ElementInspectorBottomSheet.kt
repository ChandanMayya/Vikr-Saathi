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
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.ElementStyle
import com.kex.vikrsaathi.data.model.template.FontFamily
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TextAlign
import com.kex.vikrsaathi.data.model.template.VerticalAlign
import com.kex.vikrsaathi.databinding.BottomSheetElementInspectorBinding
import com.kex.vikrsaathi.util.TemplateImageStore

class ElementInspectorBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onApply(element: TemplateElement)
        fun onDelete(elementId: String)
    }

    private var _binding: BottomSheetElementInspectorBinding? = null
    private val binding get() = _binding!!

    private var templateId: Long = 0L
    private var element: TemplateElement? = null
    var callback: Callback? = null

    private var pendingStaticBitmap: Bitmap? = null
    private var selectedBindingType = ElementBinding.STATIC.name
    private var selectedDataField: String? = null
    private var selectedHorizontalAlign = TextAlign.LEFT.name
    private var selectedVerticalAlign = VerticalAlign.TOP.name
    private var selectedFontFamily = FontFamily.DEFAULT.name

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@registerForActivityResult
        pendingStaticBitmap = bitmap
        binding.imageStaticPreview.setImageBitmap(bitmap)
        binding.textStaticImageHint.isVisible = false
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
        val current = element ?: return

        binding.textElementKind.text = current.kind.name

        setupBindingTypeDropdown(current)
        setupDataFieldDropdown(current)
        setupAlignmentDropdowns(current)
        setupFontFamilyDropdown(current)
        loadCurrentValues(current)

        binding.buttonChooseStaticImage.setOnClickListener {
            imagePicker.launch("image/*")
        }

        binding.buttonApplyInspector.setOnClickListener {
            applyChanges(current)
        }
        binding.buttonDeleteElement.setOnClickListener {
            callback?.onDelete(current.id)
            dismiss()
        }
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
        val isDynamic = selectedBindingType == ElementBinding.DYNAMIC.name

        binding.layoutStaticText.isVisible = isText && !isDynamic
        binding.layoutBindingKey.isVisible = isDynamic && (isText || isImage)
        binding.layoutStaticImage.isVisible = isImage && !isDynamic
        binding.layoutPrefix.isVisible = isText && isDynamic
        binding.layoutTextStyle.isVisible = isText
        binding.layoutAlignment.isVisible = isText || isImage
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
            bounds = ElementBounds(
                x = binding.editPosX.text.toString().toFloatOrNull() ?: original.bounds.x,
                y = binding.editPosY.text.toString().toFloatOrNull() ?: original.bounds.y,
                width = binding.editWidth.text.toString().toFloatOrNull() ?: original.bounds.width,
                height = binding.editHeight.text.toString().toFloatOrNull() ?: original.bounds.height
            ),
            style = ElementStyle(
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
                    .getOrDefault(original.style.fontFamily)
            ),
            content = content
        )
        callback?.onApply(updated)
        dismiss()
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
    }
}
