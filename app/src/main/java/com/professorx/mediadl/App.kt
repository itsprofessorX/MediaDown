package com.professorx.mediadl

import android.app.Application

/** Holds the downloader for the whole process so downloads survive closing the screen. */
class App : Application() {
    val dl: DlViewModel by lazy { DlViewModel(this) }
}
