package com.oldpad.app.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import kotlin.math.min
import com.oldpad.app.databinding.DialogConfirmBinding

/** 全应用统一的大字号确认框，防止老人误触退出。 */
object Dialogs {

    fun confirm(
        activity: Activity,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(activity)
        val binding = DialogConfirmBinding.inflate(activity.layoutInflater)
        dialog.setContentView(binding.root)

        binding.dialogTitle.text = title
        binding.dialogMessage.text = message
        binding.btnConfirm.text = confirmText
        binding.btnCancel.text = cancelText

        binding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            val dm = activity.resources.displayMetrics
            val width = min((dm.widthPixels * 0.9f).toInt(), (720 * dm.density).toInt())
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}
