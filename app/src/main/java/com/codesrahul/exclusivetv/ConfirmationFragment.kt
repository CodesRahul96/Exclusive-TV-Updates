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
    private val changelog: String = "",
    private val update: Boolean,
    private val force: Boolean = false
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

            val tvChangelog = view.findViewById<TextView>(R.id.tvChangelog)

            tvMessage.text = message
            
            if (changelog.isNotEmpty()) {
                tvChangelog.text = changelog
            } else {
                tvChangelog.text = "Bug fixes and improvements."
            }

            // Prevent cancellation if forced
            isCancelable = !force

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

            val focusListener = android.view.View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                    view.elevation = 10f
                    view.setBackgroundResource(if(view.id == R.id.btnUpdate) R.drawable.selector_item_focus else R.drawable.tv_button_bg)
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    view.elevation = 0f
                     view.setBackgroundResource(if(view.id == R.id.btnUpdate) R.drawable.tv_button_bg else R.drawable.tv_button_bg) // Reset to default if needed, or just let selector handle it
                }
            }

           // btnUpdate.onFocusChangeListener = focusListener
           // btnCancel.onFocusChangeListener = focusListener

            builder.setView(view)
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setCanceledOnTouchOutside(!force)
            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    interface ConfirmationListener {
        fun onConfirm()
        fun onCancel()
    }
}

