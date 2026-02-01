package com.codesrahul.exclusivetv

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView

class ConfirmationFragment : DialogFragment() {

    private var listener: ConfirmationListener? = null

    companion object {
        private const val ARG_MESSAGE = "message"
        private const val ARG_CHANGELOG = "changelog"
        private const val ARG_UPDATE = "update"
        private const val ARG_FORCE = "force"

        fun newInstance(
            listener: ConfirmationListener,
            message: String,
            changelog: String = "",
            update: Boolean,
            force: Boolean = false
        ): ConfirmationFragment {
            val fragment = ConfirmationFragment()
            fragment.listener = listener
            val args = Bundle().apply {
                putString(ARG_MESSAGE, message)
                putString(ARG_CHANGELOG, changelog)
                putBoolean(ARG_UPDATE, update)
                putBoolean(ARG_FORCE, force)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = arguments?.getString(ARG_MESSAGE) ?: ""
        val changelog = arguments?.getString(ARG_CHANGELOG) ?: ""
        val update = arguments?.getBoolean(ARG_UPDATE) ?: false
        val force = arguments?.getBoolean(ARG_FORCE) ?: false

        return activity?.let {
            val dialog = Dialog(it, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_update, null)
            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)

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
            dialog.setCanceledOnTouchOutside(!force)

            if (update) {
                tvTitle.text = "Update Available"
                btnUpdate.text = "Update Now"
                btnUpdate.setOnClickListener {
                    listener?.onConfirm()
                    if (!force) dismiss() // Only dismiss if not forced (wait for download)
                }
                
                if (force) {
                    btnCancel.visibility = android.view.View.GONE
                } else {
                    btnCancel.setOnClickListener {
                        listener?.onCancel()
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
            // Focus handling
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

