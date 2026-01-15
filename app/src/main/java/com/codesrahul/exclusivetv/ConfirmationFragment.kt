package com.codesrahul.exclusivetv

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView

class ConfirmationFragment(
    private val listener: ConfirmationListener,
    private val message: String,
    private val update: Boolean
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_update, null)

            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            val btnUpdate = view.findViewById<Button>(R.id.btnUpdate)
            val btnCancel = view.findViewById<Button>(R.id.btnCancel)
            val tvTitle = view.findViewById<TextView>(R.id.tvTitle)

            tvMessage.text = message

            if (update) {
                tvTitle.text = "Update Available"
                btnUpdate.text = "Update Now"
                btnUpdate.setOnClickListener {
                    listener.onConfirm()
                    dismiss()
                }
                btnCancel.setOnClickListener {
                    listener.onCancel()
                    dismiss()
                }
            } else {
                tvTitle.text = "Up to Date"
                btnUpdate.text = "OK"
                btnCancel.visibility = android.view.View.GONE
                btnUpdate.setOnClickListener {
                    dismiss()
                }
            }

            val focusListener = android.view.View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.10f).scaleY(1.10f).setDuration(120).start()
                    view.elevation = 24f
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    view.elevation = 0f
                }
            }

            btnUpdate.onFocusChangeListener = focusListener
            btnCancel.onFocusChangeListener = focusListener

            builder.setView(view)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    interface ConfirmationListener {
        fun onConfirm()
        fun onCancel()
    }
}

