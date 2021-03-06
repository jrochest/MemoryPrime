package com.md

import android.content.Context

interface ToneManager {
    fun backupTone()
    fun errorTone()
    fun keepHeadphoneAlive()

    fun maybeStartTone(context: Context)
    fun maybeStopTone()
}