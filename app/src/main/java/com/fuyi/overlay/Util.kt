package com.fuyi.overlay

import android.content.Context
import android.widget.Toast

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.sp(value: Int): Float = value * resources.displayMetrics.scaledDensity

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
