package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.databinding.BottomSheetAddElementBinding

class AddElementBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onAddElement(kind: ElementKind)
    }

    var callback: Callback? = null

    private var _binding: BottomSheetAddElementBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddElementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonAddText.setOnClickListener { addAndDismiss(ElementKind.TEXT) }
        binding.buttonAddImage.setOnClickListener { addAndDismiss(ElementKind.IMAGE) }
        binding.buttonAddTable.setOnClickListener { addAndDismiss(ElementKind.TABLE) }
        binding.buttonAddLine.setOnClickListener { addAndDismiss(ElementKind.LINE) }
        binding.buttonAddRect.setOnClickListener { addAndDismiss(ElementKind.RECT) }
    }

    private fun addAndDismiss(kind: ElementKind) {
        callback?.onAddElement(kind)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AddElementBottomSheet = AddElementBottomSheet()
    }
}
