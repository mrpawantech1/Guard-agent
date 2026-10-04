package com.guard.agent.utils

import android.util.Log

object Logger {

    private const val TAG = "GuardAgent"
    private const val DEBUG = true

    // ---------- DEBUG ----------
    fun d(msg: String) {
        if (DEBUG) Log.d(TAG, msg)
    }

    fun d(tag: String, msg: String) {
        if (DEBUG) Log.d("$TAG/$tag", msg)
    }

    // ---------- ERROR ----------
    fun e(msg: String) {
        Log.e(TAG, msg)
    }

    fun e(tag: String, msg: String) {
        Log.e("$TAG/$tag", msg)
    }

    fun e(tag: String, msg: String, t: Throwable?) {
        if (t != null) Log.e("$TAG/$tag", msg, t)
        else Log.e("$TAG/$tag", msg)
    }

    // ---------- WARN ----------
    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w("$TAG/$tag", msg)
    }

    // ---------- INFO ----------
    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i("$TAG/$tag", msg)
    }
}
