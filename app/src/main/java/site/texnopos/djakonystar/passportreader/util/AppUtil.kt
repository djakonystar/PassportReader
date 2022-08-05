package site.texnopos.djakonystar.passportreader.util

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface

object AppUtil {
    fun showAlertDialog(
        activity: Activity?,
        title: String?,
        message: String?,
        buttonText: String?,
        isCancelable: Boolean,
        listener: DialogInterface.OnClickListener?
    ) {
        showAlertDialog(activity, title, message, buttonText, null, null, isCancelable, listener)
    }

    private fun showAlertDialog(
        activity: Activity?,
        title: String?,
        message: String?,
        positiveButtonText: String?,
        negativeButtonText: String?,
        neutralButtonText: String?,
        isCancelable: Boolean,
        listener: DialogInterface.OnClickListener?
    ) {
        val dialogBuilder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(isCancelable)
        if (positiveButtonText != null && !positiveButtonText.isEmpty()) dialogBuilder.setPositiveButton(
            positiveButtonText,
            listener
        )
        if (negativeButtonText != null && !negativeButtonText.isEmpty()) dialogBuilder.setNegativeButton(
            negativeButtonText,
            listener
        )
        if (neutralButtonText != null && !neutralButtonText.isEmpty()) dialogBuilder.setNeutralButton(
            neutralButtonText,
            listener
        )
        dialogBuilder.show()
    }
}