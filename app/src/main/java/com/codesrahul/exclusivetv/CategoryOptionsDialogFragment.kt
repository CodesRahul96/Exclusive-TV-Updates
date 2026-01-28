package com.codesrahul.exclusivetv

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.codesrahul.exclusivetv.R
import com.codesrahul.exclusivetv.ui.TvUiUtils

class CategoryOptionsDialogFragment : DialogFragment() {
    private var listener: CategoryOptionsListener? = null
    private var categoryName: String = ""

    interface CategoryOptionsListener {
        fun onMoveSelected()
        fun onRenameSelected()
        fun onHideSelected()
        fun onCancelSelected()
    }

    companion object {
        private const val ARG_CATEGORY_NAME = "category_name"

        fun newInstance(categoryName: String): CategoryOptionsDialogFragment {
            val fragment = CategoryOptionsDialogFragment()
            val args = Bundle()
            args.putString(ARG_CATEGORY_NAME, categoryName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryName = arguments?.getString(ARG_CATEGORY_NAME) ?: ""
        setStyle(STYLE_NO_TITLE, 0)
    }

    fun setCategoryOptionsListener(listener: CategoryOptionsListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category_options, null)

        val titleText = view.findViewById<android.widget.TextView>(R.id.dialog_title)
        val btnMove = view.findViewById<Button>(R.id.btn_move)
        val btnRename = view.findViewById<Button>(R.id.btn_rename)
        val btnHide = view.findViewById<Button>(R.id.btn_hide)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        titleText.text = "Category Options: $categoryName"

        btnMove.setOnClickListener {
            listener?.onMoveSelected()
            dismiss()
        }

        btnRename.setOnClickListener {
            listener?.onRenameSelected()
            dismiss()
        }

        btnHide.setOnClickListener {
            listener?.onHideSelected()
            dismiss()
        }

        btnCancel.setOnClickListener {
            listener?.onCancelSelected()
            dismiss()
        }

        val tvUiUtils = TvUiUtils(requireContext())
        tvUiUtils.initSounds(R.raw.focus, R.raw.click)

        // Symbols Fix: Explicitly tint icons for compatibility with Android API < 23
        val iconColor = android.graphics.Color.parseColor("#80FFFFFF")
        tvUiUtils.tintTextViewDrawable(btnMove, iconColor)
        tvUiUtils.tintTextViewDrawable(btnRename, iconColor)
        tvUiUtils.tintTextViewDrawable(btnHide, iconColor)

        val buttons = listOf(btnMove, btnRename, btnHide, btnCancel)
        buttons.forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    tvUiUtils.playFocusSound()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
        }

        builder.setView(view)
        val dialog = builder.create()
        dialog.setOnShowListener {
            btnMove.requestFocus()
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
}
