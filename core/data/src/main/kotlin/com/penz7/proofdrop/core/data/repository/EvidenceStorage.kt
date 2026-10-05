package com.penz7.proofdrop.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Evidence media lives in app-private storage: not visible to the gallery or other apps. */
@Singleton
class EvidenceStorage @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "evidence").apply { mkdirs() }

    fun newFile(name: String): File = File(dir, name)

    fun file(name: String): File = File(dir, name)
}
