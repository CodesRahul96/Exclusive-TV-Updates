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
    private val update: Boolean,
    private val force: Boolean = false
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val dialog = Dialog(it, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_update, null)
            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            val btnUpdate = view.findViewById<Button>(R.id.btnUpdate)
            val btnCancel = view.findViewById<Button>(R.id.btnCancel)
            val tvTitle = view.findViewById<TextView>(R.id.tvTitle)

            tvMessage.text = message

            // Prevent cancellation if forced
            isCancelable = !force
            dialog.setCanceledOnTouchOutside(!force)

            if (update) {
                tvTitle.text = "Update Available"
                btnUpdate.text = "Update Now"
                btnUpdate.setOnClickListener {
                    listener.onConfirm()
                    if (!force) dismiss() // Only dismiss if not forced (wait for download)
                }
                
                if (force) {
                    btnCancel.visibility = android.view.View.GONE
                } else {
                    btnCancel.setOnClickListener {
                        listener.onCancel()
                        dismiss()
                    }
                }
            } else {
                tvTitle.text = "Up to Date"
                btnUpdate.text = "OK"
                btnCancel.visibility = android.view.View.GONE
                btnUpdate.setOnClickListener {
                    dismiss()
                }
            }
            // Focus handling (Optional logic if needed, previously commented out or simple animation)
             val focusListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            btnUpdate.onFocusChangeListener = focusListener
            btnCancel.onFocusChangeListener = focusListener

            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    interface ConfirmationListener {
        fun onConfirm()
        fun onCancel()
    }
}

