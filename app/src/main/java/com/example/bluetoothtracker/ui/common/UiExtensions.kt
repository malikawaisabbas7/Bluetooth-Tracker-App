package com.example.bluetoothtracker.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.bluetoothtracker.R

/** Opens a web link in the browser. */
fun Context.openLink(url: String) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) {
        Toast.makeText(this, R.string.link_failed, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)))
    } catch (_: Exception) {
        Toast.makeText(this, R.string.link_failed, Toast.LENGTH_SHORT).show()
    }
}

/** Shows [content] in a dialog that uses its own rounded background (see bg_dialog). */
fun Context.showCustomDialog(content: View): AlertDialog {
    val dialog = AlertDialog.Builder(this).setView(content).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.show()
    dialog.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    return dialog
}
