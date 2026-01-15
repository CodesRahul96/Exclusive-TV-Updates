package com.codesrahul.exclusivetv

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment

class ChannelOptionsDialogFragment : DialogFragment() {
    private var listener: ChannelOptionsListener? = null
    private var channelName: String = ""

    interface ChannelOptionsListener {
        fun onMoveSelected()
        fun onRenameSelected()
        fun onCancelSelected()
    }

    companion object {
        private const val ARG_CHANNEL_NAME = "channel_name"

        fun newInstance(channelName: String): ChannelOptionsDialogFragment {
            val fragment = ChannelOptionsDialogFragment()
            val args = Bundle()
            args.putString(ARG_CHANNEL_NAME, channelName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelName = arguments?.getString(ARG_CHANNEL_NAME) ?: ""
    }

    fun setChannelOptionsListener(listener: ChannelOptionsListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_channel_options, null)

        val titleText = view.findViewById<android.widget.TextView>(R.id.dialog_title)
        val btnMove = view.findViewById<Button>(R.id.btn_move)
        val btnRename = view.findViewById<Button>(R.id.btn_rename)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        titleText.text = "Channel Options: $channelName"

        btnMove.setOnClickListener {
            listener?.onMoveSelected()
            dismiss()
        }

        btnRename.setOnClickListener {
            listener?.onRenameSelected()
            dismiss()
        }

        btnCancel.setOnClickListener {
            listener?.onCancelSelected()
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }
}
